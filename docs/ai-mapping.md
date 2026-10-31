# AI alan eşleme asistanı

AI isteğe bağlıdır. JSON, XML ve IDOC arasındaki tüm çiftlerde alan bağlantıları önerir; entegrasyon
çalıştırmaz, dönüşüm kodu üretmez ve mevcut eşlemeleri kendiliğinden değiştirmez.
Frontend bu repoda bulunmaz; aşağıdaki servisler ayrı frontend projesi içindir.
Sağlayıcı backend ayarıyla seçilir: varsayılan Docker içindeki yerel Ollama,
isteğe bağlı olarak Google Gemini API. Frontend'de sağlayıcı/model/API anahtarı
seçimi yoktur; aynı endpoint'ler kullanılır.

## Docker kurulumu

Bu bölüm Ollama içindir. Gemini için model/container indirmeniz gerekmez;
[Gemini kurulumu](#gemini-kurulumu) bölümünü uygulayın.

Normal `docker compose up` Ollama başlatmaz. `.env.example` içinde `AI_ENABLED=false`
varsayılandır. AI kullanmayan geliştiricinin model indirmesi gerekmez.

Önce normal kurulumdaki DB, yönetici ve şifreleme ayarlarını hazırlayın.
Model indirmeden önce `.env` içinde Ollama sağlayıcısını ve yerel modeli seçin;
kurulum servisi de `AI_MODEL` değerini kullanır. Gemini model adıyla Ollama pull
çalıştırmayın:

```dotenv
AI_ENABLED=true
AI_PROVIDER=ollama
AI_BASE_URL=http://ollama:11434
AI_MODEL=qwen3.5:4b
OLLAMA_IMAGE=ollama/ollama:0.40.13
```

Modeli indirmek için ayrı, internet erişimli kurulum container'ını çalıştırın:

```sh
docker compose --profile ai-setup run --rm ollama-model-pull
```

`qwen3.5:4b` yaklaşık 3,3 GB'tır; Ollama imajı ayrıca yer kaplar. Model
`ollama_data` volume'ünde tutulur, Git'e/backend imajına girmez. Mevcut model
kurulumunu güncellerken önce `docker compose --profile ai stop ollama` çalıştırın;
kurulum ve çalışma servislerini aynı volume üzerinde eşzamanlı çalıştırmayın.
Model hazır olduğunda çalışma profilini başlatın:

```sh
docker compose --profile ai up --build -d --wait --wait-timeout 180
docker compose --profile ai ps
python3 scripts/check_ai_network.py
```

Çalışma container'ı yalnızca `internal: true` olan `ai_internal` ağına bağlıdır;
host portu yayımlamaz, bind mount veya Docker soketi almaz. Backend bu iç ağa
bağlanır. Ollama'da `OLLAMA_NO_CLOUD=1` ayarlıdır. Kurulum servisi çalışma
servisinin dependency'si değildir; backend model indirme endpoint'i çağırmaz.

`check_ai_network.py`, yalnızca iç ağlara bağlantıyı, portların yayımlanmadığını,
bulutun kapalı olduğunu, dış IP/domain TCP çıkışının başarısız olduğunu ve
backend → Ollama erişiminin başarılı olduğunu kontrol eder. Bu çalışma container'ının
ağ kontrolüdür; Docker daemon'ının veya bütün bilgisayarın internet erişimini kapatmaz.
Backend'in diğer ağı vardır; yerel sağlayıcı yalnızca ayarlardaki Ollama adresine gider.
AI isteği environment config veya credential bilgilerini modele iletmez.

macOS Docker Desktop'taki standart Ollama container'ı Apple GPU hızlandırmasını
kullanamaz; bu kurulum CPU üzerinde çalışır. İlk yükleme ve öneri üretimi zaman alabilir.
Model çalışırken container limiti varsayılan 6 GB RAM ve 4 CPU'dur; Docker Desktop VM'sinde
backend/DB için de yeterli bellek bırakılmalıdır.

## Yapılandırma ve sağlayıcılar

| Ayar | Varsayılan | Anlam |
|---|---|---|
| `AI_ENABLED` | `false` | Öneri işi oluşturmayı açar |
| `AI_PROVIDER` | `ollama` | Backend seçimi: `ollama` veya `gemini`; yeniden başlatma gerekir |
| `AI_BASE_URL` | `http://ollama:11434` | Yalnızca Ollama origin'i; Gemini bu ayarı kullanmaz |
| `AI_MODEL` | `qwen3.5:4b` | Seçili sağlayıcının model ID'si; Gemini seçilirse ayrıca değiştirilir |
| `GEMINI_API_KEY` | boş | Yalnızca backend'de tutulan Google API anahtarı; Ollama için gerekmez |
| `AI_REQUEST_TIMEOUT_SECONDS` | `300` | Model isteğinin toplam zaman aşımı; en fazla 600; kuyrukta bekleme dahil değil |
| `AI_CONTEXT_TOKENS` | `8192` | Uygulamanın girdi planlama bütçesi; Ollama'ya `num_ctx` olarak da iletilir |
| `AI_MAX_OUTPUT_TOKENS` | `2048` | Model çıktı sınırı; kesilen çıktı başarılı sayılmaz |
| `OLLAMA_IMAGE` | `ollama/ollama:0.40.13` | Kurulum ve çalışma için aynı imaj |
| `OLLAMA_MEMORY_LIMIT` | `6g` | Container bellek limiti |
| `OLLAMA_CPUS` | `4` | Container CPU limiti |

Her iki sağlayıcının yanıtında yalnızca kısa alan kimlikleri (`S0`, `T0` gibi), kısa eşleme
açıklamaları ve eşleşmeyen alanlar için neden kodları üretilir. Backend bu kimlikleri
izin verilen gerçek alan yollarına çevirir ve neden kodlarına okunabilir açıklamalar
ekler. Frontend API sözleşmesi değişmez. Mevcut/korunmuş hedefler modelin adaylarından
çıkarılır; tüm hedefler korunmuşsa model çağrısı yapılmaz. Tip ve dizi uyumu
kontrolleri backend'de uygulanmaya devam eder; bir hedef için çelişen kaynaklar önerilmez.
IDoc `BEGIN`/`SEGMENT` teknik attribute'ları kaynak ve hedef adaylarından çıkarılır;
eşleşmeyen alanlar listesinde kalırlar ve manuel eşleme davranışını değiştirmezler.
Taraflardan birinde aday iş alanı kalmazsa model çağrısı yapılmaz.

Ollama modeli, istek sonrasında 10 dakika bellekte tutulur (`keep_alive` ve
`OLLAMA_KEEP_ALIVE`). Bu, ardışık işlerde yeniden yükleme maliyetini azaltabilir;
bu süre boyunca model RAM kullanmayı sürdürür. CPU üzerinde çalışan model için
300 saniyelik bekleme bütçesi hız garantisi değildir. Çıktı sınırı aşıldığında
`AI_OUTPUT_TRUNCATED` döner; sınırı düşürmek tek başına yanıtı hızlandırmaz.

`catalog.ai.max-fields=100` taraf başına alan sınırıdır; normalize girdi ayrıca
64 KiB ile sınırlıdır. Bağlam kontrolünde yaklaşık girdi boyutu ve prompt/çıktı
payı kullanılır; gerçek tokenizer ölçümü değildir. Büyük çiftler `AI_CONTEXT_LIMIT`
ile reddedilebilir. Bağlamı artırmak bellek kullanımını da artırabilir.

`MappingSuggestionProvider` sağlayıcıdan bağımsız girdi/çıktı arayüzüdür.
Ollama ve Gemini adaptörleri HTTP ve model çıktısını ortak sözleşmeye çevirir.
`MappingSuggestionProtocol` prompt'u, kısa kimlikleri ve IDoc kontrol filtresini;
`MappingSuggestionValidator` alan yolu, korunmuş bağlantı, tip/dizi ve güven
kontrollerini paylaşır. `AiProviderHttpClient` zaman aşımı, istek iptali, 256 KiB
yanıt sınırı ve hassas ayrıntıları dışarı vermeyen hata işlemesini ortak uygular.
OpenAI adaptörü henüz uygulanmadı. Otomatik sağlayıcı/bulut fallback'i yoktur.
API anahtarı frontend'e, iş kaydına, loglara veya Git'e konmaz.

AI kapalı veya erişilemezken backend readiness ve manuel eşleme çalışmaya devam eder.

## Gemini kurulumu

Gemini dış servistir. Seçilirse kaynak/hedef alan adları, yolları, tipleri, yapısı
ve `useSampleValues=true` ise örnek değerler HTTPS üzerinden Google'a gönderilir.
`useSampleValues=false`, yalnızca örnek değerleri kaldırır; alan metaverisi yine
gönderilir. Ham payload dosyası ve ortam config/credential kayıtları gönderilmez.
Modelden web araması veya araç kullanımı istenmez. Ollama çalışma container'ı
internete kapalı kalır; Gemini isteğini backend kendi dış ağı üzerinden yapar.

Backend `.env` ayarı:

```dotenv
AI_ENABLED=true
AI_PROVIDER=gemini
AI_MODEL=gemini-3.5-flash-lite
GEMINI_API_KEY=google-ai-studio-uzerinden-aldiginiz-anahtar
```

Yukarıdaki anahtar yer tutucudur; gerçek anahtarı sohbet veya frontend ayarına
koymayın. Model ID'si `models/` öneki olmadan yazılır. Örnekteki model güncel
[model listesinde](https://ai.google.dev/gemini-api/docs/models) yer alır;
hesabınızın erişebildiği, yapılandırılmış JSON çıktısını destekleyen bir metin
modeli kullanın. Varsayılan `AI_MODEL` kendiliğinden Gemini'ye çevrilmez.

```sh
docker compose up -d --build --wait app
```

Bu komut DB/backend için yeterlidir; `ai` veya `ai-setup` profili gerekmez.
Önceden çalışan Ollama'yı durdurmak isterseniz `docker compose --profile ai stop ollama`
çalıştırabilirsiniz; model volume'ü silinmez. Gemini etkinleştiğinde Ollama'ya
istek gönderilmez. Ollama'ya dönmek için `AI_PROVIDER=ollama`,
`AI_MODEL=qwen3.5:4b` ayarlayıp Ollama profilini ve backend'i yeniden başlatın.
Sağlayıcı/model değişmeden önce kuyrukta olan işler yeni sağlayıcıya aktarılmaz;
worker bunları `AI_CONFIGURATION_CHANGED` ile sonlandırır.

Adaptör, sabit `https://generativelanguage.googleapis.com/v1beta/models/` adresini
kullanır; `AI_BASE_URL` ile Google anahtarının farklı bir adrese gönderilmesi
mümkün değildir. Anahtar URL yerine `x-goog-api-key` header'ında taşınır.
İçerik üretimi `:generateContent`, JSON Schema `responseJsonSchema` üzerinden
istenir. Capability kontrolü model metaverisini GET ile sorgular, öneri üretmez.
Eksik anahtar veya geçersiz model biçimi iş kuyruğa girmeden reddedilir;
anahtarın yetkisi, kota ve model erişimi sağlayıcı çağrısında ayrıca kontrol edilir.

Gemini'nin [ücretsiz ve ücretli katmanları](https://ai.google.dev/gemini-api/docs/pricing)
vardır; kota, erişim ve veri kullanım koşulları Google hesabınıza/katmanınıza
bağlıdır. Bu proje ücretli plan etkinleştirmez ve ücretsiz kullanım garantisi
vermez. Kota dolunca otomatik tekrar veya başka sağlayıcıya geçiş yapılmaz.

## Frontend sağlayıcıdan bağımsız kalır

Frontend yalnızca capability → iş oluşturma → durum sorgulama → sonuç → kullanıcı
onayıyla append akışını uygular. Job oluşturma gövdesine `provider`, `model`,
`baseUrl` veya `apiKey` eklenmez; bunları değiştiren bir frontend endpoint'i yoktur.
Capability/sonuç yanıtlarındaki `provider` ve `model` bilgilendirme metaverisidir,
seçim alanı değildir. API anahtarı bu yanıtlarda bulunmaz.

`AI_RATE_LIMITED`, `AI_AUTHENTICATION_FAILED`, `AI_API_KEY_MISSING` gibi durumlarda
kullanıcıya anlaşılır hata ve manuel eşleme olanağı sunun; frontend başka bir
sağlayıcı seçerek otomatik tekrar yapmaz. POST/GET/result/append yolları ve mevcut
başarılı yanıt biçimleri her iki sağlayıcı için aynıdır.

## Desteklenen format çiftleri ve kaynak/hedef örnekleri

Kaynak ve hedef birbirinden bağımsız olarak JSON, XML veya IDOC olabilir.
Doğrudan ve ara katmanlı request/response akışlarında dokuz çift desteklenir:

| Kaynak → Hedef | JSON | XML | IDOC |
|---|---|---|---|
| JSON | Desteklenir | Desteklenir | Desteklenir |
| XML | Desteklenir | Desteklenir | Desteklenir |
| IDOC | Desteklenir | Desteklenir | Desteklenir |

IDOC burada `<IDOC>` öğesi içeren XML gösterimidir; düz metin/fixed-width IDOC
dosyası ayrıştırılmaz. CSV/Excel AI önerisi ve append kapsamında değildir.
Formatlar arasında alan önerisi üretmek, payload dönüşümünü çalıştırmak anlamına gelmez.

Mevcut schema kaydetme endpoint'ine ek alanlar eklenmiştir. Örnek ID'ler temsilidir.
Doğrudan akışta aynı şema kaydının iki bağımsız tarafı bulunur:

```http
POST /api/v1/integrations/42/mappings/schemas
Content-Type: application/json
```

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {
    "customerCode": "CUST-001",
    "fullName": "Deniz Örnek",
    "email": "demo@example.com"
  },
  "targetSamplePayload": {
    "clientId": "CUST-001",
    "displayName": "Deniz Örnek",
    "email": "demo@example.com"
  }
}
```

`schemaDefinition` verilmezse `samplePayload` mevcut tanım alanını da doldurur.
Standart JSON Schema `properties/items/type` tanımı varsa `schemaDefinition`
ile gönderilebilir. Hedef için `targetSchemaDefinition` kullanılabilir.
Örnek değerler zorunlu değildir; alan tanımları yeterliyse değer olmadan öneri istenir.
Doğrudan akışta bağımsız hedef tanımı/örneği bulunmazsa AI `SAMPLE_REQUIRED` döner.

XML/IDOC kaynak için `format` ve `rawContent`, JSON kaynak için
`schemaDefinition` ve/veya `samplePayload` kullanılır. Bağımsız hedefin alanları:

| Hedef | Alanlar |
|---|---|
| JSON | `targetFormat: "JSON"`, `targetSchemaDefinition` ve/veya `targetSamplePayload` |
| XML | `targetFormat: "XML"`, `targetRawContent` |
| IDOC | `targetFormat: "IDOC"`, `targetRawContent` |

XML/IDOC hedef `targetSchemaDefinition` ağacını backend üretir; istemciden bu
ağacı kabul etmez. Kaynak ve hedef formatı entegrasyonun ilgili tarafıyla eşleşmelidir.
Doğrudan response şemasında yön tersine döner: `format` entegrasyonun hedef,
`targetFormat` ise kaynak formatıdır. JSON hedeflerde format alanı eski istemciler
için boş bırakılabilir; yeni istemciler açıkça göndermelidir.

JSON → XML örneği:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {"customerCode": "000123", "fullName": "Deniz Örnek"},
  "targetFormat": "XML",
  "targetRawContent": "<Customer><ClientId>000123</ClientId><DisplayName>Deniz Örnek</DisplayName></Customer>"
}
```

JSON → IDOC için hedef tarafı şöyle değiştirilir:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "JSON",
  "samplePayload": {"customerCode": "000123", "fullName": "Deniz Örnek"},
  "targetFormat": "IDOC",
  "targetRawContent": "<DEBMAS07><IDOC BEGIN=\"1\"><E1KNA1M SEGMENT=\"1\"><KUNNR>000123</KUNNR><NAME1>Deniz Örnek</NAME1></E1KNA1M></IDOC></DEBMAS07>"
}
```

IDOC → JSON örneği:

```json
{
  "schemaType": "SOURCE_TO_DEST_REQUEST",
  "format": "IDOC",
  "rawContent": "<DEBMAS07><IDOC BEGIN=\"1\"><E1KNA1M SEGMENT=\"1\"><KUNNR>000123</KUNNR><NAME1>Deniz Örnek</NAME1></E1KNA1M></IDOC></DEBMAS07>",
  "targetFormat": "JSON",
  "targetSamplePayload": {"clientId": "000123", "displayName": "Deniz Örnek"}
}
```

XML → JSON aynı istek yapısını kullanır; kaynak `format: "XML"` ve `rawContent`
olur. XML ↔ IDOC çiftlerinde iki taraf da kendi formatında raw içerik taşır.

Middleware akışında kaynak ve hedef zaten ayrı şema kayıtlarıdır. Her birinde
JSON için `schemaDefinition` ve/veya `samplePayload`, XML/IDOC için `rawContent`
kullanılır. `targetFormat`, `targetRawContent`, `targetSamplePayload` ve
`targetSchemaDefinition` yalnızca doğrudan akış içindir. İzin verilen çiftler:

- Doğrudan: aynı `SOURCE_TO_DEST_REQUEST` veya aynı `DEST_TO_SOURCE_RESPONSE` kaydı.
- Middleware request: `SOURCE_TO_MW_REQUEST` → `MW_TO_DEST_REQUEST`.
- Middleware response: `DEST_TO_MW_RESPONSE` → `MW_TO_SOURCE_RESPONSE`.

JSON örnekleri nesne olmalıdır. İç içe nesne/diziler desteklenir; JSON normalizasyonunda
en fazla 32 seviye, dizi başına en fazla üç temsilî öğe ve 128 karakterlik değerler kullanılır.
XML/IDOC ayrıştırıcısı DTD ve harici entity çözümlemesini engeller; 64 seviye,
10.000 düğüm ve taraf başına 1 MiB metin sınırı vardır. AI'a aktarılacak scalar
alan sayısı ayrıca `max-fields` ile sınırlıdır. Namespace, attribute, tekrarlanan
segmentler ve üçe kadar örnek değer ortak alan profiline aktarılır.

JSON AI alan yolları JSON Pointer biçimindedir: `/customer/id`, `/lines/*/sku`.
`*` dizi öğesini gösterir; JSON anahtarındaki `/` ve `~`, `~1` ve `~0` ile kaçırılır.
Basit eski `customer.id` / `lines[0].sku` yolları alias olarak normalize edilir;
yeni istemciler canonical yolları kullanmalıdır. Tek bir örnekten çıkarılan tip,
alanın bütün gerçek değerlerini kapsayan bir sözleşme değildir.

XML/IDOC yolları mevcut alan ağacındaki tam yollardır; JSON Pointer'a çevrilmez:
`/Customer/ClientId`, `/DEBMAS07/IDOC/E1KNA1M/KUNNR`, `/{urn:customer}Customer/@code`.
Namespace URI'si slash içerse de yol aynen korunur. Tekrarlanan XML segmentlerinin
alt alanları `array=true` taşır; yollarına JSON `*` eklenmez. XML/XSD tip bilgisi
bulunmadığı için scalar alan tipi `unknown` olur; örnekler metin olarak korunur.
`000123` gibi kodlar sayıya dönüştürülmez. XSD veya dış sözlük indirilmez.
Kaynak/hedef ham XML örnekleri ve hedef formatı da context sürümüne dahil edilir;
yalnızca örnek değer değişirse bile önceki öneri eski sayılır.

## Frontend servisleri

Ortak kök: `/api/v1/integrations/{integrationId}/mappings`.
GET işlemleri VIEWER/EDITOR/ADMIN; POST işlemleri EDITOR/ADMIN ve CSRF gerektirir.
POST işlemlerinde 1–100 karakter `Idempotency-Key` başlığı zorunludur.

| İşlem | Endpoint | Başarılı yanıt |
|---|---|---|
| Bağlamı oku | GET `/context` | 200 `{contextRevision,schemas,mappings}` |
| Kullanılabilirlik | GET `/ai/capabilities` | 200 `{enabled,available,provider,model,supportedFormats,maxFieldsPerSide,code}` |
| Öneri işi oluştur | POST `/ai/jobs` | 202 job gövdesi + `Location` |
| Durumu oku | GET `/ai/jobs/{jobId}` | 200 job gövdesi |
| Sonucu al | GET `/ai/jobs/{jobId}/result` | 200 sonuç gövdesi |
| Seçilen bağlantıları ekle | POST `/fields/append` | 200 `{contextRevision,addedMappings}` |

Önce `/context` ile güncel sürümü alın. Sürüm şemalar, örnekler, akış ve mevcut
eşlemelerden hesaplanır; istemci üretmez. Doğrudan akış için iki schema ID aynıdır.

```json
{
  "sourceSchemaId": 101,
  "targetSchemaId": 101,
  "expectedContextRevision": "context-yanitindaki-gercek-deger",
  "protectedMappings": [],
  "options": {"useSampleValues": true}
}
```

Bu gövde POST `/ai/jobs` içindir. DB'deki eşlemeler backend tarafından korunur.
`protectedMappings`, ekranda henüz kaydedilmemiş bağlantılar için aynı schema
ID/alan yollarını taşıyan ek listedir. `options` verilmezse örnek değerler kullanılır;
`useSampleValues=false` ile modele yalnızca alan yapısı/tipleri gönderilir.
Bu seçenek ilk kez kaydedilen örnek veriyi DB'den silmez.

Job gövdesi `jobId,status,createdAt,expiresAt,errorCode,message,resultUrl,pollAfterMs`
alanlarını içerir. Durumlar `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `EXPIRED`
olabilir. `QUEUED`/`RUNNING` iken `pollAfterMs=2000`; terminal durumda null'dır.
İş tarihleri UTC `Instant` değerlerinin ISO-8601 gösterimidir.
`SUCCEEDED` olduğunda `resultUrl` kullanılır. İş oluşturma şemaları/eşlemeleri
değiştirmez; geçerli konfigürasyonla iş kabul edildikten sonra sağlayıcı hatası
oluşursa durum endpoint'i 200 ve `status=FAILED` döner.

Frontend, öneri üretimini açık tutulan tek bir HTTP isteğiyle beklememelidir.
POST hızlıca `202` döner; `pollAfterMs` aralığında kısa GET istekleriyle durum
izlenir. `RUNNING` durumundaki bir işi istemcide 60/180 saniye geçti diye başarısız
göstermeyin. Varsayılan model isteği bütçesi 300 saniyedir; kuyrukta bekleme buna
eklenir. Ekrandan çıkıldıktan sonra aynı `jobId` ile izleme sürdürülebilir.

Sonuç örneği:

```json
{
  "jobId": "is-yanitindaki-id",
  "contextRevision": "is-olusturulurken-alinan-surum",
  "stale": false,
  "provider": "ollama",
  "model": "qwen3.5:4b",
  "suggestions": [
    {
      "suggestionId": "backendin-urettigi-id",
      "sourceSchemaId": 101,
      "sourceFieldPath": "/customerCode",
      "targetSchemaId": 101,
      "targetFieldPath": "/clientId",
      "confidence": "HIGH",
      "reason": "İki alan aynı müşteri kodunu temsil ediyor."
    }
  ],
  "unmatchedSourceFields": [],
  "unmatchedTargetFields": [],
  "warnings": []
}
```

`HIGH`/`MEDIUM` kalibre edilmiş doğruluk yüzdesi değildir. Sayısal
`aiConfidenceScore` null kalır. Belirsiz veya dönüşüm gerektiren alanların
eşleşmeden bırakılması beklenir. Eşleşmeyen kayıtlar `fieldPath,code,reason`
taşır; kodlar `AMBIGUOUS`, `NO_COMPATIBLE_FIELD`, `TYPE_MISMATCH`,
`INSUFFICIENT_CONTEXT`, `TRANSFORMATION_REQUIRED`, `NO_CONFIDENT_MATCH` olabilir.
Sıfır öneri geçerli bir başarılı sonuçtur. Model açıklamalarını düz metin olarak gösterin.

Öneriler frontend'de geçici bağlantılar olarak gösterilir. Kullanıcı düzenler,
kaldırır ve seçtiklerini POST `/fields/append` ile gönderir:

```json
{
  "expectedContextRevision": "is-olusturulurken-alinan-surum",
  "aiJobId": "is-yanitindaki-id",
  "mappings": [
    {
      "suggestionId": "backendin-urettigi-id",
      "sourceSchemaId": 101,
      "sourceFieldPath": "/customerCode",
      "targetSchemaId": 101,
      "targetFieldPath": "/clientId"
    }
  ]
}
```

Yalnızca eklenecek bağlantıları gönderin. Servis mevcut ID'leri korur; yeni bağlantılar
ekler. Hedef başına tek bağlantı kabul edilir; kaynak birden çok hedefe bağlanabilir.
Çakışma, geçersiz alan veya eski sürüm varsa bütün batch reddedilir.
Başarılı ekleme entegrasyonu `DRAFT` yapar; `reviewRequired` kendiliğinden kaldırılmaz.

AI önerisi aynı yollarla kaydedilirse `origin=AI_ACCEPTED,isAiGenerated=true`;
yollar düzenlenirse `origin=AI_EDITED,isAiGenerated=false` olur. `aiJobId` ve
`aiSuggestionId` kökeni korur. Bunları backend belirler. Manuel yeni bağlantıda
`suggestionId` gönderilmez; tamamen manuel eklemede `aiJobId` de gönderilmez.
Manuel append için AI'ın açık olması gerekmez.

Aynı key + aynı gövde, ilk yanıtı döndürür; farklı gövde 409 verir. Tekrar yanıtı
ilk kayıt makbuzudur; sonraki düzenlemelerden sonra güncel durumu `/context` ile okuyun.
Seçilenleri tek onayda kaydedin; ilk kayıttan sonra bağlam sürümü değişir.
`stale=true` veya `CONTEXT_CHANGED` durumunda bağlamı yenileyip yeni iş oluşturun.

Mevcut POST `/fields` hâlâ bütün listeyi değiştirir; AI önerilerini doğrudan bu
endpoint'e göndermeyin. Değişmeden tekrar gönderilen eski AI bağlantılarının kökeni
korunur; yeni manuel bağlantıların AI işareti istemciden kabul edilmez.

## Hatalar ve iş yaşam döngüsü

| HTTP / durum | Kodlar / davranış |
|---|---|
| 400 | `MISSING_REQUEST_HEADER`, `INVALID_IDEMPOTENCY_KEY`, DTO doğrulama hataları |
| 401 / 403 | Oturum, rol veya CSRF eksikliği |
| 404 | Entegrasyon, şema veya bu entegrasyona ait iş yok |
| 409 | `CONTEXT_CHANGED`, `MAPPING_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `AI_RESULT_NOT_READY` |
| 410 | `AI_RESULT_EXPIRED` |
| 413 | `AI_INPUT_TOO_LARGE`, `AI_CONTEXT_LIMIT` |
| 422 | `AI_FORMAT_NOT_SUPPORTED`, `INVALID_SCHEMA_PAIR`, `FORMAT_MISMATCH`, `SAMPLE_REQUIRED`, `FIELD_PATH_NOT_FOUND`, `INVALID_SUGGESTION`, XML/IDOC doğrulama ve karmaşıklık/alan sınırları |
| 429 | `AI_QUEUE_FULL` |
| 503 | `AI_DISABLED`, `AI_PROVIDER_NOT_SUPPORTED`, Gemini için `AI_API_KEY_MISSING` / `AI_CONFIGURATION_INVALID` |
| Job `FAILED` | `AI_UNAVAILABLE`, `AI_MODEL_NOT_INSTALLED`, `AI_TIMEOUT`, `AI_INVALID_OUTPUT`, `AI_OUTPUT_TRUNCATED`, `AI_INTERRUPTED`, `AI_CONFIGURATION_CHANGED`, `AI_CONFIGURATION_INVALID`, `AI_CLOUD_MODEL_FORBIDDEN`, `AI_API_KEY_MISSING`, `AI_AUTHENTICATION_FAILED`, `AI_MODEL_NOT_AVAILABLE`, `AI_RATE_LIMITED`, `AI_CONTENT_BLOCKED`, `AI_INTERNAL_ERROR` |

Ollama capability `code=READY` modelin yüklü olduğunu gösterir; `AI_MODEL_NOT_INSTALLED`
durumunda ayrı kurulum adımını çalıştırın. Gemini `READY`, anahtarla model metaverisinin
okunabildiğini ve `generateContent` desteğini gösterir; kota veya sonraki üretim
başarısını garanti etmez. Model sağlığı genel readiness'i etkilemez.

Gemini 401/403 → `AI_AUTHENTICATION_FAILED`, 404 → `AI_MODEL_NOT_AVAILABLE`,
429 → `AI_RATE_LIMITED`, 400/422 → `AI_CONFIGURATION_INVALID`, 408/504 →
`AI_TIMEOUT`, diğer servis hataları → `AI_UNAVAILABLE` olarak çevrilir. Sağlayıcı
hata gövdesi ve ham model yanıtı kullanıcıya/loglara yansıtılmaz. Güvenlik engeli
`AI_CONTENT_BLOCKED`, `MAX_TOKENS` bitişi `AI_OUTPUT_TRUNCATED` olur; kısmi
eşlemeler başarılı sonuç gibi kaydedilmez.

Kuyruk PostgreSQL'de kalıcıdır; worker DB kilidini bırakıp ayrı thread'de modele gider.
Tek backend instance'ında aynı anda tek model isteği çalışır; varsayılan kuyruk
sınırı 10'dur. Çok instance durumunda `SKIP LOCKED` aynı işi iki worker'ın almasını
engeller, ancak instance başına worker vardır. Bu sürüm tek instance yerel kullanım içindir.
Kesilmiş bir çalışan iş lease süresi dolunca FAILED olur; otomatik tekrar model çağrısı yoktur.

`AI_TIMEOUT` sorununda önce işin durumunu kontrol edin. İş `FAILED` ve kod
`AI_TIMEOUT` ise backend model süresini doldurmuştur; iş hâlâ `RUNNING` ise
frontend'in bekleme sınırını ve durum sorgulamasını inceleyin. Yeni ayarları
uygulamak için app container'ını yeniden oluşturun. Önceden başarısız olmuş işler
kendiliğinden tekrar çalışmaz; yeni iş için yeni `Idempotency-Key` kullanın.
Aynı anahtarı tekrar göndermek mevcut başarısız işi döndürür.

İşlerin süresi varsayılan 24 saattir. Ham normalize girdi başarılı/başarısız iş
sonunda temizlenir. Süre dolunca girdi/sonuç JSON'u da temizlenir; temizlik AI
kapalıyken de çalışır. Durum/idempotency metadata'sı ve append makbuzları entegrasyon
silinene kadar tutulur. Entegrasyon silinince job/makbuzlar DB cascade ile silinir.
Kaydedilmiş şema örnekleri bu geçici iş temizliğinin kapsamı dışındadır.

## Doğrulama

```sh
./mvnw -B '-Dtest=*Tests,!IntegrationCatalogApplicationTests' test
python3 scripts/check_repository.py
python3 scripts/check_ai_network.py
python3 scripts/smoke_ai_mapping.py
```

Smoke betiği yerel `.env`/environment içindeki yönetici hesabıyla giriş yapar;
geçici bir entegrasyon ve bağımsız örnekler oluşturur. Manuel bağlantının korunmasını,
gerçek model önerisini, onay kaydını, idempotency ve eski sürüm reddini doğrular;
sonunda kendi test entegrasyonunu siler. Önceden var olan kayıtlara ekleme yapmaz.
Müşteri kodu ve adının beklenen alanlara bağlandığını da kontrol eder; yalnızca
boş olmayan bir öneri listesi dönmesi smoke testini geçirmek için yeterli değildir.
Betik backend'de seçili sağlayıcıyı kullanır. Gemini seçiliyse sentetik örnekler
Google'a gönderilir ve hesabın API kotası/fiyatlandırması uygulanır. Anahtarsız
otomatik `GeminiProviderTests` testleri HTTP yanıtlarını taklit eder; canlı Google
erişimini, hesabın kotasını veya gerçek modelin eşleme başarısını doğrulamaz.

`AiMappingPostgresTests`, diğer PostgreSQL testleriyle aynı
`-Dcatalog.payload.postgres-test=true` opt-in ayarını kullanır; yalnızca ayrı test DB'sinde
çalıştırın. Normal testler sahte sağlayıcı kullanır; CI model indirmez veya bulut servisi çağırmaz.
Yerel geliştirmede AI tablo ve kolonları da Hibernate `ddl-auto=update` ile
entity tanımlarından oluşturulur. Bu projede sürümlü DB migration sistemi yoktur;
yeni kurulum boş veritabanıyla yapılır.

Gerçek model smoke betiği format seçebilir:

```sh
python3 scripts/smoke_ai_mapping.py --source-format JSON --target-format IDOC
python3 scripts/smoke_ai_mapping.py --source-format XML --target-format JSON
python3 scripts/smoke_ai_mapping.py --matrix
```

`--matrix` dokuz çifti sıralı çalıştırır. Her çift için geçici entegrasyon oluşturulur;
mevcut manuel bağlantı korunur, AI önerisi onaylanır ve fixture sonunda silinir.
Modelin CPU üzerinde çalıştığı ortamlarda bütün matris birkaç dakika sürebilir.

### Canlı denemelerin kapsamı

Otomatik testler doğrudan ve middleware request/response yönlerini, dokuz format
çiftini, namespace/attribute yollarını, tekrar eden öğeleri ve XML dış kaynak
engellerini kapsar. Provider HTTP testleri sahte yanıtlarla çalışır; gerçek model
kalitesini veya Google hesabının kotasını doğrulamaz.

31 Ekim 2026 yerel ölçümleri; süreler durum sorgulaması ve kayıt kontrollerini içerir:

| Sağlayıcı / ortam | Örnek | Gözlenen süre |
|---|---|---|
| Ollama, Apple M4 / 16 GB, CPU/Docker, `qwen3.5:4b` | Küçük JSON/JSON, JSON/IDoc, IDoc/JSON | 20,2–40,6 saniye |
| Aynı Ollama kurulumu | 15 alanlı JSON/XML | 170,2 saniye |
| Google Gemini `gemini-3.5-flash-lite` | Küçük JSON/JSON, JSON/XML, JSON/IDoc | Her biri yaklaşık 2,1 saniye |

Geçici örneklerde müşteri kodu/adı için doğru bağlantılar, mevcut manuel email
bağlantısının korunması, onayla kayıt, idempotency ve eski bağlam reddi doğrulandı.
Ollama için önceki sürümde dokuz format çiftinin canlı matrisi de çalıştırıldı;
son protokolde yukarıdaki çiftler yeniden kontrol edildi. Test betikleri kendi
kayıtlarını temizler. Ölçümler gecikme veya kurumsal alanlarda doğruluk garantisi
değildir; model yükü, şema büyüklüğü ve donanım sonucu değiştirir.

Resmi kaynaklar: [Gemini içerik üretme API'si](https://ai.google.dev/api/generate-content),
[Gemini yapılandırılmış çıktı](https://ai.google.dev/gemini-api/docs/structured-output),
[Gemini model listesi](https://ai.google.dev/gemini-api/docs/models),
[Gemini fiyatlandırma](https://ai.google.dev/gemini-api/docs/pricing),
[Ollama Docker](https://docs.ollama.com/docker),
[yerel kullanım/bulut ayarı](https://docs.ollama.com/faq),
[Docker iç ağları](https://docs.docker.com/reference/compose-file/networks/#internal),
[Qwen model lisansı](https://huggingface.co/Qwen/Qwen3.5-4B/blob/main/LICENSE).
