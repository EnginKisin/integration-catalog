# Frontend entegrasyon rehberi

Bu belge ayrı frontend projesi için bağlantı, oturum, ekran ve hata akışını tanımlar.
Endpoint ayrıntıları [API dizininde](api-reference.md), entegrasyon yaşam döngüsü
[iş akışındadır](integration-workflow.md).

İsteğe bağlı “AI ile eşleştir” ekranında JSON, XML ve IDOC arasındaki dokuz çift
desteklenir. Öneri işi başlatma, durum sorgulama, alan yolları, bağımsız hedef
örnekleri ve kullanıcı onayıyla ekleme sözleşmesi [AI eşleme rehberindedir](ai-mapping.md).
AI sağlayıcısı (`ollama`/`gemini`) backend'de seçilir. Frontend aynı endpoint'leri
kullanır; sağlayıcı/model seçici veya API anahtarı girişi eklemeyin.
AI önerilerini eski bütün-liste `/mappings/fields` kaydına doğrudan göndermeyin;
seçilenleri `/mappings/fields/append` ile ekleyin.

API kökü `/api/v1`'dir. Geliştirmede frontend dev server üzerinden `/api` yollarını
backend'e proxy edin; üretimde aynı origin altında yayınlayın. Backend'de farklı
origin'lere izin veren CORS yapılandırması yoktur. `credentials: "include"` tek başına
farklı origin erişimini açmaz. JWT veya localStorage'da giriş token'ı kullanılmaz.

## Yerel bağlantı ve frontend projesi

Frontend ayrı repodaysa bu belgelerin hangi backend commit'ine ait olduğunu takip
edin. Kopya tutuyorsanız backend değiştiğinde yenileyin; `../README.md`,
`../scripts/` ve `../.github/` yolları backend repo köküne aittir.
Backend `.env` dosyası ve DB/SMTP/şifreleme/Gemini anahtarları frontend'e taşınmaz.
Kullanıcı giriş bilgisini formdan alır; tarayıcı ortam değişkenleri secret içermez.
AI yanıtındaki sağlayıcı/model alanları bilgilendirme metaverisidir.

Backend varsayılan olarak `http://localhost:8080` üzerinde çalışır. Vite kullanılıyorsa
mevcut `vite.config.ts` içindeki ayarlara aşağıdaki `server` bölümünü ekleyin:

```typescript
server: {
  port: 5173,
  strictPort: true,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}
```

Bu bir config parçasıdır; dosyanın tamamı değildir. `/api` önekini kaldıran rewrite
eklemeyin. Tarayıcı istekleri `/api/v1/...` biçiminde göreli olmalıdır; backend'in
8080 adresine doğrudan fetch yapmayın. APP_PORT değiştirilmişse proxy hedefini eşleştirin.
Üretimde aynı origin altında `/api` backend'e, uygulama yolları frontend'e gitmelidir.

Frontend'de `/account/action` rotasını doğrudan URL açılışında da çalıştırın.
Backend `ACCOUNT_ACTION_URL` bu rotanın tam adresiyle eşleşmelidir; varsayılanı
`http://localhost:5173/account/action` adresidir. Değişiklik backend container'ına
yeniden uygulanmalıdır. Gerçek SMTP kullanmak bu frontend sayfasını oluşturmaz.

### İlk oturum ve Swagger

Başlangıçta `/auth/me` isteğinin 401 dönmesi normaldir; giriş ekranını gösterin.
İlk admin bootstrap ile oluşturulur; `.env` değişikliği mevcut parolayı değiştirmez.

Swagger ve `/v3/api-docs` ADMIN oturumu gerektirir. `localhost:5173` üzerinden
giriş yapmak, tarayıcıda aynı hostname kullanıldığında `localhost:8080` üzerindeki
Swagger için de oturum çerezinin kullanılmasına olanak verir; çerezler portla
ayrılmaz. `localhost` ile `127.0.0.1` adreslerini karıştırmayın. Proxy üzerinden
Swagger kullanılacaksa `/swagger-ui` ve `/v3/api-docs` yollarını da ayrıca yönlendirin.
Swagger Authorize düğmesi login akışının yerine geçmez; yazma denemeleri CSRF ister.

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
// await api('/systems', {
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
| Katalog yönetimi | `/systems`; `active`, `usageCount` göster |
| Oluşturma / temel bilgi | Aktif sistem listeleri; POST oluşturma, PUT tam güncelleme |
| Entegrasyon detayı | `GET /integrations/{id}`; sistem ID/adları ve environmentTypes doğrudan yanıtta |
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
Ara katman ekleme/kaldırma sonrasında güncel akışa uygun ilk başarılı örnek kaydı,
eski akışın uyumsuz şemalarını ve bağlı alan eşlemelerini aynı transaction'da
temizler. Metin veya dosya kaydı sonrasında schemas ve fields listelerini yeniden
okuyun. Başarısız kayıt eski örnekleri ve eşlemeleri korur.
Entegrasyon tarihleri offset içermeyen sunucu yerel `LocalDateTime` değerleridir;
`updatedAt` null olabilir. Bunlara istemcide kendiliğinden `Z` eklemeyin.

Pasif mevcut seçimi düzenleme ekranında koruyun; aktif listede yok diye null
göndermeyin. Silme için usageCount yalnızca UI yardımıdır; kararın son kontrolü
backend'dedir. [İş akışı](integration-workflow.md) ve [sistem kuralları](systems-frontend.md)
ekranların temel sözleşmesidir.

Dosya yüklerken `FormData` içinde `metadata` (`application/json` Blob) ve `file`
gönderin; multipart `Content-Type` başlığını tarayıcı oluştursun. Yukarıdaki
`api()` yardımcısı yanıtı text/JSON okur; ikili örnek dosya indirmek için oturum
çereziyle ayrı `fetch` ve `response.blob()` kullanın. GET indirme CSRF istemez.
CSV/Excel kayıtlarında `mappingSupported=false` olduğundan eşleme araçları
gösterilmez; bir eşleme çiftinin iki ucu da bu desteğe sahip olmalıdır.

Ortak sistem seçimi, haritada düğüm tekilleştirme ve ortam filtresi için
[frontend sistem sözleşmesini](systems-frontend.md) kullanın.

## Alan eşleme ekranı

Doğrudan mapping iki ucu aynı şema ID'sine bağlar; kaynak tarafında
`format/schemaDefinition/samplePayload/rawContent`, bağımsız hedef tarafında
`targetFormat/targetSchemaDefinition/targetSamplePayload/targetRawContent` kullanılır.
Ara katmanlı akışta hedef ayrı şema kaydıdır. Desteklenen çiftler JSON/XML/IDOC
arasında bağımsız seçilir; alan yollarını [AI sözleşmesine](ai-mapping.md) göre üretin.

XML/IDOC alan yolu varlığı kontrol edilir. JSON'da kaynak örneği veya bağımsız hedef
tanımı/örneği varsa tam liste kaydı da yol varlığını kontrol eder; kaynak örneği
bulunmayan JSON taraflarında bu eski endpoint'in kontrolü sınırlıdır. AI job/append akışı
normalize alan yollarını doğrular; model önerilerinde tip/dizi uyumu ayrıca
denetlenir. Manuel bağlantılar için otomatik tip dönüşümü veya anlamsal doğruluk
garantisi verilmez.

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

Backend mesajları İngilizcedir. Kullanıcıya gösterilen metni `code` üzerinden,
alan ve tamamlama hatalarını `violations[].code` + `params` üzerinden yerelleştirin.
`message` ve `details` güvenli yedek açıklamalardır; davranış belirlemek için
ayrıştırmayın. Bilinmeyen kodda frontend'in genel hata mesajını kullanın.

| HTTP | İstemci davranışı |
|---|---|
| 400 | `VALIDATION_FAILED` ise bütün alan ihlallerini ilgili input altında göster; diğer kodlar istek/gövde hatasını belirtir |
| 401 | `AUTH_REQUIRED`, `INVALID_CREDENTIALS` veya `SESSION_INVALID` koduna göre giriş akışını yönet |
| 403 | `ACCESS_DENIED`, credential izni ve `CSRF_MISSING`/`CSRF_INVALID` durumlarını ayır |
| 404 | Kayıt bulunamadı; ilgili kaynağın koduna göre mesaj seç |
| 409 | İş kuralını kodla göster; `INTEGRATION_INCOMPLETE` için kodlu ihlalleri listele |
| 410 | AI sonucu süresi dolmuş; yeni öneri iste |
| 413 | İçerik/bütçe sınırı; ilgili kodu ve varsa limit parametrelerini kullan |
| 422 | AI veya şema/alan doğrulama koduna göre girdiyi düzelt |
| 429 | Her zaman gelen `Retry-After` saniyesi kadar bekle; bu süre başarı garantisi değildir |
| 500 / 503 | Genel hata/geçici servis mesajını göster; destek için `requestId` sakla |

`X-Request-ID` her yanıtta bulunur; HTTP hata gövdesindeki `requestId` aynı
değerdir. İş durumu HTTP 200 dönse bile AI `status=FAILED`/`EXPIRED` olabilir;
`errorCode` okunmalıdır. Bu durumda başarılı durum sorgusu için `Retry-After`
beklenmez; çalışan işler `pollAfterMs` aralığında sorgulanır.

[Ortak hata sözleşmesi](error-codes.md), [tam kod dizini](error-code-index.md)
ve [örnek yanıt kataloğu](error-catalog.json) istemci için esas kaynaklardır.

Teknik seçim değiştiğinde kullanıcı config/şema/eşlemeyi gözden geçirip incelemeyi
onaylar; ardından complete çağırır. Eski config/mapping kayıtları ETag/If-Match
veya beklenen sürüm almaz. AI context/job/append ise eski `contextRevision` ile
kaydı reddeder.

## Frontend kabul kontrolleri

- Giriş, yanlış parola, sayfa yenilemede oturum kontrolü ve çıkış çalışır.
- Oturum bittiğinde 401/CSRF kaynaklı 403 ele alınır; yazmalar otomatik tekrarlanmaz.
- VIEWER, EDITOR ve ADMIN ekran eylemleri ile credential izinleri ayrı doğrulanır.
- Katalog oluşturma/güncelleme, pasif mevcut seçimi koruma ve kullanılan kaydın
  silinememesi ele alınır.
- Entegrasyon oluşturma → config → şema → isteğe bağlı mapping → tamamlama çalışır;
  409 kodlu `violations` listesi yerelleştirilerek gösterilir. Dört akışın zorunlu şema matrisi kontrol edilir.
- Config/field kaydının tüm listeyi değiştirdiği dikkate alınır; tek satır kaydetme
  diğer satırları yanlışlıkla silmez. Maskelenmiş secret gövdeye gönderilmez.
- XML/IDOC alan ağacı ve yol hataları, CSV/Excel'de eşleme kapatma, multipart
  yükleme, dosya indirme ve 413 boyut hataları uygulanır.
- AI önerilerinde manuel bağlantılar korunur; öneri düzenleme/silme/onay, durum
  sorgulama, eski bağlam ve sağlayıcı hatasında manuel kullanım ele alınır.
- Teknik değişiklik sonrası inceleme ve yeniden tamamlama akışı uygulanır;
  güncel akışa uygun ilk başarılı örnek kaydı eski topolojinin uyumsuz şemalarını
  ve bağlı eşlemeleri temizlediği için schemas ve fields listeleri yeniden okunur.
- Davet, parola sıfırlama ve e-posta onayı bağlantıları doğrudan açılır;
  fragment temizlenir, CSRF'li POST yapılır, süre/kullanım hataları gösterilir.
- 200 boş, 201 JSON, 202 boş/JSON ve 204 boş yanıtlar doğru işlenir.

Bu liste frontend kabulü için kullanılmalıdır. Backend testlerinin kapsamı
[mimari belgesindedir](architecture.md#test-kapsamı-ve-doğrulama-yöntemi).
