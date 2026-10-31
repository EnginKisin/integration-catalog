# Ortak sistem kataloğu ve frontend sözleşmesi

API kökü `/api/v1`'dir. Oturum ve CSRF akışı
[frontend rehberindeki](frontend-integration.md) gibidir. Sistem yönetimi tek
katalog kullanır; kaynak/hedef/ara katman seçimleri aynı sistem listesinden yapılır.

## Kimlikler ve ilişkiler

- Eski `/sources`, `/destinations`, `/middlewares` endpoint'leri kaldırıldı.
- Entegrasyon istekleri `sourceSystemId`, `destinationSystemId`,
  `middlewareSystemId` kullanır; eski ID alanlarına uyumluluk yoktur.
- Liste ve detay yanıtları her rolün sistem ID'sini ve adını içerir.
- `environmentTypes`, yapılandırılmış ortam türlerini tekrar etmeden
  `DEV`, `TEST`, `PROD` sırasıyla içerir. Hiç ayar yoksa `[]` döner.
- Sistem adı yerine ID ile seçim, ilişkilendirme ve harita tekilleştirmesi yapılır.

## Sistem yönetimi

| Metot / yol | Gövde | Başarı |
|---|---|---|
| GET `/systems` | Yok | 200 sistem dizisi |
| GET `/systems?active=true` veya `?active=false` | Yok | 200 filtrelenmiş sistem dizisi |
| GET `/systems/{id}` | Yok | 200 sistem |
| POST `/systems` | `{name,description}` | 201 sistem |
| PUT `/systems/{id}` | `{name,description}` | 200 sistem |
| PATCH `/systems/{id}/active` | `{active}` | 200 sistem |
| DELETE `/systems/{id}` | Yok | 204 boş |

POST/PUT örneği:

```json
{"name":"SAP","description":"Kurumsal ERP"}
```

Sistem yanıtı:

```json
{"id":1,"name":"SAP","description":"Kurumsal ERP","active":true,"usageCount":2}
```

`usageCount` üç rolün toplamındaki farklı entegrasyon sayısıdır; aynı entegrasyon
bir kez sayılır. `name` boş olamaz/en fazla 100, `description` null olamaz/en fazla
500 karakterdir. Yeni sistem aktiftir; aktiflik PATCH ile değiştirilir.
Silme ADMIN, diğer yazmalar EDITOR/ADMIN, okumalar üç role de açıktır.
Kullanımdaki sistem silinemez (409); kullanım ve aktiflik kuralları aşağıdadır.

## Kullanım sayısı ve silme

`usageCount`, sistemin kaynak, hedef veya ara katman olduğu entegrasyonların
sayısıdır. Taslak ve tamamlanmış kayıtlar dahildir. Aynı sistem bir entegrasyonda
üç rolü de taşıyorsa o entegrasyon bir kez sayılır. Listeleme sayıları toplu sorguyla
alınır; her sistem için ayrı count sorgusu çalıştırılmaz.

Kullanılan bir sistem pasif olsa bile silinemez. Silme hiçbir entegrasyonun
referansını boşaltmaz. Örnek hata:

```json
{"status":409,"message":"System is used by 5 integration(s); deactivate it instead"}
```

Katalog güncellemeleri, aktiflik ve silme sistem satırını transaction boyunca
kilitler. Entegrasyona atamada seçilen ortak sistemler birer kez, artan ID
sırasıyla kilitlenir. Kullanım silme anında tekrar kontrol edilir.

## Pasif kayıtlar ve rol değişikliği

Pasifleştirme mevcut entegrasyonları veya durumlarını değiştirmez. Pasif sistemin
adı ve kimliği liste/detayda görünür. Aynı entegrasyon alanındaki mevcut pasif
seçim korunabilir; yeni entegrasyonda kullanma, yeni sistem seçme veya pasif bir
sistemi başka bir role taşıma 409 döner.

Düzenleme ekranında mevcut pasif seçimi listeden düşürmeyin. Yeni seçenekler için
`active=true` listesini kullanın; mevcut pasif kaydı entegrasyon yanıtından veya
sistem detayından ayrıca gösterin. Sistem adının değiştirilmesi ortak kaydı
kullanan bütün entegrasyonların sonraki liste/detay yanıtlarına yansır.

## Entegrasyon oluşturma ve güncelleme

`POST /integrations` 201, `PUT /integrations/{id}` 200 döndürür.
PUT tam temel bilgi güncellemesidir; yalnızca değişen alanı göndermeyin.

```json
{
  "name":"Sipariş aktarımı",
  "description":"SAP siparişlerinin CRM sistemine aktarımı",
  "sourceSystemId":1,
  "destinationSystemId":2,
  "middlewareSystemId":3,
  "sourcePayloadFormat":"JSON",
  "destinationPayloadFormat":"XML",
  "hasMiddleware":true,
  "isSync":false
}
```

Doğrudan akışta `hasMiddleware=false`, `middlewareSystemId=null` kullanılır.
Sistemlerde sabit rol bulunmaz: başka bir entegrasyonda `sourceSystemId=2`,
`destinationSystemId=1` gönderilebilir; aynı SAP kaydı tekrar oluşturulmaz.
Aynı sistem ID'sinin bir entegrasyonda birden fazla rolde kullanılması da geçerlidir.

Liste ve detayda kullanılan yanıt biçimi (tarih ve ortam ayarı ayrıntıları kısaltıldı):

```json
{
  "id":10,
  "name":"Sipariş aktarımı",
  "description":"SAP siparişlerinin CRM sistemine aktarımı",
  "sourceSystemId":1,
  "sourceSystemName":"SAP",
  "destinationSystemId":2,
  "destinationSystemName":"CRM",
  "middlewareSystemId":3,
  "middlewareSystemName":"Entegrasyon Platformu",
  "sourcePayloadFormat":"JSON",
  "destinationPayloadFormat":"XML",
  "hasMiddleware":true,
  "isSync":false,
  "status":"DRAFT",
  "reviewRequired":false,
  "environmentTypes":["DEV","PROD"]
}
```

Doğrudan akışta ara katman ID ve adı null döner. Liste ve detayda
`environmentConfigs` da bulunur; normal yanıtlarda secret değerleri yoktur.
`GET /integrations?keyword=...` entegrasyon adı ve üç roldeki sistem adlarında arar.

## Doğrulamalar ve form davranışı

- `sourceSystemId` ve `destinationSystemId` zorunlu pozitif kimliklerdir.
- `hasMiddleware=true` için pozitif `middlewareSystemId` zorunludur; false için
  null olmalı veya hiç gönderilmemelidir. Flag ve `isSync` zorunludur.
- Referans verilen sistem yoksa 404; yeni seçim pasifse 409 döner. Mevcut pasif
  seçim yalnızca aynı entegrasyonun aynı rolünde korunabilir.
- Düzenlemede pasif mevcut seçimi gösterin; yeni seçenekleri `active=true` ile alın.
- İsim en fazla 100 karakter ve boş olamaz; açıklama null olamaz/en fazla 500'dür.
- Payload formatları ve akış/şema kuralları [payload sözleşmesindeki](payload-content.md)
  gibidir. Ortam `configTarget` değerleri `DESTINATION` ve `MIDDLEWARE` olarak kalır.
- Teknik güncellemede DRAFT/inceleme ve ilk başarılı örnekte eski akış temizliği
  korunur. Başarılı kayıttan sonra ilgili listeleri yeniden okuyun.
- Teknik PUT mevcut ortam/credential/şema/eşlemeleri korur. İnceleme gerekiyorsa
  `POST /integrations/{id}/review/confirm` için `{ "confirmed": true }` gönderin;
  ardından ayrıca complete çağırın. Sistem adı/aktiflik değişikliği entegrasyonu
  taslağa döndürmez. Ayrıntılar [iş akışında](integration-workflow.md).

## Harita ve ortam filtresi

Harita için ayrı bir endpoint yoktur; `GET /integrations` verisini kullanın.
Düğüm anahtarı sistem ID'si olmalıdır. Rolü veya entegrasyon ID'sini düğüm
anahtarına eklemeyin. Kenar anahtarında entegrasyon ID'sini tutarak aynı sistemler
arasındaki farklı entegrasyonları koruyun. Örnek veri dönüşümü:

```javascript
function graphData(integrations, environment = 'ALL') {
  const nodes = new Map();
  const edges = [];
  for (const row of integrations) {
    if (environment !== 'ALL' && !row.environmentTypes.includes(environment)) continue;
    const addNode = (id, name) => nodes.set(String(id), {id: String(id), label: name});
    const addEdge = (from, to, step) => edges.push({
      id: `${row.id}:${step}`, source: String(from), target: String(to), integrationId: row.id
    });
    addNode(row.sourceSystemId, row.sourceSystemName);
    addNode(row.destinationSystemId, row.destinationSystemName);
    if (row.hasMiddleware) {
      addNode(row.middlewareSystemId, row.middlewareSystemName);
      addEdge(row.sourceSystemId, row.middlewareSystemId, 'source-middleware');
      addEdge(row.middlewareSystemId, row.destinationSystemId, 'middleware-destination');
    } else {
      addEdge(row.sourceSystemId, row.destinationSystemId, 'source-destination');
    }
  }
  return {nodes: [...nodes.values()], edges};
}
```

Bu dönüşüm gidiş akışını çizer; `isSync` bilgisini kullanarak yanıt yönü ayrıca
sunulabilir. Alan eşleme yönleri değişmemiştir. Ortam filtresi istemcide
`environmentTypes.includes(secilenOrtam)` ile uygulanır; `ALL` bütün kayıtları
kapsar. Ortam ayarı olmayan kayıtlar yalnızca ALL görünümünde yer alır.
