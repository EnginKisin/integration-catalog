# Frontend entegrasyon rehberi

Ayrı repo kurulumu, yerel proxy ve henüz kararlaştırılmamış görsel mapping konuları
için [frontend devir notunu](frontend-handoff.md) da okuyun.

API kökü `/api/v1`'dir. Geliştirmede frontend dev server üzerinden `/api` yollarını
backend'e proxy edin; üretimde aynı origin altında yayınlayın. Backend'de farklı
origin'lere izin veren CORS yapılandırması yoktur. `credentials: "include"` tek başına
farklı origin erişimini açmaz. JWT veya localStorage'da giriş token'ı kullanılmaz.

## Oturum ve CSRF

1. `GET /api/v1/auth/csrf`: `headerName`, `parameterName`, `token` alın. Tarayıcı çerezi saklar.
2. Login formunu URL-encoded gövdeyle POST edin; CSRF başlığı ekleyin.
3. Başarılı login 200 ve boş gövde döner. CSRF'yi yeniden alın, sonra `/auth/me` okuyun.
4. Her POST/PUT/PATCH/DELETE isteğine CSRF başlığını ekleyin.
5. Logout 204'tür; istemcide profil/secret state'ini temizleyin. Yeniden girişte yeni CSRF alın.

Anonim davet/parola sıfırlama/e-posta doğrulama POST istekleri de CSRF gerektirir.
HttpOnly çerez JavaScript'ten okunmaz. Hareketsizlik süresi 30 dakikadır; sunucu
yeniden başladığında veya hesap oturumları iptal edildiğinde tekrar giriş gerekir.

Aşağıdaki TypeScript örneği aynı origin/proxy içindir. Boş başarılı yanıtları JSON
olarak okumaz ve başarısız yazma isteklerini otomatik tekrarlamaz:

```typescript
type Csrf = { headerName: string; parameterName: string; token: string };
let csrf: Csrf | undefined;

export async function refreshCsrf(): Promise<void> {
  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'include', cache: 'no-store'
  });
  if (!response.ok) throw new Error(`CSRF alınamadı: ${response.status}`);
  csrf = await response.json();
}

export async function api<T = void>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) await refreshCsrf();
    headers.set(csrf!.headerName, csrf!.token);
  }
  const response = await fetch(`/api/v1${path}`, {
    ...init, method, headers, credentials: 'include', cache: 'no-store'
  });
  const text = await response.text();
  let body: unknown = undefined;
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }
  if (!response.ok) {
    throw Object.assign(new Error(`İstek başarısız: ${response.status}`), {
      status: response.status, body, retryAfter: response.headers.get('Retry-After')
    });
  }
  return body as T;
}

export async function login(username: string, password: string): Promise<void> {
  await refreshCsrf();
  await api('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password })
  });
  csrf = undefined;
  await refreshCsrf();
}

export async function logout(): Promise<void> {
  try { await api('/auth/logout', { method: 'POST' }); }
  finally { csrf = undefined; }
}

// JSON isteği örneği:
// await api('/sources', {
//   method: 'POST', headers: { 'Content-Type': 'application/json' },
//   body: JSON.stringify({ name: 'ERP', description: 'Sipariş sistemi' })
// });
```

Login ve kimlik değişikliği isteklerini aynı anda başlatmayın. Örnek yardımcı
üretim uygulamasındaki state yönetimi ve hata ekranının yerine geçmez.
Frontend'in istek iptal süresi, backend işleminin geri alındığı anlamına gelmez;
bağlantı kesilmesinde yazmayı tekrar göndermeden önce güncel kaydı okuyun.

`GET /auth/me` örneği:

```json
{
  "username":"editor",
  "email":"editor@example.com",
  "emailVerified":true,
  "active":true,
  "authorities":["ROLE_EDITOR"],
  "permissions":["MANAGE_CREDENTIALS"]
}
```

Roller burada `ROLE_` öneki taşır; kullanıcı yönetimi gövdelerinde `EDITOR` gibi
önek olmadan gönderilir. Bootstrap/eski hesaplarda email null olabilir.
Butonları role göre gösterin; backend her istekte yetkiyi ayrıca denetler.
ADMIN credential okuma/yazma yetkisine doğrudan sahiptir; `permissions` listesinde
ayrıca yazmıyor diye ADMIN düğmelerini gizlemeyin. Diğer kullanıcılar için görüntüleme
`VIEW_CREDENTIALS`, değiştirme EDITOR + `MANAGE_CREDENTIALS` gerektirir.

## Ekranlar ve veri yükleme

| Ekran | İstek / davranış |
|---|---|
| Entegrasyon listesi | `GET /integrations?keyword=...`; dizi döner, sayfalama yok |
| Katalog yönetimi | `/sources`, `/destinations`, `/middlewares`; `active`, `usageCount` göster |
| Oluşturma / temel bilgi | Aktif sistem listeleri; POST oluşturma, PUT tam güncelleme |
| Entegrasyon detayı | `GET /integrations/{id}`; sistem adları için katalog ID'lerini eşleştir |
| Ortam ayarları | Detaydaki `environmentConfigs`; POST ile tüm config listesi |
| Şema / mapping | Ayrı GET schemas ve GET fields; POST ile şema upsert / tüm çizgileri kaydet |
| Payload / dosya örneği | XML/IDOC metin veya upload + alan ağacı; CSV/Excel yalnızca örnek dosya; [sözleşme](payload-content.md) |
| Gözden geçirme | `reviewRequired=true` ise inceleme onayı, ardından complete |
| Kullanıcı yönetimi | ADMIN; `/users?page=0&size=20`, davet, rol, aktiflik, izin, oturum iptali |
| Profil / hesap işlemleri | Parola değişimi, e-posta bağlama/değişimi ve doğrulama sayfası |

Detay için önce entegrasyon GET yapın. Olmayan entegrasyonun GET schemas isteği
404 döner; GET fields isteği boş liste döndürebilir. Alan eşleme listesinden
entegrasyonun varlığına karar vermeyin.
PUT gövdesine yalnızca request alanlarını koyun. Kaydetme sonrası detay ve ilgili
alt listeleri yeniden okuyun; bazı başarılı kayıtlar boş gövde döner.
Entegrasyon tarihleri offset içermeyen sunucu yerel `LocalDateTime` değerleridir;
`updatedAt` null olabilir. Bunlara istemcide kendiliğinden `Z` eklemeyin.

Pasif mevcut seçimi düzenleme ekranında koruyun; aktif listede yok diye null
göndermeyin. Silme için usageCount yalnızca UI yardımıdır; kararın son kontrolü
backend'dedir. [İş akışı](integration-workflow.md) ve [katalog kuralları](catalog-management.md)
ekranların temel sözleşmesidir.

Dosya yüklerken `FormData` içinde `metadata` (`application/json` Blob) ve `file`
gönderin; multipart `Content-Type` başlığını tarayıcı oluştursun. Yukarıdaki
`api()` yardımcısı yanıtı text/JSON okur; ikili örnek dosya indirmek için oturum
çereziyle ayrı `fetch` ve `response.blob()` kullanın. GET indirme CSRF istemez.
CSV/Excel kayıtlarında `mappingSupported=false` olduğundan eşleme araçları
gösterilmez; bir eşleme çiftinin iki ucu da bu desteğe sahip olmalıdır.

## Erişim bilgileri ekranı

Normal config yanıtta yalnızca `credentialsConfigured` vardır. Form ilk açıldığında
secret alma isteği göndermeyin. Maskeyi gerçek parola gibi gövdeye koymayın.

- Mevcut bilgiyi koruma: `authCredentials` alanını göndermeyin veya null gönderin;
  aynı ortam/hedef/auth türü kalmalıdır.
- Yeni bilgi: izin varsa dolu JSON map gönderin; eski map tamamen değişir.
- Temizleme: `authType=NONE`; `{}` geçerli temizleme gövdesi değildir.
- Görüntüleme: kullanıcı “Göster” dediğinde `GET /integrations/{id}/configs/DEV/DESTINATION/credentials`.
  Yanıt `{"authCredentials":{...}}`; `Cache-Control: no-store` içerir.

Backend BASIC_AUTH/OAUTH2/API_KEY için zorunlu map anahtarlarını tanımlamaz.
Örneğin username/password veya apiKey formu oluşturulacaksa bunun frontend
sözleşmesi ayrıca belirlenmelidir; backend bu alanların anlamsal doğrulamasını yapmaz.
Görüntülenen değerleri sadece geçici component state'inde tutun; önerilen davranış
30 saniye sonra, ekran kapanınca ve logout'ta gizleyip temizlemektir. Bu süre frontend
önerisidir; backend'de görünürlük sayacı veya yeniden parola sorma/MFA akışı yoktur.
Secret'ları localStorage, URL, analytics veya uygulama loglarına yazmayın.

## E-posta bağlantısı sayfası

`ACCOUNT_ACTION_URL` sayfasına bağlantı fragment ile gelir:
`#purpose=INVITATION&token=...`. `window.location.hash` değerini okuyun;
`URLSearchParams` ile ayrıştırıp token'ı yalnızca bellekte tutun ve
`history.replaceState` ile fragment'ı adres çubuğundan temizleyin.
Token'ı temizledikten sonra sayfa yenilenirse kullanıcı bağlantıyı yeniden açmalıdır.
Otomatik GET ile token tüketmeyin; kullanıcı onayıyla CSRF'li POST gönderin.

| purpose | Ekran / POST |
|---|---|
| `INVITATION` | Yeni parola; `/auth/invitations/accept` `{token,newPassword}` |
| `PASSWORD_RESET` | Yeni parola; `/auth/password-reset/confirm` `{token,newPassword}` |
| `EMAIL_CHANGE` | Adres onayı; `/auth/email/confirm` `{token}` |

Başarı 204'tür. Davet ve reset sonrası kullanıcı normal giriş yapar.
Süresi dolmuş/kullanılmış bağlantı 400'dür; davet için ADMIN yeniden gönderir,
reset için kullanıcı yeniden talep eder. Parola unutma talebinde her adres için
aynı bilgilendirmeyi gösterin; 202 postanın ulaştığı anlamına gelmez.
Hesap politikaları ve tam endpoint listesi [kullanıcı yönetimindedir](user-management.md).

## Hataları ele alma

| HTTP | Frontend davranışı |
|---|---|
| 400 | Geçersiz gövde/token; mesajı göster, formu koru |
| 401 | Giriş gerekli/hatalı; kullanıcıyı girişe yönlendir |
| 403 | Yetki veya CSRF sorunu olabilir; oturumu/CSRF'yi kontrol et, yazmayı otomatik tekrarlama |
| 404 | Kayıt yok; listeyi yenile |
| 409 | İş kuralı veya tamamlama engeli; varsa `details` listesini göster |
| 413 | İçerik/dosya boyut sınırı; boyutu azaltmasını iste, formu koru |
| 429 | Hız sınırı; varsa `Retry-After` kadar bekle, tekrar tekrar gönderme |
| 500 / 503 | İşlemin sonucunu kontrol et; güvenli genel hata göster |

Süresi biten oturumda yazma isteği CSRF nedeniyle 403 dönebilir; her 403'ü rol
eksikliği olarak göstermeyin. Hata gövdelerinin tüm alanları her yanıtta bulunmaz.
Kullanıcı mesajlarını metin olarak render edin. Eşzamanlı kullanıcı düzenlemeleri
için API'de ETag/If-Match veya istemciden beklenen sürüm alanı yoktur; backend
entity sürümlemesini tarayıcı için çakışma garantisi saymayın.
