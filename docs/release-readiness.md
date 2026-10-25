# Public paylaşım ve backend olgunluğu

İnceleme tarihi: 25 Ekim 2026. Bu değerlendirme yerel çalışma kopyasına ve
yerelde erişilebilen Git geçmişine aittir. Public repo açılması ile çalışan
uygulamanın internete açılması farklı kararlardır.

## Genel değerlendirme

Backend, işlevli bir MVP/pilot seviyesindedir. Katalog ve hesap yönetimi, rol/CSRF
kontrolleri, credential şifreleme, tamamlama kuralları ve format bazlı payload
saklama uygulanmıştır. Olgun bir üretim sürümü olarak değerlendirmek için aşağıdaki
işletim, veri geçişi ve doğrulama eksikleri giderilmelidir.

Son test raporlarında 379 test, sıfır hata/başarısızlık/atlama vardır. Bu çalıştırma
izole PostgreSQL ile uygulama açılışını ve beş formatın gerçek DB kayıt/okumasını
içerir. Test sayısı kod kapsam yüzdesi, yük testi veya üretim kabulü anlamına gelmez.
Testlerin ayrıntılı sınırları [mimari belgesindedir](architecture.md).

## Public paylaşım öncesi yerel bulgular

| Konu | İncelemedeki durum | Yapılması gereken |
|---|---|---|
| `.env` | Ignore ediliyor; indeks ve yerel geçmişte bulunmadı | Git dışında tutun; yalnızca boş `.env.example` paylaşın |
| Parola/anahtar izi | Dört yerel hassas `.env` değeri yayınlanabilecek dosyalarda ve geçmişte eşleşmedi; belirgin anahtar/token örüntüsü bulunmadı | Bu sınırlı taramayı kapsamlı secret taramasıyla tamamlayın |
| Git geçmişi | Yerelde erişilebilen iki commit ve 100 benzersiz blob tarandı; ikili bloblarda da yerel secret değerleri karşılaştırıldı | GitHub'daki diğer geçmiş/artefaktlar ayrıca incelenmeli |
| Gereksiz metadata | Yedi `.DS_Store` Git indeksinden çıkarıldı; yerel dosyalar korunuyor | Silme kayıtları commit'e dahil edilmeli; dosyalar eski commit'lerde hâlâ var |
| Yerel dosya koruması | `.env`, anahtarlar, yerel araç ayarları, loglar ve DB yedekleri ignore ediliyor | `python3 scripts/check_repository.py` ile dosya adlarını/bağlantıları denetleyin |
| Lisans | Kullanıcı henüz karar vermedi; `LICENSE` yok, POM lisans metaverisi boş | Lisans kararı sonrası dosya ve metaveri eklenmeli |
| CI ve bağımlılık takibi | Java 21 + PostgreSQL CI ve haftalık Maven/Actions/Docker Dependabot dosyaları hazır | Commit/push sonrası ilk Actions sonucu görülmeli; tam secret/CVE taraması ayrıca gerekli |
| Güvenlik bildirimi | Kullanıcı tercihiyle `SECURITY.md` eklenmeyecek; GitHub üzerinden özel bildirim kullanılacak | `Private vulnerability reporting` ayrıca etkinleştirilmeli; GitHub ayarının açık olduğu bu çalışmada doğrulanmadı |
| Çalışma kopyası | Çok sayıda değiştirilmiş/takip edilmeyen dosya var | Yayımlanacak commit'in bu kodu, testleri ve belgeleri içerdiğini doğrulayın |

Örüntü taramasındaki `https://user:pass@example.com/action` eşleşmesi,
`AccountMailTests` içindeki reddedilmesi beklenen sentetik URL testidir; gerçek
hesap bilgisi olarak değerlendirilmedi. `application.properties` içindeki
`catalog_password` yerel geliştirme varsayılanıdır; üretim parolası olarak kullanılmamalıdır.
Testlerdeki sabit anahtar/parolalar test fixture'ıdır; gerçek ortam için kullanılmaz.

Tarama GitHub sunucusuna bağlanmadı. Uzakta olup yerelde bulunmayan ref'ler,
Actions log/artefaktları, release dosyaları, issue/PR açıklamaları ve özel kurum
verisinin anlamsal incelemesi bu değerlendirmenin kapsamında değildir.
Bağımlılık ağacının tamamında otomatik CVE taraması da çalıştırılmadı.
Bu nedenle sonuç, bütün olası sırların veya açıkların bulunmadığı garantisi değildir.

GitHub'a göre public görünürlükte kod ve Actions geçmişi/logları herkese açılır;
mevcut kopyalar/forklar sonradan private yapılınca geri alınmış sayılmaz.
[Görünürlük etkileri](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/managing-repository-settings/setting-repository-visibility).
Public paylaşım ile açık kaynak lisanslama aynı işlem değildir;
[lisans rehberini](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)
inceleyip projenin paylaşım amacına göre karar verin.
GitHub'ın [secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning)
ve [bağımlılık güvenliği](https://docs.github.com/en/code-security/getting-started/quickstart-for-securing-your-repository)
özellikleri yayın sonrası izleme için kullanılabilir.

## Üretim öncesi öncelikler

1. **Sürümlü DB geçişleri:** Flyway/Liquibase baseline ve tüm yeni tablo/kolonların
   yükseltme yolu hazırlanmalı. Mevcut payload SQL dosyası tüm geçmiş şemanın
   migration sistemi değildir. Yedek alma ve aynı anahtarla geri yükleme sınanmalı.
2. **Tam iş akışı ve eşzamanlılık:** Gerçek PostgreSQL üzerinde son ADMIN koruması,
   token tüketimi, katalog silme/atama, cascade silme ve rollback davranışı;
   gerçek SMTP veya Mailpit üzerinden hesap akışları uçtan uca test edilmeli.
3. **Şema yaşam döngüsü:** Ara katmanlı/doğrudan topoloji değişiminden kalan uyumsuz
   şemalar tamamlamayı engelleyebilir. Tekil şema silme/temizleme akışı bulunmuyor.
   Bu kullanıcı akışı ürün seviyesinde tamamlanmalı.
4. **Dağıtım ve işletim:** Üretim profili, HTTPS/Secure cookie, kısıtlı DB kullanıcısı,
   secret yönetimi, merkezi log/uyarı, yedek ve izleme kurulmalı. Mevcut Compose
   localhost geliştirme kurulumudur; yalnızca unhealthy durumunda restart yapmaz.
5. **Kaynak tüketimi:** Excel ayrıştırma aynı JVM'de çalışır; ayrıca süre/CPU sınırı
   yoktur. Sıkıştırılmış dosyanın açılmış boyutu, örnek dosyaların DB büyümesi,
   saklama süresi, sayfalamasız listeler ve eşzamanlı upload yükü ölçülmeli.
6. **Çakışma ve yetki modeli:** ETag/If-Match gibi istemci çakışma protokolü yoktur.
   Yetkiler uygulama geneli rol bazındadır; ekip/tenant ve kayıt bazlı gizlilik
   gerekiyorsa ayrıca tasarlanmalı. Çok instance için ortak session/rate-limit
   deposu da mevcut değildir.
7. **Sürüm güvenliği:** CI testleri, bağımlılık/CVE taraması ve güvenlik güncelleme
   süreci işletilmeli. Hazırlanan workflow'un ilk GitHub çalıştırması doğrulanmalı;
   Dependabot sürüm kontrolü tam güvenlik taraması olarak değerlendirilmemeli.
   Genel exception handler'a düşen framework hatalarının
   HTTP durumları ve üretim loglaması tutarlı hale getirilmeli.

## Paylaşım temizliğinin doğrulanması

Git indeksinden yalnızca yedi `.DS_Store` çıkarıldı; `.env` ve yerel dosyalar
korundu. Git geçmişi yeniden yazılmadı. `.gitignore` ve Docker build context
kuralları genişletildi. POM adı/açıklaması dolduruldu; lisans seçilmediği için
lisans metaverisi eklenmedi.

[CI workflow'u](../.github/workflows/ci.yml), `contents: read` izniyle push ve PR
olaylarında test/paketleme çalıştırır; deploy veya repo görünürlüğünü değiştirmez.
[Dependabot ayarları](../.github/dependabot.yml) haftalık sürüm kontrolü tanımlar.
[Yerel kontrol](../scripts/check_repository.py) dosya adları ve belge bağlantıları
içindir; dosya içeriğinde secret taraması veya harici bağlantı kontrolü yapmaz.

Bu öncelikler, iş kapsamı aynı kalan bir katalog backend'inin üretim hazırlığına
aittir. Entegrasyon çalıştırma motoru veya AI mapping servisi eklemek üretim
olgunluğunun ön şartı değildir; bunlar mevcut ürün kapsamının dışındadır.
