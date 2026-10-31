# Giriş ve rol yönetimi

Backend yerel kullanıcı hesapları, oturum tabanlı giriş ve üç rol kullanır.
Kullanıcı giriş parolaları PBKDF2 ile hash'lenir; geri çözülemez.
Bu, dış sistem erişim bilgilerinin şifrelenmesinden ayrı bir işlemdir.

## İlk yönetici

İlk çalıştırmada boş `user_accounts` tablosu için şu ortam değişkenlerini tanımlayın:

- `CATALOG_ADMIN_USERNAME`: 1–100 karakter; harf, rakam, nokta, tire veya alt çizgi.
- `CATALOG_ADMIN_PASSWORD`: 12–128 karakter; yalnızca boşluklardan oluşmamalı.

Varsayılan kullanıcı/parola yoktur. Her başlangıçta kullanıcı tablosu kontrol edilir;
tablo boşsa bu bilgilerle bir ADMIN oluşturulur. Bilgiler eksik/geçersizse başlangıç
durur. Herhangi bir kullanıcı varsa yeni admin oluşturulmaz, mevcut parola
değiştirilmez. İlk kurulumdan sonra bootstrap değişkenleri kaldırılabilir; DB
sıfırlanırsa yeniden verilmelidir. Boş DB'de tablolar `ddl-auto=update` ile oluşur.

## Roller

| İşlem | VIEWER | EDITOR | ADMIN |
|---|---|---|---|
| Katalog okuma | Evet | Evet | Evet |
| Oluşturma, düzenleme, config/mapping kaydetme ve tamamlama | Hayır | Evet | Evet |
| Entegrasyon silme | Hayır | Hayır | Evet |
| Kullanılmayan sistem silme | Hayır | Hayır | Evet |
| Kullanıcı listeleme ve oluşturma | Hayır | Hayır | Evet |
| Swagger / OpenAPI | Hayır | Hayır | Evet |

Yeni endpoint'ler açıkça yetkilendirilene kadar kapalıdır.
Kullanıcılar ADMIN davetiyle eklenir. Rol, hesap durumu, oturum iptali ve
e-posta doğrulama akışları için [kullanıcı yönetimi](user-management.md) belgesine bakın.
Fiziksel kullanıcı silme ve açık kayıt endpoint'i yoktur.
Ortak sistem kataloğunun güncelleme, aktiflik ve silme kuralları için
[katalog yönetimi](systems-frontend.md) belgesine bakın.

## Frontend / Postman akışı

1. `GET /api/v1/auth/csrf` isteği yapın. Dönen `token` ve `headerName` değerlerini
   alın; yanıtın oturum çerezini sonraki isteklerde koruyun.
2. `POST /api/v1/auth/login` isteğini `application/x-www-form-urlencoded`
   biçiminde `username` ve `password` alanlarıyla gönderin. CSRF token'ını önceki
   yanıttaki başlık adıyla gönderin. Başarılı giriş 200, hatalı giriş 401 döner.
3. Girişten sonra tekrar CSRF token'ı alın. Oturum kimliği girişte yenilenir.
4. `GET /api/v1/auth/me` kullanıcı adı, e-posta, doğrulama/aktiflik durumu ve rolleri döndürür.
   `permissions` alanı güncel credential izinlerini içerir.
5. POST/PUT/PATCH/DELETE isteklerinde güncel CSRF token'ını ve oturum çerezini gönderin.
6. `POST /api/v1/auth/logout` CSRF token'ıyla çıkış yapar; 204 döner ve oturumu kapatır.
   Yeniden girişten önce yeni CSRF token'ı alın.

ADMIN, `POST /api/v1/users` ile aşağıdaki biçimde JSON göndererek kullanıcı davet eder:

```json
{"username":"reader","email":"reader@example.com","role":"VIEWER"}
```

ADMIN parola belirlemez. Kullanıcı e-postadaki bağlantıyla parolasını belirleyene
kadar hesap kullanılamaz. `GET /api/v1/users?page=0&size=20` kullanıcıları sayfalı
listeler (`items`, `page`, `size`, `totalElements`, `totalPages`); parola ve hash
yanıtta bulunmaz. Tekrarlanan kullanıcı adı/e-posta 409 döner. Kullanıcı adları
büyük/küçük harfe duyarlıdır; e-postalar küçük harfle saklanır.

Oturum çerezi HttpOnly ve SameSite=Lax'tır; hareketsizlik süresi 30 dakikadır.
Frontend aynı origin üzerinden çalışmalıdır; geliştirmede frontend dev proxy kullanın.
Backend'de farklı origin'lere izin veren CORS yapılandırması yoktur. HTTPS ortamında
`SESSION_COOKIE_SECURE=true` tanımlayın. Yerel HTTP geliştirme için varsayılan değer false'tur.
Oturumlar uygulama belleğindedir; yeniden başlatmada kaybolur.
Parola, e-posta, rol veya credential izni değiştiğinde ve hesap pasifleştirildiğinde
oturum sürümü artırılır. Eski oturumlar sonraki istekte veritabanı kontrolüyle reddedilir.

## Dış sistem erişim bilgileri

`authCredentials` katalog okuma yanıtlarında gönderilmez; `credentialsConfigured`
alanı bilgilerin mevcut olup olmadığını gösterir. Config kaydında `authCredentials`
gönderilmezse aynı ortam/hedef/auth türünün mevcut bilgileri korunur. Yeni map
gönderilirse eski map'in yerini alır; `authType=NONE` bilgileri temizler.
Farklı auth türüne geçişte eski türün bilgileri aktarılmaz.
Config POST bütün listeyi temsil eder; gönderilmeyen ortam/hedef kayıtları silinir.
Frontend örneği ve maskeleme kuralları için [frontend rehberine](frontend-integration.md) bakın.

### Anahtar ve şifreli saklama

Uygulamayı çalıştırmadan önce `CATALOG_CREDENTIALS_KEY` ortam değişkenine Base64
biçiminde 32 byte rastgele anahtar tanımlayın. Örneğin anahtarı kendi makinenizde
`openssl rand -base64 32` ile üretebilirsiniz. Anahtarı kaynak koda, veritabanına,
Git'e veya loglara koymayın; kalıcı bir secret deposunda saklayın. Her başlatmada
aynı anahtar gerekir. Kaybolan anahtarla saklanmış bilgiler geri çözülemez.

Yeni erişim bilgileri AES-256-GCM ile şifrelenir ve aynı JSONB sütununda format,
12-byte rastgele IV ve ciphertext olarak tutulur. Entegrasyon/ortam/hedef/auth türü
şifrelemeye bağlanır; başka ortama taşınan veya değiştirilen ciphertext reddedilir.
Yeni anahtara geçiş/anahtar rotasyonu bu sürümde otomatik yapılmaz.

Eski açık kayıtlar varsa:

1. Veritabanını yedekleyin; uygulamanın diğer instance'larını durdurun.
2. Kalıcı anahtarı tanımlayın, `CATALOG_CREDENTIALS_MIGRATE=true` ile bir kez başlatın.
3. Başarılı dönüşümden sonra `CATALOG_CREDENTIALS_MIGRATE=false` yapın; anahtarı koruyun.

Dönüşüm transaction içinde yapılır; şifreli kayıtlar tekrar şifrelenmez.
Açık kayıtlar varken dönüşüm kapalıysa, ya da şifreli kayıtlar mevcut anahtarla
çözülemiyorsa uygulama açılışı durur. Eski yedeklerdeki açık bilgiler bu işlemle
silinmez; yedeklerin erişimi ayrıca korunmalıdır. Credential dönüşümünün gerçek
DB testi ile payload saklamanın DB testi ayrı kontrollerdir.

### Ayrı izinler

| İzin | Etki |
|---|---|
| `VIEW_CREDENTIALS` | Yetkili görüntüleme endpoint'inden çözülmüş bilgileri alma |
| `MANAGE_CREDENTIALS` | EDITOR için yeni bilgi yazma, değiştirme ve silme |

ADMIN her iki yetkiye sahiptir. EDITOR ve VIEWER varsayılan olarak bu izinlere
sahip değildir. VIEWER'a görüntüleme izni verilebilir; değiştirme izni rolünün
salt okuma sınırını aşmaz. Görüntüleme ve değiştirme izinleri birbirini içermez.

ADMIN, `PUT /api/v1/users/{id}/permissions` ile izinlerin tamamını günceller:

```json
{"permissions":["VIEW_CREDENTIALS","MANAGE_CREDENTIALS"]}
```

Boş liste izinleri kaldırır. İzinler veritabanından kontrol edildiğinden mevcut
oturumlarda da iptal bir sonraki istekte etkili olur.

Frontend'deki "Göster" düğmesi yalnızca ihtiyaç olduğunda
`GET /api/v1/integrations/{id}/configs/DEV/DESTINATION/credentials` isteği yapar.
Bu yanıt `Cache-Control: no-store` içerir. Başarılı görüntüleme uygulama loguna
kullanıcı, entegrasyon, ortam ve hedef bilgisiyle yazılır; secret değerleri yazılmaz.
Secret görüntüleme audit'i uygulama logudur. Kullanıcı yönetimi işlemleri ayrıca
`account_audit` tablosuna yazılır. MFA henüz yoktur.

Config kaydetmede `MANAGE_CREDENTIALS` olmadan mevcut secret korunarak URL gibi
metaveriler güncellenebilir. Yeni secret, auth türü değiştirme veya secret içeren
config'i listeden çıkarma ayrıca izin gerektirir. Boş credential map reddedilir;
temizlemek için `authType=NONE` kullanılır. Yeni auth türü için bilgi verilmelidir.
Config ID ve oluşturma zamanı güncellemede korunur.

## Doğrulama

Giriş/CSRF/rol kontrolleri gerçek güvenlik filtreleriyle, credential dönüşümü ise
mock JDBC ile test edilir. Test grupları ve gerçek DB/SMTP kapsamının sınırları
[mimari belgesindedir](architecture.md#test-kapsamı-ve-doğrulama-yöntemi).

Referans: [Spring Security form login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html)
ve [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
Şifreleme referansı: [Java 21 GCM parametreleri](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html).
