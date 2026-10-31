# Mimari, kapsam ve doğrulama

Backend tek bir Spring Boot uygulamasıdır. Controller katmanı HTTP/DTO,
service katmanı iş kuralları ve transaction, repository katmanı JPA/PostgreSQL
erişimini üstlenir. Güvenlik filtreleri oturum, CSRF, rol ve güncel hesap kontrolünü yapar.

## Veri ilişkileri

`CatalogSystem` entity'si `systems` tablosundadır; sabit bir rolü yoktur.
Entegrasyon kaynak/hedef için zorunlu, ara katman için isteğe bağlı olarak bu
tabloya referans verir. Ortam ayarları,
şemalar ve alan eşlemeleri entegrasyon aggregate'ına aittir. Ortak katalog kaydını
silmek entegrasyonu veya FK'lerini boşaltmaz; kullanım varsa silme engellenir.
`usageCount` taslak ve tamamlanmış kayıtları üç rolde sayar; aynı entegrasyon
bir kez sayılır. Ortak sistem satırları, rol sırasından bağımsız olarak artan ID
sırasında kilitlenir.

Her kullanıcı hesabının birden fazla token kaydı olabilir. Hesaplar `user_accounts`, ek izinler `user_permissions`, tek kullanımlık
token'lar `account_tokens`, hesap audit'i `account_audit`, şifreli posta işleri
`mail_outbox` tablolarında saklanır. Entity adlarından üretilen fiziksel şema
ayrıntıları için model sınıfları esas alınmalıdır.

## Tutarlılık ve güvenlik

Entegrasyon teknik/alt veri değişiklikleri ve tamamlama transaction içinde
çalışır. Güncellenen entegrasyon ve seçilen katalog satırları ilgili işlemlerde
kilitlenir. Katalog aktiflik/silme kontrolleri ile yeni bağlantı atama aynı satırı
kilitleyerek sıralanır. Son aktif ADMIN koruması ve token tüketimi de transaction
ve satır kilitleri kullanır. Gerçek PostgreSQL davranışı ayrıca doğrulanmalıdır.

Entegrasyonda entity sürümlemesi bulunur; mevcut entegrasyon PUT ve bütün listeyi
değiştiren mapping POST istemciden beklenen sürüm almaz. Bu eski akışlarda uzun
süre açık kalan formun başka kullanıcının düzenlemesini ezmesini bütünüyle önleyen
bir protokol henüz yoktur. Yeni AI context/job/append akışı ise şemalar ve mevcut
eşlemelerden hesaplanan `contextRevision` ile eski sürümden kayıt yapılmasını reddeder.

Ara katman ekleme/kaldırma sırasında alt veriler korunur. Güncel akışa uygun ilk
başarılı örnek kaydı eski akışın uyumsuz şemalarını ve bunlara bağlı eşlemeleri
temizler. Eşlemeler şemalardan önce silinir; yeni kayıt ve temizlik tek transaction
içindedir. Hata durumunda eski veriler korunur. Sözleşme
[payload belgesindedir](payload-content.md#format-ve-akış-modeli).

Parolalar PBKDF2 ile hash'lenir; dış sistem bilgileri geri çözülebilir AES-GCM
şifrelemesiyle korunur. Token doğrulama tablosunda SHA-256 hash vardır; e-posta
gönderilebilmesi için ham bağlantının şifreli kopyası geçici olarak outbox'ta tutulur.
Roller ve credential izinleri ayrıdır. API hata yanıtları ham exception veya
SQL metni taşımaz; `requestId` / `X-Request-ID` hata loglarıyla ilişkilendirilir. Oturum sürümü her korunan istekte güncel
hesapla karşılaştırılır. Ayrıntılar: [güvenlik](authentication.md), [kullanıcı yaşam döngüsü](user-management.md).

## Posta akışı

202/201 yanıtı SMTP teslimini beklemez. Worker'ın SMTP kabulünden sonra commit
öncesi kesilmesi çift gönderime yol açabilir; token tek kullanımlıdır. Uzun kuyruk
beklemesinde token postaya ulaşmadan sona erebilir. `mail-status` pending/failed
sayılarını verir; gelen kutusuna teslim edildiğini ölçmez. SMTP arızası katalog
readiness'ını düşürmez.

## Yayın ve üretim sınırları

Backend yerel kullanım ve demo/MVP kapsamındadır; üretim kabulü tamamlanmış
değildir. [CI ve repo kontrolü](../README.md#paylaşım-kontrolleri-ve-ci) yerel
kaynakları doğrulamaya yardımcı olur; uzaktaki Actions sonucu ayrıca izlenmelidir.


- Entegrasyon çalıştırma ve dış sistem bağlantı testi yoktur. İsteğe bağlı
  [AI alan önerileri](ai-mapping.md) JSON/XML/IDOC çiftlerini destekler; kullanıcı onayı gerekir.
  Sağlayıcı (`ollama`/`gemini`) backend ayarıyla seçilir; her iki adaptör aynı
  alan doğrulama ve append kurallarını kullanır. Gemini seçilirse backend Google'a
  alan profillerini/isteğe bağlı örnek değerleri gönderir; otomatik fallback yoktur.
- Tam JSON Schema standardı ve çalışma zamanı tip dönüşümleri doğrulanmaz.
  Yeni AI/append akışı JSON/XML/IDOC alan yollarını doğrular; eski tek tanımlı
  JSON manuel sözleşmesinde yol kontrolü sınırlıdır. CSV/Excel yalnızca örnek dosya olarak saklanır.
- Tekil şema silme endpoint'i yoktur.
- Credential map'lerinin auth türüne göre zorunlu anahtar sözleşmesi yoktur.
- Secret görüntülemede yeniden parola/MFA ve kalıcı DB audit endpoint'i yoktur.
- Session ve auth hız limiti instance belleğindedir; dağıtık session/limit kurulmadı.
- Katalog/entegrasyon listelerinde sayfalama yoktur; büyük veri performansı ayrıca ölçülmelidir.
- `ddl-auto=update` kullanılır; Flyway/Liquibase ile sürümlü migration henüz yoktur.
- API hataları ortak İngilizce hata zarfına dönüştürülür; alan hataları kodlu
  `violations` taşır. Actuator sağlık yanıtları kendi biçimindedir.
  [Hata sözleşmesi](error-codes.md) ve kod kataloğu frontend yerelleştirmesinin temelidir.
- Frontend bu repoda bulunmaz; hesap sayfaları ve üretim reverse proxy ayrı yapılandırılır.
- Compose localhost/HTTP geliştirme içindir. Üretimde HTTPS, Secure cookie,
  kısıtlı DB kullanıcısı, izleme/log yönetimi ve aynı anahtarla yedekten dönüş
  ayrıca hazırlanmalıdır. Container'ın `unhealthy` olması otomatik restart değildir.
- Bağımlılık/secret taraması, genel exception loglarının gözden geçirilmesi ve
  gerçek PostgreSQL üzerinde eşzamanlı yük/kilit testleri üretim kabulünden ayrıdır.
- Gemini'ye gönderilebilecek örnek veriler ve kullanım/kota sınırları belirlenmelidir;
  sentetik model testleri kurumsal eşleme doğruluğunu garanti etmez.

## Test kapsamı ve doğrulama yöntemi

| Grup | Doğruladığı davranış |
|---|---|
| IntegrationServiceImpl / MappingServiceImpl testleri | Güncelleme, şema sahipliği, liste kaydetme |
| PayloadSchemas / PayloadWeb testleri | XML/IDOC alan ağacı, CSV/Excel dosyaları, boyut/format/yetki ve eşleme engelleri |
| PayloadPostgresTests (opt-in) | Gerçek PostgreSQL JSONB/TEXT/BYTEA kayıt/okuma ve upsert; [çalıştırma](payload-content.md#veritabanı-geçişi-ve-test) |
| SystemCatalogPostgresTests (opt-in) | Ortak kimlikler, üç rolde tekil kullanım sayısı, sistem adları, ortam türleri, pasif seçim ve eski tabloların yokluğu |
| MappingFlowPostgresTests (opt-in) | İki yönde akış değişiminde metin/dosya kaydıyla temizlik, başka entegrasyonu koruma ve temizlik sonrası hatada rollback |
| IntegrationCompletion / IntegrationSystemSelection | Şema matrisi, inceleme, taslağa dönüş, pasif seçim |
| SystemManagement | Güncelleme, kullanım sayısı, aktiflik, silme |
| AiMappingService / AiMappingWorker / AiMappingWeb | Backend sağlayıcı seçimi, kuyruk, korunmuş bağlantılar, onay/idempotency, rol/CSRF, hata ve sağlayıcı değişimi |
| MappingSuggestionProtocol / OllamaProvider / GeminiProvider / AiProviderHttpClient | Dokuz format çifti, kısa alan kimlikleri, ortak tip/dizi kuralları, HTTP hata/timeout/yanıt sınırı; dış HTTP taklit edilir |
| AiMappingPostgresTests (opt-in) | Kalıcı iş/JSONB sonucu, onay kaydı, dokuz format çifti, eski bağlam reddi, süre dolumu ve AI/mapping kayıtlarının cascade temizliği |
| Security / CredentialSecurity / CatalogManagementWeb | Gerçek Spring Security filtreleri, roller, CSRF, secret yetkileri |
| CredentialCrypto / CredentialMigration | Şifreleme, yanlış anahtar, bozulma, mock JDBC ile dönüşüm |
| AccountLifecycle / AccountLifecycleWeb / AccountMail | Davet, reset, doğrulama, oturum iptali, outbox |
| AdminBootstrap / AuthRateLimitFilter | İlk kullanıcı ve hız sınırı |
| RequestValidation / HealthEndpoint | DTO doğrulamaları; Actuator gizliliği ve simüle edilmiş sağlık durumları |

`PayloadPostgresTests`, gerçek PostgreSQL üzerinde beş formatın JSONB/TEXT/BYTEA
kayıt/okuma ve upsert davranışını doğrular; test transaction'ları rollback olur.
`MappingFlowPostgresTests`, ayrı transaction'larda commit ve temizlik sonrası
hata/rollback davranışını doğrular; kendi test kayıtlarını temizler.
Bu sınıflar, `SystemCatalogPostgresTests` ve `AiMappingPostgresTests`,
`catalog.payload.postgres-test=true` ile etkinleşir.

Hesap/katalog kilitlerinin eşzamanlılığı, bütün alt veri türlerinin birlikte
cascade silinmesi, eski sürümlerden şema geçişi ve gerçek SMTP teslimi bu testlerin
kapsamında değildir. AI/mapping alt kayıtlarının entegrasyon silindiğinde
temizlenmesi ayrı DB testinde doğrulanır.
Credential dönüşümünün JDBC çağrıları mock ile test edilir. HTTP güvenlik testleri
gerçek filtreleri kullanır; `RequestValidationTests` yalnızca DTO/controller
doğrulamaları için güvenlik filtrelerini kapatır.
`IntegrationCatalogApplicationTests` tam uygulama context'ini açar ve çalışan
DB/config gerektirir. Normal test komutu [başlangıç belgesinde](../README.md),
opt-in DB testi [payload belgesindedir](payload-content.md#veritabanı-geçişi-ve-test).
Canlı Ollama/Gemini denemeleri otomatik testlerden ayrıdır;
[AI doğrulama notları](ai-mapping.md#doğrulama) kullanılan örnekleri ve sınırlarını açıklar.


## Erişim ve dosya işleme sınırları

Katalog erişimi rol bazında bütün uygulama için tanımlıdır; entegrasyon başına
sahiplik, ekip/tenant izolasyonu veya ayrı paylaşım yetkisi yoktur. VIEWER/EDITOR
rolleri kendilerine tanınan işlemleri bütün katalog kayıtlarında yapabilir.
Bu model, ekipler arasında gizli entegrasyon saklamak için tek başına yeterli değildir.

CSV/Excel örnekleri ve XML/IDOC ham içerikleri credential şifrelemesine dahil
değildir. Dosya ayrıştırma HTTP isteğinin JVM sürecinde, tamamlamada da yeniden
çalışır; ayrıca bir CPU/deadline veya ayrı parser süreci bulunmaz. Sıkıştırılmış
Excel'in yükleme boyutu, açıldığındaki bellek tüketimine üst sınır sağlamaz.
Dosya işleme yükü, örneklerde veri maskeleme ve saklama süresi üretim öncesi ele
alınmalıdır. Kütüphanenin [dosya işleme güvenliği rehberi](https://poi.apache.org/security.html)
bu sınırları ayrıca açıklar.
