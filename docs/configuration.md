# Yapılandırma ve işletim

Ana kaynaklar `application.properties`, `.env.example`, `docker-compose.yml` ve
`Dockerfile`'dır. `.env` dosyasını Compose okur; IDE veya `./mvnw` kendiliğinden
okumaz. IDE Run Configuration üzerinden ortam değişkenlerini ayrıca tanımlayın.

## Docker ile çalıştırma

Compose, PostgreSQL ve backend'i aynı ağda çalıştırır; backend DB'ye
`postgres:5432` üzerinden bağlanır. İlk kurulum değişkenleri ve hızlı başlangıç
[proje README'sindedir](../README.md#başlangıç). İlk image build'i bağımlılıkları
ve base image'ları indirir. Java 21 ile çok aşamalı build yapılır; çalışma
image'ında JRE/JAR bulunur ve uygulama root olmayan kullanıcıyla çalışır.
Image build'i testleri çalıştırmaz; test/CI ayrı adımdır.

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

Davet/reset postalarını yerelde görmek için `--profile mail` ekleyin; Mailpit
`http://localhost:8025` adresindedir ve gerçek alıcılara göndermez. Gerçek SMTP
kurulumu [kullanıcı yönetimindedir](user-management.md#token-posta-kuyruğu-ve-smtp).
Ollama, yalnızca `ai` profiliyle başlar; online model indirme ve internete kapalı
çalışma ağı ayrıdır. Gemini için Ollama gerekmez. İki sağlayıcının kurulumu
[AI rehberindedir](ai-mapping.md).

Varsayılan host portları backend için `127.0.0.1:8080`, DB için
`127.0.0.1:5432`'dir. `.env` içindeki `APP_PORT` ve `POSTGRES_PORT` bunları değiştirir.
IDE'deki backend ile container aynı host portunu kullanamaz.

### Kod ve env değişikliklerini uygulama

Yalnızca `.env` değiştiyse:

```sh
docker compose up -d --wait app
```

Java kaynağı veya Dockerfile değiştiyse:

```sh
docker compose up -d --build --wait --wait-timeout 180 app
```

Compose değişen ayarlarla container'ı yeniden oluşturur. `docker compose restart app`
mevcut container'ı yeniden başlatır; yeni env değerlerini almaz veya kodu derlemez.
Backend yeniden başlayınca bellekteki oturumlar kapanır; tekrar giriş gerekir.
Her istek `X-Request-ID` başlığı alır; hata gövdesi aynı `requestId` değerini taşır.
Ortak hata logları bu kimlikle aranabilir; AI worker olayları `jobId` ile izlenir.
DB zaten hazırsa komutlara `--no-deps` eklenerek yalnızca app güncellenebilir.

`.env` Git'e/build context'ine alınmaz; secret'lar image'a yazılmaz, çalışma anında
backend'e aktarılır. Docker yönetim yetkisi olan kişiler environment'a erişebilir.
Mevcut DB parolası `.env` değiştirilerek değiştirilmez; credential anahtarını
rastgele yenilemek eski veriyi okunamaz yapar.

### Volume ve eski veritabanı

`postgres_data` veritabanını, `ollama_data` indirilen modelleri saklar. Aynı verilere
ulaşmak için Compose proje adı/dizini aynı kalmalıdır. `docker compose down`
volume'leri korur; `down -v` volume'leri siler ve normal durdurma yöntemi değildir.

Bu sürüm eski ayrı source/destination/middleware kataloglarını ortak `systems`
modeline taşıyan bir migration içermez; boş DB ile başlanabilir. Kalıcı verili eski
DB için ayrıca geçiş hazırlanmalıdır. Yeni DB'de entity'lerden şema oluşturulur;
mevcut DB değişikliklerinin sınırları aşağıdaki yapılandırma bölümündedir.

## Ortam değişkenleri

| Değişken | Varsayılan / gereklilik | Etki |
|---|---|---|
| `POSTGRES_USER` | Compose: `catalog_user` | PostgreSQL ilk kullanıcı; app datasource kullanıcısı |
| `POSTGRES_DB` | Compose: `integration_catalog` | Veritabanı adı |
| `POSTGRES_PASSWORD` | Compose: zorunlu | İlk DB parolası; mevcut volume parolasını değiştirmez |
| `POSTGRES_PORT` | Compose: `5432` | Host portu; container içinde daima 5432 |
| `APP_PORT` | Compose: `8080` | Host portu; container içinde daima 8080 |
| `CATALOG_ADMIN_USERNAME` | Boş kullanıcı tablosunda zorunlu | İlk ADMIN adı |
| `CATALOG_ADMIN_PASSWORD` | Boş kullanıcı tablosunda zorunlu | 12–128 karakter; ilk ADMIN parolası |
| `CATALOG_CREDENTIALS_KEY` | Her açılışta zorunlu | Base64 biçiminde 32 byte AES anahtarı |
| `CATALOG_CREDENTIALS_MIGRATE` | `false` | Eski açık credential'ları bir defa şifreleme |
| `SESSION_COOKIE_SECURE` | `false` | HTTPS dağıtımında `true` |
| `SMTP_HOST` | IDE: `localhost`; Compose: `mailpit` | SMTP sunucusu |
| `SMTP_PORT` | `1025` | SMTP portu |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Boş | Gerçek SMTP sağlayıcısının bilgileri |
| `SMTP_AUTH` | `false` | SMTP kimlik doğrulama |
| `SMTP_STARTTLS` | `false` | true ise STARTTLS etkin ve zorunlu |
| `MAIL_FROM` | `no-reply@localhost` | Gönderici adresi |
| `ACCOUNT_ACTION_URL` | `http://localhost:5173/account/action` | Frontend hesap bağlantısı; localhost dışında HTTPS |

`POSTGRES_*` ve `APP_PORT` Compose değişkenleridir; IDE'deki Spring ayarlarını
doğrudan değiştirmez. IDE için `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` ve gerekirse
`SERVER_PORT` kullanılır. Kaynak dosyada yerel datasource varsayılanları
`jdbc:postgresql://localhost:5432/integration_catalog` ve `catalog_user`'dır.
Varsayılan veritabanı parolası yoktur; `SPRING_DATASOURCE_PASSWORD` dışarıdan
verilmelidir. Compose bunu `.env` içindeki `POSTGRES_PASSWORD` üzerinden sağlar;
IDE'de `SPRING_DATASOURCE_PASSWORD` ayrıca tanımlanır.

Backend AI varsayılanlarının tek kaynağı `application.properties` dosyasıdır.
Compose, `.env` veya shell ortamında tanımlı AI değişkenlerini aktarır; tanımlı
olmayanlar container ortamına eklenmez ve Spring varsayılanı kullanılır.
Varsayılana dönmek için isteğe bağlı AI değişkeninin satırını kaldırın; boş değer
atamayın (boş `GEMINI_API_KEY`, anahtar kullanılmadığı anlamına gelir).
`AiProperties` yalnızca bu değerleri bağlar ve doğrular. Ollama image/RAM/CPU
gibi container ayarlarının varsayılanları Compose'da kalır.

Spring ayarları environment üzerinden değiştirilebilir. Örneğin
`SPRING_JPA_SHOW_SQL=false` SQL logunu kapatır. Mevcut `ddl-auto=update` geliştirme
şema yönetimidir; sürümlü üretim migration'ı değildir. Üretime geçişte şema
değişiklikleri ayrıca hazırlanıp gerçek PostgreSQL üzerinde doğrulanmalıdır.
Yeni/boş DB'de tablolar ve kolonlar güncel entity'lerden otomatik oluşturulur.
Mevcut DB'deki format CHECK kısıtları ayrıca kontrol edilmelidir; özellikle
EXCEL gibi yeni enum değerlerinin eklenmesi yalnızca Hibernate update'e
bırakılmamalıdır.
`CATALOG_CREDENTIALS_MIGRATE` yalnızca eski credential içeriğini şifreler;
tablo/kolon değişikliklerini yöneten bir şema migration sistemi değildir.

## IDE ile çalıştırma

1. JDK 21 seçin. [başlangıç rehberindeki](../README.md#başlangıç) `.env` değerlerini tamamlayın.
2. Yalnızca DB ve yerel postayı başlatın:

```sh
docker compose --profile mail up -d postgres mailpit
```

Compose dosyası app servisini başlatmasanız bile zorunlu değişkenleri ayrıştırır;
bu nedenle `.env` içinde DB parolası ve anahtar bulunmalıdır.

3. IDE ortamına datasource değerlerini (`SPRING_DATASOURCE_URL`,
   `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), kalıcı credential anahtarını, ilk açılış
   admin bilgilerini ve `SMTP_HOST=localhost`, `SMTP_PORT=1025` ayarlarını girin.
   DB URL'sinde host `localhost`, port `.env` içindeki POSTGRES_PORT olmalıdır.
4. `IntegrationCatalogApplication` sınıfını veya aynı environment ile aşağıdaki komutu çalıştırın:

```sh
./mvnw spring-boot:run
```

IDE ve Compose app aynı host portunda birlikte çalışamaz. İlk admin e-postasızdır;
girişten sonra e-posta bağlama akışıyla doğrulanmış adres ekleyin. Unutulan parola
kurtarma için doğrulanmış e-posta gerekir. Mevcut hesapları bootstrap ayarıyla
yeniden yazamazsınız.

## Timeout ve çalışma aralıkları

| Ayar | Mevcut değer / anlam |
|---|---|
| Session idle timeout | 30 dakika |
| Config `timeoutMs`, `retryCount`, `retryIntervalMs` | Kullanıcı metaverisi; dış istek/yeniden deneme çalıştırmaz |
| Genel HTTP işleme süresi | Uygulamaya özel ortak bir işlem deadline'ı tanımlanmadı |
| Payload metin içeriği | En fazla 1 MiB; JSON/XML/IDOC/CSV |
| Excel dosyası | En fazla 5 MiB; multipart toplam istek sınırı 6 MiB |
| XML ayrıştırma | 64 eleman derinliği, 10000 alan, alan yolu başına 500 karakter |
| Hikari connection timeout | Compose app: 5000 ms; havuzdan bağlantı bekleme süresi |
| Hikari validation timeout | Compose app: 2000 ms; bağlantı doğrulama sınırı |
| SMTP bağlantı / okuma / yazma | Her biri 5000 ms |
| Posta worker | 15 saniye aralık, en fazla 10 iş; en fazla 8 deneme |
| Auth POST IP limiti | Giriş/davet kabulü/parola/e-posta işlemlerinde 60 saniyede 20 istek; instance belleğinde |
| Aynı hesap/amaç için token üretimi | En az 60 saniye arayla |
| Davet geçerliliği | 24 saat |
| Reset / e-posta doğrulama | 30 dakika |
| Docker app health | 15 saniye aralık, 60 saniye başlangıç toleransı, 3 başarısız kontrol |
| Compose stop grace | 30 saniye |
| AI model isteği | Varsayılan 300 saniye; kuyrukta bekleme hariç, üst sınır 600 |
| AI model çıktısı | Varsayılan 2048 token; kesilen çıktı reddedilir |

AI işleri asenkrondur; frontend, `pollAfterMs` aralığında durum sorgular.
Modelin bekleme bütçesi HTTP isteğinin açık tutulacağı süre değildir.
Yerel modelin performansı ve Ollama/Gemini kurulumu için [AI eşleme](ai-mapping.md).
`AI_PROVIDER`/`AI_MODEL` backend ayarıdır; Gemini seçilirse `GEMINI_API_KEY` gerekir.
Compose, bu anahtarı yalnızca app servisine aktarır; Ollama'ya aktarmaz.

Hikari sınırları HTTP/SQL sorgusu tamamlanma süresi değildir; Compose'da verilen
değerler IDE'ye otomatik aktarılmaz. Docker health kontrolünde curl en fazla
8 saniye bekler, Docker kontrol timeout'u 10 saniyedir.

## Sağlık kontrolleri

Spring Boot Actuator yalnızca health endpoint'ini dışarı açar:

| Endpoint | Kontrol | Kullanım |
|---|---|---|
| `GET /actuator/health` | Genel sağlık | İzleme |
| `GET /actuator/health/liveness` | Uygulama yaşam durumu | Yeniden başlatma kararı |
| `GET /actuator/health/readiness` | Hazırlık durumu ve PostgreSQL bağlantısı | Trafik yönlendirme |

Üçü de giriş gerektirmeden çağrılır; bileşenler, bağlantı adresleri ve hata ayrıntıları
ADMIN dahil hiçbir kullanıcıya bu yanıtlarda gösterilmez. Diğer Actuator yolları kapalıdır.
Sağlıklı yanıtta `status=UP`, HTTP 200; DOWN veya OUT_OF_SERVICE durumunda HTTP 503 döner.

PostgreSQL bağlantısı kesilirse readiness başarısız olur, liveness bundan etkilenmez.
Veritabanı sorunu nedeniyle uygulamayı sürekli yeniden başlatmak yerine trafik durdurulabilir.
Readiness Spring Boot'un başlatma/kapatma hazırlık durumunu da dikkate alır. Şifreli
kayıt dönüşümü gibi başlangıç işlemleri bitmeden uygulama hazır kabul edilmez.

Yerel kontrol:

```sh
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

Bu endpoint'ler veritabanındaki her kaydın doğruluğunu veya dış entegrasyonların
çalıştığını garanti etmez. Docker Compose, uygulama container'ının readiness
endpoint'ini Dockerfile HEALTHCHECK ile takip eder. Başlatma ve sınırlar için
[Docker kurulumu](#docker-ile-çalıştırma) belgesine bakın.
SMTP health kontrolü kapalıdır; posta kuyruğu ADMIN'in
`GET /api/v1/users/mail-status` endpoint'inden ayrıca izlenir.
Health kontrolü yetkilendirme/CSRF akışının uçtan uca çalıştığını sınamaz.

`HealthEndpointTests`, gerçek Actuator ve güvenlik filtreleriyle endpoint erişimini,
gizliliği, availability durumlarını ve simüle edilen veritabanı kesintisini doğrular.
Gerçek PostgreSQL bağlantı kesintisi bu testte kullanılmaz.

AI sağlayıcısı da genel readiness'e dahil değildir; AI arızası manuel eşlemeyi
kapatmaz. `GET /api/v1/integrations/{id}/mappings/ai/capabilities` ayrı sorgulanır.

PostgreSQL `pg_isready` başarılı olunca app başlar. Docker HEALTHCHECK her 15
saniyede readiness'i çağırır; 60 saniye başlangıç toleransı ve üç başarısız kontrol
sonunda `unhealthy` olur. Süreç kapanırsa `restart: unless-stopped` yeniden başlatır;
elle durdurulmuş servis veya yalnızca `unhealthy` durumundaki çalışan süreç
kendiliğinden yeniden başlatılmaz. Compose sağlık durumuna göre trafiği kesmez.

## Yedekleme ve anahtar yönetimi

DB yedeğiyle birlikte anahtarı ayrı ve güvenli bir yerde koruyun. Anahtar
kaybolursa credential'lar ve şifreli posta içerikleri çözülemez. Yeni rastgele
anahtar üretmek eski veriyi geri getirmez. Otomatik anahtar rotasyonu yoktur.
Yedekten dönüşü bir deneme ortamında aynı anahtarla sınayın; uygulama sürümü ve
şema uyumluluğunu kontrol edin. `docker compose down` volume'u korur;
`down -v` veriyi siler ve normal yeniden başlatma yöntemi değildir.

Eski açık credential dönüşümü için uygulamanın diğer instance'larını durdurun,
yedek alın, [şifreleme dönüşümünü](authentication.md) uygulayın. Başarılı açılıştan
sonra migrate bayrağını kapatın. Eski yedeklerdeki açık değerler kendiliğinden silinmez.

## Sorun giderme

| Belirti | Kontrol |
|---|---|
| İlk açılış duruyor | DB bağlantısı, 32 byte Base64 key, boş kullanıcı tablosunda bootstrap bilgileri |
| Anahtar / decrypt hatası | Aynı DB'ye ait kalıcı anahtarı kullan; yeni anahtar üretmek eski veriyi çözmez |
| Compose DB auth hatası | Volume ilk oluşturulduğundaki parola ile environment eşleşiyor mu? |
| Login/yazma 403 | Aynı oturum çerezi ve güncel CSRF başlığı; HTTP'de Secure cookie yanlışlıkla açık mı? |
| Giriş sonrası tekrar 401 | Davet doğrulaması, aktiflik, reset zorunluluğu veya oturum iptali |
| İstek 409 | `code`, `violations` ve güvenli parametreleri; hata loguyla eşleştirmek için `requestId` |
| Payload 400 / 409 / 413 | `code` alanı, format/dosya türü/boyutu, mevcut eşleme çatışması; [örnekler](payload-content.md#hatalar) |
| Posta gelmiyor | Mail profili / SMTP ayarı, ADMIN mail-status, app logları, token'ın süresi |
| Container unhealthy | Readiness ve DB bağlantısını incele; Compose unhealthy için otomatik restart yapmaz |
| Proxy arkasında sık 429 | IP limiti remote address kullanır; proxy istemcileri aynı IP olarak görülebilir |

```sh
docker compose ps
docker compose logs --tail=100 app
curl -i http://localhost:8080/actuator/health/readiness
```

Log paylaşmadan önce erişim bilgilerini temizleyin. HTTP gövdesi ve SMTP debug
loglaması parola/token sızdırabilir. Kullanıcı yönetimi audit'i DB'de, credential
görüntüleme audit'i uygulama logundadır; retention/merkezi log yönetimi ayrıca kurulmalıdır.
