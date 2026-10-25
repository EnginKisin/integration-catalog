# Source, destination ve middleware yönetimi

Üç kayıt türü aynı davranışı kullanır. Aşağıdaki `{type}` değeri `sources`,
`destinations` veya `middlewares` olabilir.

| Endpoint | Yetki | Davranış |
|---|---|---|
| `GET /api/v1/{type}` | VIEWER / EDITOR / ADMIN | Aktif ve pasif bütün kayıtlar |
| `GET /api/v1/{type}?active=true` | VIEWER / EDITOR / ADMIN | Yeni seçim için aktif kayıtlar |
| `GET /api/v1/{type}?active=false` | VIEWER / EDITOR / ADMIN | Pasif kayıtlar |
| `GET /api/v1/{type}/{id}` | VIEWER / EDITOR / ADMIN | Aktiflikten bağımsız detay |
| `POST /api/v1/{type}` | EDITOR / ADMIN | Aktif kayıt oluşturma |
| `PUT /api/v1/{type}/{id}` | EDITOR / ADMIN | İsim/açıklama güncelleme |
| `PATCH /api/v1/{type}/{id}/active` | EDITOR / ADMIN | Pasifleştirme/yeniden aktifleştirme |
| `DELETE /api/v1/{type}/{id}` | ADMIN | Yalnızca kullanılmayan kaydı silme |

Güncelleme gövdesi `{ "name": "ERP", "description": "Order system" }`;
aktiflik gövdesi `{ "active": false }` biçimindedir. Değiştiren isteklerde
oturum ve CSRF token'ı gerekir. Eksik kayıt 404, kullanılan kaydı silme 409 döner.
Başarılı silme yanıtı 204'tür.

## Bağlı entegrasyonlar korunur

Yanıtlarda `active` ve `usageCount` bulunur. Kullanım sayısı, taslak ve tamamlanmış
kayıtlar dahil ilgili sisteme bağlı bütün entegrasyonları sayar. Listeleme sayıları
tek bir grup sorgusuyla alınır; her kayıt için ayrı count sorgusu çalışmaz.

İsim/açıklama güncellemesi ortak tanımı değiştirir; ID ve bağlantılar aynı kalır.
Pasifleştirme mevcut entegrasyonları değiştirmez veya taslağa döndürmez.
Pasif kayıtlar detaydan okunabilir, yeniden aktif edilebilir; kullanılıyorsa silinemez.
Silme hiçbir entegrasyonun source/destination/middleware alanını boşaltmaz.

Kullanılan kaydı silme örnek hatası:

```json
{"status":409,"message":"Source is used by 5 integration(s); deactivate it instead"}
```

Frontend kullanım sayısı sıfır değilse silme yerine pasifleştirme sunabilir.
Backend kullanım sayısını silme anında tekrar kontrol eder. Katalog değişiklikleri
ve entegrasyona ilişki atama aynı katalog satırını transaction boyunca kilitler;
böylece silmeyle eşzamanlı yeni bağlantı kurulması veya pasif kayıt seçimi sıralanır.
Gerçek PostgreSQL üzerindeki kilit davranışı bu geliştirme ortamında test edilmedi.

## Entegrasyon seçimini düzeltme

`PUT /api/v1/integrations/{id}` mevcut tam güncelleme endpoint'idir. Yanlış source
seçilmişse doğru `sourceId` ve diğer zorunlu alanlar gönderilebilir. Entegrasyonun ID'si
korunur. Her yeni seçim mevcut ve aktif olmalıdır. Pasif bir seçim yalnızca aynı
entegrasyonun mevcut seçimiyse korunabilir; oluşturma veya başka bir seçimden pasif
kayda geçiş 409 döner. Kontroller başarısızsa mevcut entegrasyon değiştirilmez.

Örnek frontend davranışı: yeni entegrasyonda yalnızca `active=true` kayıtlarını
gösterin. Düzenleme ekranında mevcut seçim pasifse detay endpoint'inden alın ve
"Pasif — mevcut seçim" olarak göstermeye devam edin; listede yok diye seçimi boşaltmayın.

Source/destination/middleware ID, payload formatı, senkronluk veya middleware
kullanım bilgisi değişince entegrasyon `DRAFT` olur. Ortam ayarları, şemalar,
mapping'ler ve kayıtlı credential'lar otomatik silinmez. Mevcut alt veri varsa
`reviewRequired=true` döner: bunların hâlâ yeni seçime uygun olduğunun incelenmesi gerekir.
Yalnızca entegrasyon ismi/açıklaması değişirse tamamlanmış durum korunur.

Frontend `reviewRequired=true` için kullanıcıya ortam ayarlarını, şemaları ve
mapping'leri gözden geçirmesini söylemelidir. Kullanıcı onayladığında EDITOR/ADMIN:

```http
POST /api/v1/integrations/1/review/confirm
Content-Type: application/json

{"confirmed":true}
```

Bu işlem yalnızca inceleme bayrağını kaldırır; kaydı otomatik tamamlamaz.
Ardından mevcut `PATCH /api/v1/integrations/{id}/complete` çağrısı yapılır. İnceleme
bekleniyorsa tamamlamada 409 ve `details` içinde açıklama döner. Onaydan sonra da
mevcut tamamlama kuralları uygulanır; eksik veya tutarsız veri tamamlanamaz.
Şema türleri ve ortamların tamamlama matrisi [entegrasyon iş akışındadır](integration-workflow.md).
Tekil şema silme endpoint'i yoktur; topoloji değişiminden kalan uyumsuz şemalar
inceleme onayından sonra da tamamlamayı engelleyebilir.
İnceleme onayı, kullanıcının veriyi değerlendirdiğine ilişkin açık bir bildirimdir;
alan yollarının JSON şeması içinde gerçekten bulunmasını doğrulayan bir motor değildir.

Ortam ayarı, şema veya mapping kaydetmek de tamamlanmış entegrasyonu taslağa döndürür.
Bu kayıt işlemleri tek başına `reviewRequired` bayrağını kaldırmaz. Katalog isim
güncellemesi ve aktiflik değişikliği ise entegrasyonun bağlantısını değiştirmez.

## Doğrulama

`SourceManagementTests`, `DestinationManagementTests`, `MiddlewareManagementTests`
kullanım sayısı, güncelleme, aktiflik ve silme kurallarını; `CatalogManagementWebTests`
gerçek güvenlik filtreleriyle HTTP erişimini, doğrulamayı ve inceleme endpoint'ini test eder.
`IntegrationSystemSelectionTests` pasif seçimlerin korunmasını ve teknik değişikliklerin
alt verileri koruyarak taslağa dönmesini; `IntegrationCompletionTests` inceleme
onayından önce tamamlamanın engellenmesini test eder.

Yeni `active`, `review_required` ve integration `version` sütunları mevcut geliştirme
ayarı `ddl-auto=update` üzerinden oluşturulur. Gerçek veritabanı migration testi
yapılmadı; üretim öncesi sürümlü migration çalışması gerekir.
