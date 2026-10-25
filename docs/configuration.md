# Yapılandırma ve işletim

Ana kaynaklar `application.properties`, `.env.example`, `docker-compose.yml` ve
`Dockerfile`'dır. `.env` dosyasını Compose okur; IDE veya `./mvnw` kendiliğinden
okumaz. IDE Run Configuration üzerinden ortam değişkenlerini ayrıca tanımlayın.

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
`SERVER_PORT` kullanılır. Kaynak dosyada yerel datasource varsayılanı
`jdbc:postgresql://localhost:5432/integration_catalog`, `catalog_user` ve
`catalog_password`'dür; bu geliştirme değerlerini gerçek DB ile eşleştirin.

Spring ayarları environment üzerinden değiştirilebilir. Örneğin
`SPRING_JPA_SHOW_SQL=false` SQL logunu kapatır. Mevcut `ddl-auto=update` geliştirme
şema yönetimidir; sürümlü üretim migration'ı değildir. Üretime geçişte şema
değişiklikleri ayrıca hazırlanıp gerçek PostgreSQL üzerinde doğrulanmalıdır.
XML/IDOC/CSV/Excel desteğini mevcut DB'ye açmadan önce
[payload migration](migrations/20261025-payload-content.sql) uygulanmalıdır;
özellikle EXCEL enum CHECK kısıtları yalnızca Hibernate update'e bırakılmamalıdır.

## IDE ile çalıştırma

1. JDK 21 seçin. [Docker kurulumundaki](docker.md) `.env` değerlerini tamamlayın.
2. Yalnızca DB ve yerel postayı başlatın:

```sh
docker compose --profile mail up -d postgres mailpit
```

Compose dosyası app servisini başlatmasanız bile zorunlu değişkenleri ayrıştırır;
bu nedenle `.env` içinde DB parolası ve anahtar bulunmalıdır.

3. IDE ortamına datasource değerlerini, kalıcı credential anahtarını, ilk açılış
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
| Auth POST IP limiti | 60 saniyede 20 istek; instance belleğinde |
| Aynı hesap/amaç için token üretimi | En az 60 saniye arayla |
| Davet geçerliliği | 24 saat |
| Reset / e-posta doğrulama | 30 dakika |
| Docker app health | 15 saniye aralık, 60 saniye başlangıç toleransı, 3 başarısız kontrol |
| Compose stop grace | 30 saniye |

Hikari sınırları HTTP/SQL sorgusu tamamlanma süresi değildir; Compose'da verilen
değerler IDE'ye otomatik aktarılmaz. Docker health kontrolünde curl en fazla
8 saniye bekler, Docker kontrol timeout'u 10 saniyedir.

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
| Anahtar / decrypt hatası | Aynı DB'ye ait kalıcı anahtarı kullan; yanlış anahtarla migration deneme |
| Compose DB auth hatası | Volume ilk oluşturulduğundaki parola ile environment eşleşiyor mu? |
| Login/yazma 403 | Aynı oturum çerezi ve güncel CSRF başlığı; HTTP'de Secure cookie yanlışlıkla açık mı? |
| Giriş sonrası tekrar 401 | Davet doğrulaması, aktiflik, reset zorunluluğu veya oturum iptali |
| İstek 409 | Yanıttaki message/details; kullanılan katalog kaydı, pasif seçim veya eksik tamamlama |
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
