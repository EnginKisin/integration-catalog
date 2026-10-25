# Ayrı frontend projesine devir

Backend belgeleri mevcut API ile frontend geliştirmeye başlamak için yeterlidir.
Bütün ürün ve görsel mapping kararlarının verilmiş olduğu anlamına gelmez.
Önce [frontend rehberini](frontend-integration.md), [API sözleşmesini](api-reference.md),
[iş akışını](integration-workflow.md), [payload sözleşmesini](payload-content.md)
ve [kullanıcı yönetimini](user-management.md) okuyun.

## Projeler ve yerel bağlantı

Frontend ayrı bir repoda olabilir. Bu `docs` klasörünü frontend reposuna
`docs/backend/` olarak kopyalayın; backend değiştiğinde kopyayı da güncelleyin.
Kopyanın hangi backend commit'ine ait olduğunu devir notuna yazın. Doküman dizininin
`../README.md` bağlantısı backend repo köküne aittir; ayrı kopyada bu dosya ayrıca
aktarılmadıysa bağlantı çalışmaz.

Backend `.env` dosyasını frontend'e taşımayın. PostgreSQL, SMTP, bootstrap parolası
ve şifreleme anahtarı frontend ayarı değildir. Kullanıcı giriş bilgisini formdan alır.
Frontend ortam değişkenleriyle tarayıcıya secret göndermeyin.

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

## İlk oturum ve Swagger

Başlangıçta `/auth/me` isteğinin 401 dönmesi normaldir; giriş ekranını gösterin.
Giriş CSRF alımı → form-urlencoded login → yeni CSRF → profil sırasını izler.
API yollarında `/api/v1` önekini kullanın. Hazır backend giriş ekranı yoktur.
İlk admin şifresi bootstrap sırasında oluşturulur; sonradan `.env` değiştirmek
mevcut hesabın şifresini değiştirmez.

Swagger ve `/v3/api-docs` ADMIN oturumu gerektirir. `localhost:5173` üzerinden
giriş yapmak, tarayıcıda aynı hostname kullanıldığında `localhost:8080` üzerindeki
Swagger için de oturum çerezinin kullanılmasına olanak verir; çerezler portla
ayrılmaz. `localhost` ile `127.0.0.1` adreslerini karıştırmayın. Proxy üzerinden
Swagger kullanılacaksa `/swagger-ui` ve `/v3/api-docs` yollarını da ayrıca yönlendirin.
Swagger Authorize düğmesi login akışının yerine geçmez; yazma denemeleri CSRF ister.

## Henüz belirlenmemiş sözleşmeler

| Konu | Mevcut backend davranışı | Frontend için karar |
|---|---|---|
| JSON alan ağacı | `schemaDefinition` serbest JSON nesnesi | JSON ağacı gösterimi frontend'de belirlenmeli; XML/IDOC ağacı backend tarafından üretilir |
| Payload içe aktarma | XML/IDOC metin ve dosya; CSV/Excel örnek dosya desteği var | [Payload sözleşmesi](payload-content.md); CSV/Excel için mapping gösterilmez |
| Erişim bilgisi formları | `authCredentials` serbest JSON map; auth türüne göre zorunlu anahtar yok | BASIC_AUTH/OAUTH2/API_KEY alan adları kararlaştırılmalı veya başlangıçta JSON editörü kullanılmalı |
| Liste ve dashboard | Entegrasyonlar dizi; özel istatistik endpoint'i yok | İstemcide filtreleme/hesaplama yapılabilir; sunucu sayfalaması veya istatistik endpoint'i varsayılmamalı |

Doğrudan mapping iki ucu aynı şema kaydına bağlar. Ayrı kaynak/hedef ağaçlarının
nasıl temsil edileceği mevcut API tarafından tanımlanmaz. Backend, XML/IDOC alan
yollarının varlığını kontrol eder; JSON yol varlığı ve tip uyumunu kontrol etmez. Görsel editör bu boşluğu çözmeden
otomatik doğru eşleme sunduğunu iddia etmemelidir.

## Frontend kabul kontrolleri

- Giriş, yanlış parola, sayfa yenilemede oturum kontrolü ve çıkış çalışır.
- Oturum bittiğinde 401/CSRF kaynaklı 403 ele alınır; yazmalar otomatik tekrarlanmaz.
- VIEWER, EDITOR ve ADMIN ekran eylemleri ile credential izinleri ayrı doğrulanır.
- Katalog oluşturma/güncelleme, pasif mevcut seçimi koruma ve kullanılan kaydın
  silinememesi ele alınır.
- Entegrasyon oluşturma → config → şema → isteğe bağlı mapping → tamamlama çalışır;
  409 `details` listesi gösterilir. Dört akışın zorunlu şema matrisi kontrol edilir.
- Config/field kaydının tüm listeyi değiştirdiği dikkate alınır; tek satır kaydetme
  diğer satırları yanlışlıkla silmez. Maskelenmiş secret gövdeye gönderilmez.
- XML/IDOC alan ağacı ve yol hataları, CSV/Excel'de eşleme kapatma, multipart
  yükleme, dosya indirme ve 413 boyut hataları uygulanır.
- Teknik değişiklik sonrası inceleme ve yeniden tamamlama akışı uygulanır;
  eski topoloji şemalarını silen endpoint olmadığı kullanıcıya açıklanır.
- Davet, parola sıfırlama ve e-posta onayı bağlantıları doğrudan açılır;
  fragment temizlenir, CSRF'li POST yapılır, süre/kullanım hataları gösterilir.
- 200 boş, 201 JSON, 202 boş/JSON ve 204 boş yanıtlar doğru işlenir.

Bu liste doğrulama planıdır; bu belge eklenirken frontend veya gerçek e-posta
üzerinden uçtan uca test yapılmadı. Backend readiness sonucunun UP olması giriş,
SMTP teslimi veya bütün iş akışlarının doğrulandığı anlamına gelmez.
