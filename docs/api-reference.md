# API sözleşmesi ve endpoint dizini

Yerel API kökü `http://localhost:8080/api/v1`. Aşağıdaki yollar bu köke göredir.
JSON yazmalarda `Content-Type: application/json`; login form-urlencoded'dır.
Başarılı yanıtlar ortak `data` zarfına sarılmaz. Bütün değişikliklerde CSRF gerekir.
Rol/credential ayrıntıları [yetkilendirme belgesindedir](authentication.md).

## Giriş ve kullanıcılar

| Metot / yol | Başarı yanıtı | Erişim |
|---|---|---|
| GET `/auth/csrf` | 200 `{headerName,parameterName,token}` | Herkes |
| POST `/auth/login` | 200 boş | Herkes + CSRF |
| POST `/auth/logout` | 204 boş | CSRF |
| GET `/auth/me` | 200 profil | Oturum |
| GET `/users?page=0&size=20` | 200 `{items,page,size,totalElements,totalPages}` | ADMIN |
| POST `/users` | 201 kullanıcı | ADMIN |
| PUT `/users/{id}/active` | 200 kullanıcı | ADMIN |
| PUT `/users/{id}/role` | 200 kullanıcı | ADMIN |
| PUT `/users/{id}/permissions` | 200 kullanıcı | ADMIN |
| POST `/users/{id}/invitations/resend` | 202 boş | ADMIN |
| POST `/users/{id}/password-reset` | 202 boş | ADMIN |
| POST `/users/{id}/sessions/revoke` | 204 boş | ADMIN |
| GET `/users/mail-status` | 200 `{pending,failed}` | ADMIN |
| POST `/auth/invitations/accept` | 204 boş | Geçerli token |
| POST `/auth/password-reset/request` | 202 `{message}` | Herkes |
| POST `/auth/password-reset/confirm` | 204 boş | Geçerli token |
| POST `/auth/password/change` | 204 boş | Oturum + mevcut parola |
| POST `/auth/email/change` | 202 boş | Oturum + mevcut parola |
| POST `/auth/email/confirm` | 204 boş | Geçerli token |

Kullanıcı yanıtı `id, username, email, emailVerified, active, passwordResetRequired,
role, permissions` alanlarını taşır. Detay GET kullanıcı endpoint'i, açık kayıt ve
kullanıcı DELETE endpoint'i yoktur. Kullanıcı listesi ID sıralıdır; sayfa sıfırdan
başlar, size 1–100. Gövde örnekleri ve token süreleri [kullanıcı yönetimindedir](user-management.md).
Login/logout filtrelerle uygulanır; Swagger'da controller endpoint'i olarak
görünmelerine veya Swagger Authorize düğmesinin giriş yapmasına güvenmeyin.
`GET /auth/me` profili `username,email,emailVerified,active,authorities,permissions`
alanlarını döndürür. `authorities` değerleri `ROLE_ADMIN` gibi `ROLE_` öneki taşır.

## Ortak kataloglar

Ortak katalog `/systems` altındadır; sistemin kaynak/hedef/ara katman rolü entegrasyonda belirlenir.

| Metot / yol | Başarı yanıtı | Erişim |
|---|---|---|
| GET `/systems` veya `/systems?active=true/false` | 200 DTO dizisi | VIEWER / EDITOR / ADMIN |
| GET `/systems/{id}` | 200 DTO | VIEWER / EDITOR / ADMIN |
| POST `/systems` | 201 DTO | EDITOR / ADMIN |
| PUT `/systems/{id}` | 200 DTO | EDITOR / ADMIN |
| PATCH `/systems/{id}/active` | 200 DTO | EDITOR / ADMIN |
| DELETE `/systems/{id}` | 204 boş | ADMIN; kullanım sıfır olmalı |

DTO: `id,name,description,active,usageCount`. POST/PUT gövdesi `{name,description}`;
PATCH gövdesi `{active}`. Listelerde sayfalama yoktur; aktiflik filtresi verilmezse
pasifler de döner. Ad/açıklama benzersizliğini frontend varsaymamalıdır.

## Entegrasyonlar ve mapping

| Metot / yol | Başarı yanıtı | Erişim |
|---|---|---|
| GET `/integrations?keyword=...` | 200 DTO dizisi | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}` | 200 DTO | VIEWER / EDITOR / ADMIN |
| POST `/integrations` | 201 DTO | EDITOR / ADMIN |
| PUT `/integrations/{id}` | 200 DTO | EDITOR / ADMIN |
| DELETE `/integrations/{id}` | 204 boş | ADMIN |
| POST `/integrations/{id}/configs` | 200 boş | EDITOR / ADMIN; secret değişikliği ek izinli |
| POST `/integrations/{id}/review/confirm` | 200 DTO | EDITOR / ADMIN |
| PATCH `/integrations/{id}/complete` | 200 boş | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/schemas` | 200 şema dizisi | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/schemas` | 200 şema | EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/schemas/upload` | 200 şema; multipart metadata + file | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/schemas/{schemaId}/sample` | 200 dosya; yoksa 404 | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/fields` | 200 eşleme dizisi | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/fields` | 200 boş | EDITOR / ADMIN |
| GET `/integrations/{id}/configs/{environment}/{target}/credentials` | 200 `{authCredentials}` | ADMIN veya VIEW_CREDENTIALS |

## AI önerileri ve güvenli ekleme

| Metot / yol | Başarı yanıtı | Erişim |
|---|---|---|
| GET `/integrations/{id}/mappings/context` | 200 `{contextRevision,schemas,mappings}` | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/capabilities` | 200 kullanılabilirlik / sağlayıcı / formatlar | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/ai/jobs` | 202 iş; `Idempotency-Key` zorunlu | EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/jobs/{jobId}` | 200 iş durumu | VIEWER / EDITOR / ADMIN |
| GET `/integrations/{id}/mappings/ai/jobs/{jobId}/result` | 200 öneriler / eşleşmeyen alanlar | VIEWER / EDITOR / ADMIN |
| POST `/integrations/{id}/mappings/fields/append` | 200 eklenen bağlantılar / yeni bağlam; `Idempotency-Key` zorunlu | EDITOR / ADMIN |

Job/append beklenen `contextRevision` alır; eski bağlam 409, henüz hazır olmayan
sonuç 409, süresi dolan sonuç 410 döner. AI işi başarısız olursa durum GET'i 200
ve `status=FAILED` döner. Tam istek/yanıtlar ve hata kodları [AI sözleşmesindedir](ai-mapping.md).
Sağlayıcı/model ve Gemini anahtarı backend ayarıdır; frontend isteğinden seçilmez.

## Katalog ve mapping yanıt alanları

Entegrasyon DTO alanları: `id,name,description,sourceSystemId,sourceSystemName,
destinationSystemId,destinationSystemName,middlewareSystemId,middlewareSystemName,
sourcePayloadFormat,destinationPayloadFormat,isSync,hasMiddleware,status,
reviewRequired,createdAt,updatedAt,environmentTypes,environmentConfigs`.
Üç rolün sistem adı aynı ortak katalogdan gelir. Şemalar ve field mapping'ler
listede/detayda gömülü değildir. `environmentTypes`, yapılandırılmış ortamların
tekilleştirilmiş listesidir; ayar yoksa `[]` döner.
Arama entegrasyon adı ve üç roldeki sistem adlarında büyük/küçük harf duyarsızdır;
sonuç dizisinde sayfalama veya istemciye açık sıralama parametresi yoktur.

Config response: `id,configTarget,environmentType,url,timeoutMs,authType,
credentialsConfigured,retryCount,retryIntervalMs`. `authCredentials` içermez.
Şema: `id,schemaType,format,schemaDefinition,samplePayload,targetSchemaDefinition,targetSamplePayload,targetFormat,targetRawContent,rawContent,mappingSupported,csvOptions,
fileName,contentType,fileSize,sampleDownloadUrl`. Format bazlı içerik, dosya yükleme
ve hata örnekleri [payload sözleşmesindedir](payload-content.md). Field mapping:
`id,sourceSchemaId,sourceFieldPath,targetSchemaId,targetFieldPath,isAiGenerated,aiConfidenceScore,origin,aiJobId,aiSuggestionId`.
İsteğe bağlı AI servisleri, bağımsız JSON/XML/IDOC tarafları ve append sözleşmesi
[AI eşleme belgesindedir](ai-mapping.md).
Ollama/Gemini seçimi backend ayarıdır; frontend sağlayıcı/model/API anahtarı
göndermez. Her iki sağlayıcı aynı AI endpoint ve yanıt sözleşmesini kullanır.

Config ve eski `/mappings/fields` POST gövdeleri JSON dizisidir; mevcut listenin tamamını
temsil eder. Eksik öğeler silinir; `[]` temizler. Config eşleşmesi ortam/hedef
çiftine göredir. Middleware kullanılmıyorsa gönderilen MIDDLEWARE config'leri
serviste filtrelenir; istemci bunları göndermemelidir. Şema POST ise tür bazlı
upsert'tir. Şema DELETE veya tekil config/field PATCH endpoint'i yoktur.
Akış değişiminden sonraki ilk başarılı şema POST/upload, önceki akışın uyumsuz
şemalarını ve bağlı eşlemeleri yeni kayıtla aynı transaction'da temizler.
Başarısız işlem eski kayıtları korur; başarılı işlemden sonra schemas ve fields
listeleri yeniden okunmalıdır.
Tam gövdeler ve tamamlama matrisi [iş akışı belgesindedir](integration-workflow.md).

Örnek gövdeler ve harita/ortam filtresi uyarlaması [frontend sistem sözleşmesindedir](systems-frontend.md).

## Enum değerleri

| Alan | Değerler |
|---|---|
| `sourcePayloadFormat`, `destinationPayloadFormat` | `JSON`, `XML`, `IDOC`, `CSV`, `EXCEL` |
| `environmentType`, credential yolundaki environment | `DEV`, `TEST`, `PROD` |
| `configTarget`, credential yolundaki target | `DESTINATION`, `MIDDLEWARE` |
| `authType` | `NONE`, `BASIC_AUTH`, `OAUTH2`, `API_KEY` |
| `status` | `DRAFT`, `COMPLETED`; backend belirler |
| `schemaType` | `SOURCE_TO_MW_REQUEST`, `MW_TO_DEST_REQUEST`, `DEST_TO_MW_RESPONSE`, `MW_TO_SOURCE_RESPONSE`, `SOURCE_TO_DEST_REQUEST`, `DEST_TO_SOURCE_RESPONSE` |
| `role` | `ADMIN`, `EDITOR`, `VIEWER` |
| `permissions` | `VIEW_CREDENTIALS`, `MANAGE_CREDENTIALS` |

## Başlıca alan doğrulamaları

| Alan | Kural |
|---|---|
| Katalog / entegrasyon `name` | Boş olamaz, en fazla 100 karakter |
| `description` | Null olamaz, en fazla 500 karakter; boş string kabul edilir |
| Entegrasyon temel alanları | sourceSystemId ve destinationSystemId zorunlu pozitif sistem ID'leri; formatlar, isSync ve hasMiddleware zorunlu |
| `middlewareSystemId` | hasMiddleware=true ise pozitif ID; false ise null/alan yok |
| Config ortam/hedef/authType | Zorunlu enum |
| `url` | Boş olamaz, en fazla 100 karakter; HTTP URL sözdizimi doğrulaması yok |
| `timeoutMs` | Verilirse > 0, milisaniye |
| `retryCount`, `retryIntervalMs` | Verilirse >= 0; interval milisaniye |
| `authCredentials` | Dinamik JSON map; yeni değer boş olamaz; tür bazlı anahtar doğrulaması yok |
| JSON kaynak içeriği | `schemaDefinition` veya `samplePayload` nesnesi gerekir; yalnız örnek verilirse tanım da bundan oluşturulur. Tamamlamada tanım boş olamaz. Diğer formatlarda `schemaDefinition` backend tarafından üretilir |
| `format`, `rawContent`, `csvOptions`, dosya | [Format bazlı sözleşme ve limitler](payload-content.md) |
| Alan yolları | Boş olamaz, en fazla 500 karakter |
| Field mapping `isAiGenerated` | Eski liste kaydında zorunlu; yeni manuel bağlantıda false, değişmeyen mevcut AI bağlantısında köken korunur |
| Field mapping `aiConfidenceScore` | Yeni manuel/AI kayıtlarında null; güven seviyeleri AI sonuçlarında HIGH/MEDIUM şeklindedir |
| Kullanıcı adı | 1–100 karakter, `[a-zA-Z0-9._-]+`, büyük/küçük harfe duyarlı |
| E-posta | Geçerli e-posta, en fazla 254 karakter; normalize edilip küçük harfle saklanır |
| Yeni parola | 12–128 karakter, yalnızca boşluk olamaz |
| Hesap bağlantısı `token` | URL-safe Base64, 43 karakter; `[A-Za-z0-9_-]{43}` |

JSON dosyası yüklenirken `schemaDefinition` dosyadan okunur. XML/IDOC için alan
ağacı, CSV/Excel için `{}` döner. Metin/dosya sözleşmesi [payload belgesindedir](payload-content.md).
Kaydetme doğrulaması ile tamamlanma doğrulaması farklıdır. Örneğin JSON'da `{}`
kaydedilebilir ama tamamlanamaz; yanlış mapping akış çifti tamamlamada reddedilir.

## Hata sözleşmesi

Standart uygulama hatası örneği:

```json
{
  "timestamp":"2026-10-18T12:00:00",
  "status":409,
  "error":"Conflict",
  "message":"Integration is not ready for completion",
  "path":"/api/v1/integrations/10/complete",
  "details":["At least one environment configuration is required"]
}
```

İçerik doğrulama hataları ayrıca `code` alanı içerir; bütün hatalarda bulunması
garanti değildir. Boyut aşımı 413 döner. `details` özellikle tamamlama hatalarında bulunur; bütün hatalarda dolu olmasını
beklemeyin. DTO doğrulaması her alan için ayrı hata map'i sağlamaz. Auth hız
limiti yanıtı yalnızca `status,error,message` içerebilir; istemci alanları opsiyonel okumalıdır.
IP hız sınırı 429 yanıtında `Retry-After: 60` gönderir; hesap/amaç bazlı token üretme
sınırında aynı başlığın varlığı garanti değildir.

400 geçersiz istek, 401 giriş gerekli/hatalı, 403 yetki/CSRF, 404 kayıt bulunamadı,
409 iş kuralı, 410 AI sonucu süresi dolmuş, 413 içerik/dosya boyut sınırı,
422 AI girdi/şema doğrulaması, 429 hız/kuyruk sınırı, 500 beklenmeyen hata,
503 geçici servis/DB sorunudur.
Bu eşleme her framework hatasının normalize edildiği garantisi değildir: bazı
tip dönüşümü/metot hataları genel handler'a düşebilir. Güvenlikte tanımlanmamış
yollar kapalı olduğundan bilinmeyen URL her zaman 404 dönmeyebilir.

Health yolları `/api/v1` dışında, `/actuator/health` altındadır; yanıtları ve
200/503 kuralları [health belgesindedir](configuration.md#sağlık-kontrolleri).
