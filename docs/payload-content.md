# Payload içeriği, dosya örnekleri ve alan eşleme

Bu API katalog içeriğini ve eşleme tanımlarını saklar; dış sisteme istek göndermez,
dosya dönüştürmez veya kaydedilen eşlemeleri çalıştırmaz.

## Format ve akış modeli

| `format` | İçerik | Alan eşleme |
|---|---|---|
| `JSON` | `schemaDefinition` veya `samplePayload` JSON nesnesi; ikisi birlikte de verilebilir | Var; AI/append yolları doğrulanır, örneksiz kaynak JSON'da eski manuel sözleşme daha sınırlıdır |
| `XML` | UTF-8 XML metni + backend'in ürettiği alan ağacı | Var; yolların ağaçta bulunması doğrulanır |
| `IDOC` | XML gösterimi, `IDOC` elemanı içermeli + üretilen alan ağacı | Var; yolların ağaçta bulunması doğrulanır |
| `CSV` | UTF-8 metin veya dosya örneği; ayırıcı/başlık seçenekleri | Yok |
| `EXCEL` | `.xls` veya `.xlsx` dosya örneği | Yok |

Her entegrasyon/`schemaType` çifti için tek kayıt vardır. Tekrar kayıt ID'yi koruyarak
kaynak içeriğini ve dosya metaverisini değiştirir. Gönderilmeyen bağımsız hedef,
formatı güncel akışla uyumluysa korunur. Ara katman yoksa request örneği
`SOURCE_TO_DEST_REQUEST`; varsa iki örnek `SOURCE_TO_MW_REQUEST` ve
`MW_TO_DEST_REQUEST` olarak tutulur. Senkron akışta karşılık gelen response
kayıtları da zorunludur; [tam matris](integration-workflow.md#ortam-ve-şema-kuralları).

Ara katman ekleme/kaldırma güncellemesi mevcut örnekleri hemen silmez. Güncel
akışa uygun ilk örnek başarıyla kaydedildiğinde, önceki akışın uyumsuz şemaları
ve kaynak veya hedef olarak bu şemalara bağlı alan eşlemeleri temizlenir.
Ara katman eklenirse `SOURCE_TO_DEST_REQUEST` ve `DEST_TO_SOURCE_RESPONSE`;
kaldırılırsa dört ara katman şema türü silinir. Güncel akışa uygun mevcut örnekler
ve kendi aralarındaki eşlemeler korunur. Metin/JSON kaydı ve dosya yüklemede yeni
örneğin kaydıyla temizlik tek transaction'dadır; hata durumunda eski örnekler ve
eşlemeler korunur. Başarılı kayıt sonrasındaki şema/eşleme sorguları temizlenmiş
listeyi döndürür. İnceleme bayrağı ayrıca onaylanmalıdır.

Temizlik örnek kaydında çalışır; uygulamanın yeniden başlatılması veya GET sorgusu
önceki sürümden kalmış verileri kendiliğinden temizlemez. Bu özellik devreye
alınmadan önce yeni akışın örnekleri kaydedilmişse, güncel akışa uygun mevcut bir
örneği aynı içerikle yeniden kaydedin. Bu upsert mevcut ID'yi korur ve eski akışın
uyumsuz şemalarını/bağlı eşlemelerini aynı transaction'da temizler.

İçerik kaydının `format` değeri aşağıdaki entegrasyon seçimiyle eşleşmelidir:

| `schemaType` | Beklenen format |
|---|---|
| `SOURCE_TO_MW_REQUEST`, `MW_TO_SOURCE_RESPONSE` | `sourcePayloadFormat` |
| `MW_TO_DEST_REQUEST`, `DEST_TO_MW_RESPONSE` | `destinationPayloadFormat` |
| `SOURCE_TO_DEST_REQUEST` | `sourcePayloadFormat` (gönderen kaynak) |
| `DEST_TO_SOURCE_RESPONSE` | `destinationPayloadFormat` (yanıtı gönderen hedef) |

Ara katmanlı akışta örneğin kaynak CSV, hedef XML olabilir. Bir eşleme çiftinin
**iki ucu da** eşlemeyi desteklemelidir; CSV → XML çiftinde iki örnek saklanır,
alan eşlemesi kaydedilmez. Doğrudan akışın her yönü tek bir kayıt olarak kalır;
bu kayıtta bağımsız hedef `targetFormat`, `targetSchemaDefinition`,
`targetSamplePayload` ve `targetRawContent` alanlarıyla temsil edilir.
JSON hedef için tanım/örnek nesnesi, XML/IDOC hedef için ham metin gönderilir;
XML alan ağacını backend üretir. Request'te hedef formatı `destinationPayloadFormat`,
response'ta ise `sourcePayloadFormat` ile eşleşir. Karma formatlı doğrudan akışı
tamamlamak ve AI önerisi almak için bağımsız hedef gerekir.
[Dokuz format çifti ve örnek gövdeler](ai-mapping.md#desteklenen-format-çiftleri-ve-kaynakhedef-örnekleri).

## Metin veya JSON nesnesi kaydetme

`POST /api/v1/integrations/10/mappings/schemas`, `Content-Type: application/json`.
Yanıt 200 ve kaydedilen kayıttır. EDITOR/ADMIN ve CSRF gerekir.

Aşağıdaki örnekler doğrudan akış içindir. Entegrasyonun `sourcePayloadFormat`
değeri gönderilen formatla eşleşmelidir. Ara katmanlı akışta uygun `schemaType`
ve yukarıdaki tablonun gösterdiği kaynak/hedef formatı kullanılmalıdır.

JSON; `format` verilmezse geriye uyumluluk için JSON kabul edilir:

`schemaDefinition` yoksa `samplePayload` tanım olarak da kullanılır. İkisi de
yoksa istek reddedilir. AI'ın örnek değerleri kullanması için `samplePayload`
ayrıca verilebilir; JSON Schema standardının tamamı doğrulanmaz.

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "schemaDefinition": {"type": "object", "properties": {"id": {"type": "string"}}}
}
```

XML:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "XML",
  "rawContent": "<order>\r\n  <id>42</id>\r\n</order>"
}
```

IDOC; düz SAP kayıt/metin veya ikili gösterimler kabul edilmez:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "IDOC",
  "rawContent": "<ORDERS05><IDOC BEGIN=\"1\"><E1EDK01><BELNR>42</BELNR></E1EDK01></IDOC></ORDERS05>"
}
```

CSV:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "CSV",
  "rawContent": "id;name\r\n42;Örnek\r\n",
  "csvOptions": {"delimiter": ";", "hasHeader": true}
}
```

CSV seçenekleri verilmezse `delimiter=","`, `hasHeader=true`. Başlıksız dosya için
`hasHeader=false`; tab için `delimiter="\t"` gönderin. Yapıştırılan CSV için
indirilebilir `sample.csv` dosyası da oluşturulur. JSON dışında `schemaDefinition`
gönderilmez; CSV dışında `csvOptions` gönderilmez. JSON metni yapıştırma durumunda
frontend metni JSON nesnesine çevirip `schemaDefinition` gönderir.

## Dosya yükleme ve indirme

`POST /api/v1/integrations/10/mappings/schemas/upload`, `multipart/form-data`:

- `metadata`: `application/json` parçası. `schemaType`, **zorunlu** `format`,
  CSV için isteğe bağlı `csvOptions`. Kaynak dosyanın içeriği `rawContent` veya
  `schemaDefinition` olarak tekrar gönderilmez. Doğrudan akışta bağımsız hedef
  formatı ve hedef içeriği ayrıca bu metadata içinde gönderilebilir.
- `file`: Dosya parçası.

Örnek metadata (Excel):

```json
{"schemaType":"SOURCE_TO_DEST_REQUEST","format":"EXCEL"}
```

Frontend örneği; mevcut oturum/CSRF akışını kullanın:

```javascript
const form = new FormData();
form.append("metadata", new Blob([JSON.stringify({
  schemaType: "SOURCE_TO_DEST_REQUEST",
  format: "EXCEL"
})], {type: "application/json"}));
form.append("file", file);
const response = await fetch(`/api/v1/integrations/${id}/mappings/schemas/upload`, {
  method: "POST",
  credentials: "include",
  headers: {[csrfHeaderName]: csrfToken},
  body: form
});
```

`Content-Type` üstbilgisini elle ayarlamayın; multipart boundary'yi tarayıcı ekler.
Bu endpoint JSON/XML/IDOC/CSV dosyalarını da kabul eder. JSON dosyası bir nesne
olmalıdır; içeriği `schemaDefinition` olarak saklanır. Dosya MIME beyanına güvenilmez;
uzantı ve gerçek içerik birlikte kontrol edilir.

| Format | Uzantı | Maksimum içerik boyutu |
|---|---|---|
| JSON | `.json` | 1 MiB |
| XML | `.xml` | 1 MiB |
| IDOC | `.xml`, `.idoc` (XML içerik) | 1 MiB |
| CSV | `.csv` | 1 MiB |
| EXCEL | `.xls`, `.xlsx` | 5 MiB |

Metin dosyaları UTF-8 olmalıdır; UTF-8 BOM kabul edilir. XML encoding bildirimi
varsa UTF-8 olmalıdır. XML DTD/harici kaynak/entity tanımları kapalıdır; en fazla
64 eleman derinliği, 10000 alan ve alan yolu başına 500 karakter kabul edilir.
XML yapısal olarak doğrulanır; XSD, IDOC iş kuralları ve SAP segment standardı
doğrulaması yapılmaz. Alan tipleri örnekten kesin çıkarsanmaz: yapraklar string,
alt elemanı olan düğümler object olarak gösterilir.

CSV'de tırnaklı alanlar/satır sonları desteklenir; sütun sayısı tutarlı olmalı,
başlık kullanılıyorsa başlıklar boş veya aynı olmamalıdır. Yalnızca başlık satırı
olan dosya kabul edilir. Excel okunabilir, şifresiz bir workbook olmalı ve en az
bir worksheet içermelidir. Excel alan ağacı/önizleme endpoint'i yoktur.

Yüklenen dosyanın özgün baytları PostgreSQL `bytea` alanında saklanır. XML/IDOC
özgün metni ayrıca `TEXT`, üretilen alan ağacı `jsonb` alanında saklanır. Metin
biçimlendirilmez; satır sonları korunur. Dosya yolu alınmaz, yalnızca temizlenmiş
dosya adı saklanır. Örnek içerikler credential şifreleme mekanizmasına dahil
değildir; örneklerde gerçek erişim bilgisi kullanılmamalıdır.

`GET /api/v1/integrations/10/mappings/schemas/30/sample` özgün dosyayı attachment
olarak döndürür. VIEWER/EDITOR/ADMIN erişebilir. Kayıt ilgili entegrasyona ait
değilse veya dosyası yoksa 404 döner. Yapıştırılmış XML/IDOC için `rawContent`
kullanılır; yüklenmiş dosya olmadığı için indirme URL'si verilmez.

## Okuma yanıtları ve alan yolları

`GET /api/v1/integrations/10/mappings/schemas` kayıt dizisi döndürür.
POST yanıtı aynı yapının tek kaydıdır. Aşağıdaki örneklerde ilgisiz null alanlar
kısalık için gösterilmemiştir.

JSON:

```json
{"id":30,"schemaType":"SOURCE_TO_DEST_REQUEST","format":"JSON","mappingSupported":true,"schemaDefinition":{"type":"object","properties":{"id":{"type":"string"}}}}
```

XML:

```json
{
  "id": 30,
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "XML",
  "mappingSupported": true,
  "rawContent": "<order>\r\n  <id>42</id>\r\n</order>",
  "schemaDefinition": {
    "kind": "xml",
    "fields": [{
      "name": "order", "path": "/order", "kind": "element", "type": "object",
      "namespaceUri": "", "repeated": false,
      "children": [{
        "name": "id", "path": "/order/id", "kind": "element", "type": "string",
        "namespaceUri": "", "repeated": false, "children": []
      }]
    }]
  }
}
```

IDOC aynı alan ağacı yapısını `format="IDOC"`, `kind="idoc-xml"` ile döndürür;
örnekte eşlenebilir alan yolu `/ORDERS05/IDOC/E1EDK01/BELNR` olur.
Attribute yolları `/order/@id`; namespace yolları `/{urn:orders}order/{urn:orders}id`
biçimindedir. Bunlar API alan yolu kimlikleridir; XPath ifadesi değildir.
Namespace prefix değişikliği alan kimliğini değiştirmez. Tekrarlanan elemanlarda
`repeated=true`; örnekte görülen alt alanlar birleştirilir, dizi indeksi eklenmez.

CSV:

```json
{
  "id": 30, "schemaType": "SOURCE_TO_DEST_REQUEST", "format": "CSV",
  "mappingSupported": false, "schemaDefinition": {},
  "rawContent": "id;name\r\n42;Örnek\r\n",
  "csvOptions": {"delimiter":";","hasHeader":true},
  "fileName":"sample.csv", "contentType":"text/csv", "fileSize":20,
  "sampleDownloadUrl":"/api/v1/integrations/10/mappings/schemas/30/sample"
}
```

Excel (boyut dosyaya göre değişir):

```json
{
  "id": 30, "schemaType": "SOURCE_TO_DEST_REQUEST", "format": "EXCEL",
  "mappingSupported": false, "schemaDefinition": {}, "rawContent": null,
  "fileName": "orders.xlsx",
  "contentType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "fileSize": 4096,
  "sampleDownloadUrl": "/api/v1/integrations/10/mappings/schemas/30/sample"
}
```

Dosyanın ikili içeriği GET JSON yanıtına/base64'e eklenmez. UI eşleme düğmelerini
`mappingSupported` ile yönetmeli; backend de CSV/Excel uçlarını reddeder.

## Eşleme ve tamamlama

`POST .../mappings/fields` eşleme listesinin tamamını değiştirir. XML/IDOC yolları,
hem kayıt sırasında hem tamamlamada üretilen ağaçta aranır. JSON kaynakta
`samplePayload`, bağımsız hedefte tanım/örnek varsa yol varlığı da kontrol edilir;
örneksiz eski kaynak JSON sözleşmesinin kontrolü sınırlıdır. AI job/append akışı
normalize JSON/XML/IDoc yollarını doğrular; AI önerileri tip/dizi uyumundan da
geçer. Manuel kayıtta anlamsal doğruluk veya çalışma zamanı tip dönüşümü doğrulanmaz.
CSV/Excel uçlu eşleme kaydı 400 `MAPPING_NOT_SUPPORTED` döndürür.

İçerik değişikliği güncel akışta korunacak eşlemenin yolunu kaldırıyorsa veya kayıt
CSV/Excel'e çevrilirken böyle bir eşleme varsa 409 `SCHEMA_MAPPING_CONFLICT` döner. Önce ilgili
eşlemeleri listeden çıkarıp listeyi yeniden kaydedin; ardından içerik değişikliğini
yapın. Geçerli yollar korunurken alan ağacı/format değişirse `reviewRequired=true`
olur; standart inceleme onay akışı kullanılır. Başarısız değişiklik eski içeriği
ve eşlemeleri değiştirmez. Başarılı içerik/dosya kaydı entegrasyonu DRAFT yapar.
Akış temizliğinde silinecek eski şemalara bağlı eşlemeler bu kaydı engellemez.

Tamamlama mevcut ortam/topoloji/inceleme kontrollerine ek olarak format uyumunu,
JSON nesnesinin boş olmamasını, XML/IDOC ayrıştırılabilirliğini ve alan ağacını,
CSV/Excel dosya örneğinin bulunmasını ve okunabilirliğini kontrol eder.
Hiçbir formatta alan eşlemesi zorunlu değildir. Asenkron akışta response
kayıtları isteğe bağlıdır; bulunuyorlarsa yine doğrulanır.

## Hatalar

Mevcut hata zarfına içerik hataları için `code` eklenir:

```json
{
  "timestamp":"2026-10-25T20:00:00",
  "status":400,
  "error":"Bad Request",
  "code":"INVALID_XML",
  "message":"Malformed or unsupported XML at line 1, column 8",
  "path":"/api/v1/integrations/10/mappings/schemas"
}
```

Her format için bu zarfın `code`/`message` değerleri:

| Örnek hata | HTTP | `code` | Örnek `message` |
|---|---|---|---|
| JSON nesnesi verilmemiş | 400 | `INVALID_JSON` | `Schema definition must be a JSON object` |
| Bozuk XML | 400 | `INVALID_XML` | `Malformed or unsupported XML at line 1, column 8` |
| IDOC elemanı olmayan XML | 400 | `INVALID_IDOC` | `IDOC XML must contain an IDOC element` |
| Eksik CSV sütunu | 400 | `INVALID_CSV` | `Inconsistent column count at record 2` |
| Sahte/şifreli Excel dosyası | 400 | `INVALID_EXCEL` | `A readable, unencrypted .xls or .xlsx workbook is required` |
| UTF-8 olmayan dosya | 400 | `INVALID_ENCODING` | `Only UTF-8 text files are supported` |
| Boyut aşımı | 413 | `PAYLOAD_TOO_LARGE` | `Content exceeds 1048576 bytes` |
| Yanlış akış formatı | 400 | `FORMAT_MISMATCH` | `Expected CSV for SOURCE_TO_DEST_REQUEST` |

DTO/JSON gövde okuma ve auth hatalarının hepsinde `code` bulunması garanti değildir.
Tamamlama başarısızlığı mevcut 409 zarfında `details` listesiyle döner.

## Veritabanı geçişi ve test

Yeni/boş DB'de payload kolonları güncel entity'lerden Hibernate `ddl-auto=update`
ile oluşturulur. Mevcut DB yükseltilirken format CHECK kısıtları ayrıca kontrol
edilmelidir; Hibernate eski kısıtlara EXCEL gibi yeni değerleri otomatik
eklemeyebilir. `schema_definition NOT NULL` olmaya devam eder; CSV/Excel için
boş nesne saklanır.

Eski kayıtlarda format entegrasyonda XML/IDOC/CSV seçili olsa bile içerik gerçekte
JSON nesnesi olarak saklanıyordu. Bu kayıtlar otomatik XML/CSV'ye dönüştürülmez;
uygun formatta içerik yeniden yüklenene kadar tamamlama format uyumsuzluğu döner.

Normal test komutu README'dedir. Gerçek JSONB/TEXT/BYTEA kayıt/okuma, akış temizliği, AI işleri ve sistem kataloğu
testleri ayrıca opt-in çalışır; varsayılan testlerde atlanır. **Ayrı, boş bir PostgreSQL test DB'si**
ve yalnızca o DB'ye yetkili `catalog_payload_test` kullanıcısı oluşturun
(test parolası `payload-test-only-password`; üretimde kullanılmaz). Ardından:

```sh
./mvnw -B '-Dtest=*PostgresTests' -Dcatalog.payload.postgres-test=true \
  -Dcatalog.payload.test-db-url=jdbc:postgresql://localhost:5432/integration_payload_test_20261025 test
```

Testler tabloları mevcut Hibernate geliştirme ayarıyla oluşturur. Payload roundtrip
testlerinde kayıt transaction'ları rollback olur. Akış temizliği testleri commit
ve rollback'i doğrulamak için bağımsız transaction'lar kullanır ve kendi örnek
kayıtlarını test sonunda siler. AI DB testleri kalıcı iş, sonuç, onay/idempotency,
süre dolumu ve cascade temizliğini doğrular. Bootstrap test kullanıcısı DB'de kalabilir.
