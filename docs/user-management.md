# Kullanıcı yaşam döngüsü

Açık kayıt yerine ADMIN daveti kullanılır. Yeni hesap parolası ADMIN tarafından
belirlenmez. Davet kabulü e-posta sahipliğini doğrular ve kullanıcının parolasını oluşturur.
Parolalar 12–128 karakter olmalı ve yalnızca boşluk içermemelidir. Giriş kullanıcı adıyla yapılır.

## Endpoint'ler

POST/PUT işlemleri, anonim kabul edilenlerde bile, önce `/api/v1/auth/csrf` üzerinden
alınan CSRF token'ını ve aynı oturum çerezini gerektirir.

| Endpoint | Yetki | İşlev |
|---|---|---|
| `POST /api/v1/users` | ADMIN | `{username,email,role}` ile davet, 201 |
| `GET /api/v1/users?page=0&size=20` | ADMIN | En fazla 100 kayıtlık sayfalar |
| `POST /api/v1/users/{id}/invitations/resend` | ADMIN | Yeni davet, 202 |
| `PUT /api/v1/users/{id}/active` | ADMIN | `{active:false}` ile pasifleştirme |
| `PUT /api/v1/users/{id}/role` | ADMIN | `{role:"EDITOR"}` ile rol değiştirme |
| `PUT /api/v1/users/{id}/permissions` | ADMIN | Credential izinlerini güncelleme |
| `POST /api/v1/users/{id}/sessions/revoke` | ADMIN | Tüm oturumları iptal, 204 |
| `POST /api/v1/users/{id}/password-reset` | ADMIN | Zorunlu parola sıfırlama, 202 |
| `GET /api/v1/users/mail-status` | ADMIN | Kuyruktaki ve başarısız posta sayıları |
| `GET /api/v1/auth/me` | Oturum | Profil, roller ve credential izinleri |
| `POST /api/v1/auth/invitations/accept` | Token | `{token,newPassword}`, 204 |
| `POST /api/v1/auth/password-reset/request` | Herkes | `{email}`, her adres için aynı 202 yanıtı |
| `POST /api/v1/auth/password-reset/confirm` | Token | `{token,newPassword}`, 204 |
| `POST /api/v1/auth/password/change` | Oturum | `{currentPassword,newPassword}`, 204 |
| `POST /api/v1/auth/email/change` | Oturum | `{currentPassword,email}`, 202 |
| `POST /api/v1/auth/email/confirm` | Token | `{token}`, 204 |

`users` yanıtlarında `emailVerified`, `active` ve `passwordResetRequired` hesap
durumunu gösterir. Parola, parola hash'i veya bağlantı token'ı yanıtlarla gönderilmez.

## Davet ve doğrulama

1. ADMIN kullanıcı adı, e-posta ve rol gönderir. E-posta küçük harfe çevrilir ve
   benzersizdir. Hesap pasif ve doğrulanmamış olarak oluşturulur.
2. Posta kuyruğuna doğrulama bağlantısı yazılır. Davet 24 saat geçerlidir.
3. Kullanıcı frontend'de yeni parolasını belirler; frontend token ve parolayı
   `/invitations/accept` endpoint'ine gönderir.
4. Hesap aktif/doğrulanmış olur; token tüketilir. Otomatik giriş yapılmaz; kullanıcı
   normal giriş akışını kullanır. Süresi dolarsa ADMIN yeniden davet gönderebilir.

Yeni davet önceki bağlantıyı geçersiz kılar. Aynı hesaba davet/sıfırlama/e-posta
değişikliği bağlantısı aynı amaçla en fazla 60 saniyede bir üretilebilir.

## Parola değiştirme ve sıfırlama

Giriş yapmış kullanıcı mevcut parolasını doğrular. Yeni parola eskisiyle aynı olamaz.
Parola değişince bilgilendirme postası kuyruğa alınır; mevcut ve diğer oturumlar iptal edilir.

"Parolamı unuttum" endpoint'i hesabın varlığına göre farklı yanıt vermez. Her e-posta
için aynı şifreli iş kuyruğa alınır; kullanıcı sorgusu arka planda yapılır. Yalnızca
aktif ve doğrulanmış hesaplara 30 dakikalık bağlantı üretilir. Pasif hesaplar bu yolla
yeniden aktifleştirilemez. Yeni parola belirlendikten sonra bütün oturumlar iptal
edilir, bilgilendirme postası oluşturulur ve yeniden giriş gerekir.

ADMIN'in başlattığı zorunlu sıfırlamada eski oturumlar hemen iptal edilir, eski
parolayla yeni giriş de kapanır. Sıfırlama tamamlanınca giriş yeniden açılır.
Son aktif ADMIN bu yolla kilitlenemez; kendi parola değiştirme veya unuttum akışını kullanır.

## E-posta değiştirme

Mevcut parola istenir. Eski adres değiştirilmeden yeni adrese 30 dakikalık bağlantı
gönderilir; eski adrese talep bildirimi oluşturulur. Yeni adres doğrulanınca adres
değişir, oturumlar iptal edilir ve iki adrese de bildirim oluşturulur. Adresin başka
bir hesap tarafından alınmış olup olmadığı doğrulama sırasında tekrar kontrol edilir.

Başlangıç yönetici hesabı ve daha önce oluşturulmuş e-postasız hesaplar mevcut
girişlerini korur; `/auth/email/change` akışıyla e-posta bağlamalıdır. E-posta
bağlamadan e-postayla parola kurtarma kullanılamaz. Bu geçiş istisnası yeni davet
hesapları için geçerli değildir. Yeni hesaplar doğrulanmadan giriş yapamaz.

## Yönetim ve oturumlar

Kullanıcılar fiziksel olarak silinmez. Son giriş yapabilen ADMIN'in pasifleştirilmesi
veya rolünün düşürülmesi 409 döner. ADMIN hesapları veritabanında sıralı olarak
kilitlenerek eşzamanlı iki isteğin bütün yöneticileri devre dışı bırakması önlenir.
Hesap ve token değişiklikleri transaction içinde ve token tüketimi kilit altında yapılır.

Parola/e-posta/rol/izin değişiklikleri, pasifleştirme ve oturum iptali işlemleri
hesabın `sessionVersion` değerini artırır. Gerçek giriş oturumları her istekte
veritabanındaki güncel değerle karşılaştırılır. Eski sürüm reddedilir ve oturum
geçersiz kılınır. Hesap doğrulanırken veritabanı kullanılamazsa korunan istek 503
döner. Health endpoint'leri bu kullanıcı kontrolünden bağımsızdır.

Kullanıcı işlemleri `account_audit` tablosuna kullanıcı/amaç/zaman bilgisiyle yazılır;
parola veya ham token kaydedilmez. Audit erişim/retention politikası ve dağıtık
oturum deposu ayrıca yapılandırılmalıdır.

## Token, posta kuyruğu ve SMTP

Token 32 byte güvenli rastgele verinin padding'siz URL-safe Base64 gösterimidir;
43 karakterdir (`[A-Za-z0-9_-]{43}`). Token tablosunda yalnızca SHA-256
hash'i bulunur. Amaç, hesap, e-posta, son kullanım zamanı ve oturum sürümüyle
doğrulanır. Süresi bitmiş, kullanılmış veya eski sürüme ait bağlantı 400 döner.

E-postalar hesap işlemiyle aynı transaction'da `mail_outbox` tablosuna yazılır.
Kuyruktaki alıcı, metin ve ham bağlantı AES-GCM ile şifrelenir; anahtar mevcut
`CATALOG_CREDENTIALS_KEY` değeridir. Burada ham token'ın şifreli kopyası vardır;
token doğrulama tablosunda ise yalnızca hash bulunur. Kuyruk satırının UUID'si
şifrelemeye bağlanır. Anahtarın korunması bu postalar için de gereklidir.

Worker 15 saniyede bir en fazla 10 iş seçer; SMTP gönderiminde beş saniyelik
bağlantı/okuma/yazma timeout'ları vardır. Hatalar artan gecikmeyle en fazla sekiz
kez denenir. Başarılı veya kalıcı başarısız teslimde şifreli mesaj içeriği temizlenir;
durum ve deneme sayısı kalır. Son denemesi başarısız bir davet için ADMIN yeni davet
üretmelidir. Bir mesaj SMTP'ye teslim edildikten hemen sonra süreç/commit başarısız
olursa tekrar teslim mümkündür; alıcı tek kullanımlık aynı bağlantıyı görebilir.
SMTP kabulü mesajın alıcının gelen kutusuna ulaştığını garanti etmez.

Posta kesintisi katalog readiness'ını bozmaz. ADMIN `/users/mail-status` ile pending
ve failed sayılarını izleyebilir. Loglar yalnızca posta işinin ID ve deneme sayısını
içerir. SMTP debug ve HTTP gövde/trace loglaması açılmamalıdır; bunlar parola veya
token içerebilir.

Giriş, davet kabulü, parola ve e-posta POST işlemleri IP başına 60 saniyede 20
istekle sınırlıdır (429).
Sayaç uygulama belleğindedir; yeniden başlatmada sıfırlanır. İstemcinin gönderdiği
X-Forwarded-For'a güvenilmez. Çok instance veya reverse proxy dağıtımında gateway
üzerinde paylaşımlı limit ve güvenilir istemci IP ayarı ayrıca yapılmalıdır.

### Yerel deneme

```sh
docker compose --profile mail up --build -d
```

Mailpit arayüzü `http://localhost:8025` adresindedir. Mesajlar gerçek kişilere
gönderilmez. Mailpit'e erişebilenler test bağlantılarını da görebilir; servis
yalnızca localhost portlarına bağlıdır. IDE'den çalıştırırken SMTP_HOST=localhost,
SMTP_PORT=1025 kullanın; Docker'daki app için varsayılan SMTP_HOST=mailpit'tir.

Gerçek sunucuda SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH,
SMTP_STARTTLS ve MAIL_FROM ayarlarını tanımlayın. STARTTLS sunucusunda örneğin
SMTP_AUTH=true, SMTP_STARTTLS=true ve uygun port kullanılır. Kimlik bilgilerini Git'e
koymayın. SMTP kabulünü ve gerçek teslimi kendi ortamınızda doğrulayın.

ACCOUNT_ACTION_URL frontend doğrulama sayfasının tam adresidir.
Localhost dışında HTTPS gereklidir; Host başlığından bağlantı üretilmez. Token URL
fragment'ında bulunur: `#purpose=INVITATION&token=...`. Frontend token'ı okuyup URL'den
temizlemeli, saklamamalı ve uygun POST endpoint'ine göndermelidir. Doğrulama/sıfırlama
frontend sayfaları bu repoda bulunmaz; backend akışları HTTP istemcisiyle denenebilir.
Fragment işleme ve endpoint eşlemesi [frontend rehberinde](frontend-integration.md),
ortam değişkenleri [yapılandırma belgesinde](configuration.md) açıklanır.
Mailpit mesajları container yeniden oluşturulduğunda korunacak bir volume'a yazılmaz;
yerel posta kutusunu kalıcı audit veya yedek yerine kullanmayın.

## Doğrulama sınırı

Hesap servisleri ve HTTP güvenlik filtreleri otomatik test edilir; SMTP testleri
sahte sender kullanır. Gerçek PostgreSQL eşzamanlılığı ve SMTP teslimi için kapsam
[mimari belgesindedir](architecture.md#test-kapsamı-ve-doğrulama-yöntemi).

Referanslar: [Spring Boot e-posta](https://docs.spring.io/spring-boot/3.5/reference/io/email.html),
[OWASP parola kurtarma](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html),
[Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
