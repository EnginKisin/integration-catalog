# Integration Catalog backend

Ortak sistem kataloğunu, entegrasyonların kaynak/hedef/ara katman rollerini,
ortam ayarlarını, erişim bilgilerini ve alan eşlemelerini yöneten REST API. Java 21, Spring Boot 3.5.16, Spring Security,
Spring Data JPA ve PostgreSQL kullanır.

Bu uygulama bir entegrasyon kataloğudur. Dış sistemlere iş isteği göndermez;
kayıtlı entegrasyonu çalıştıran bir runtime veya retry motoru içermez.
İsteğe bağlı [AI eşleme asistanı](docs/ai-mapping.md), Docker içindeki yerel Ollama
veya backend'de seçilen Google Gemini API ile JSON, XML ve IDOC arasında alan
bağlantıları önerir; kullanıcı onayı olmadan eşleme kaydetmez. Varsayılan sağlayıcı
Ollama'dır; Gemini kullanımı eşleme metaverisini/isteğe bağlı örnekleri Google'a gönderir.
Frontend ayrı projede geliştirilir; bu repo backend'i içerir.

## Geliştirme durumu

Backend MVP/pilot aşamasındadır. Katalog, hesap/rol yönetimi, credential şifreleme,
payload örnekleri ve alan eşleme tanımları uygulanmıştır. Üretim kabulü tamamlanmış
değildir; migration, işletim, yük ve eşzamanlılık konuları
[üretim sınırlarında](docs/architecture.md#yayın-ve-üretim-sınırları) açıklanır.
Lisans henüz seçilmedi; `LICENSE` eklenene kadar projeyi açık kaynak lisanslı
olarak tanımlamayın.

## Başlangıç

Docker Engine/Desktop ve Docker Compose gerekir. Container dışında geliştirme
için JDK 21 kullanın; Maven Wrapper repoda bulunur.

```sh
cp .env.example .env
```

`.env` içindeki `POSTGRES_PASSWORD`, ilk yönetici için `CATALOG_ADMIN_PASSWORD`
(12–128 karakter) ve `CATALOG_CREDENTIALS_KEY` alanlarını doldurun. Şifreleme
anahtarını bir kez `openssl rand -base64 32` ile üretip kalıcı olarak saklayın.
Mevcut veritabanında mevcut parola ve şifreleme anahtarını kullanın.

```sh
docker compose --profile mail up --build -d --wait --wait-timeout 180
docker compose ps
curl -i http://localhost:8080/actuator/health/readiness
```

| Adres | Kullanım |
|---|---|
| `http://localhost:8080/api/v1` | API kökü; oturum ve role göre erişim |
| `http://localhost:8080/swagger-ui/index.html` | ADMIN oturumu ile Swagger |
| `http://localhost:8080/v3/api-docs` | ADMIN oturumu ile OpenAPI JSON |
| `http://localhost:8080/actuator/health/readiness` | Giriş gerektirmeyen sağlık kontrolü |
| `http://localhost:8025` | Mailpit test posta kutusu; `mail` profili gerekir |

Giriş, JSON veya JWT yerine form verisi ve oturum çerezi kullanır. İlk istek
`GET /api/v1/auth/csrf` olmalıdır. [Frontend rehberinde](docs/frontend-integration.md)
çalıştırılabilir istemci örneği ve giriş sırası bulunur. Mailpit mesajları gerçek
alıcılara göndermez. `.env` IDE/Maven tarafından kendiliğinden yüklenmez.

## Belgeler

[Doküman dizini](docs/README.md) bütün backend belgelerinin başlangıç noktasıdır.
README kurulum ve proje kapsamını, `docs/` API ve iş kurallarını açıklar.

- [Frontend entegrasyonu](docs/frontend-integration.md): oturum, CSRF, ekran akışları ve hatalar.
- [Entegrasyon iş akışı](docs/integration-workflow.md): oluşturma, config, şema, mapping ve tamamlama.
- [API sözleşmesi](docs/api-reference.md): endpoint'ler, yanıtlar, enum ve doğrulama kuralları.
- [Yapılandırma ve işletim](docs/configuration.md): ortam değişkenleri, IDE, timeout ve sorun giderme.
- [Mimari ve doğrulama sınırları](docs/architecture.md): veri ilişkileri, güvenlik ve testler.
- [Payload ve dosya sözleşmesi](docs/payload-content.md): formatlar, yükleme/indirme, akış temizliği ve DB testleri.

## Testler

JDK 21 ile, harici veritabanı gerektiren uygulama açılış testini hariç tutarak:

```sh
./mvnw -B '-Dtest=*Tests,!IntegrationCatalogApplicationTests' test
```

`./mvnw test` ayrıca `IntegrationCatalogApplicationTests` testini çalıştırır;
ulaşılabilir PostgreSQL, şifreleme anahtarı ve boş kullanıcı tablosunda bootstrap
yönetici ayarlarını gerektirir. Docker image build'i testleri çalıştırmaz.
Test kapsamı ve gerçek ortamda henüz doğrulanmayan noktalar [mimari belgesindedir](docs/architecture.md).
`PayloadPostgresTests`, `MappingFlowPostgresTests`, `SystemCatalogPostgresTests` ve `AiMappingPostgresTests`
varsayılan olarak atlanır;
ayrı bir test veritabanıyla [opt-in komutuyla](docs/payload-content.md#veritabanı-geçişi-ve-test)
çalışır. Güncel sonuçlar `target/surefire-reports/` veya ilgili CI çalışmasından okunur.

## Paylaşım kontrolleri ve CI

```sh
python3 scripts/check_repository.py
```

Bu kontrol, Git'in takip ettiği yerel/özel dosya adlarını ve Markdown belgelerinin
yerel dosya bağlantılarını denetler; kapsamlı secret/CVE taraması yapmaz.
`.env`, credential dosyaları, loglar, DB yedekleri ve Finder metadata'sı ignore
edilir; boş `.env.example` paylaşılır. Daha önce commit edilmiş içerik ignore
kuralı eklemekle geçmişten silinmez.

[Backend CI](.github/workflows/ci.yml), push/PR ve elle başlatmada Java 21 ve
geçici PostgreSQL 16 ile bütün testleri (opt-in payload DB testi dahil) çalıştırır
ve JAR paketler. Gerçek ortam secret'ı gerektirmez; workflow değerleri yalnızca
izole test fixture'larıdır. GitHub'daki çalışma sonucu reponun Actions sekmesinden izlenir.
[Dependabot](.github/dependabot.yml), Maven, GitHub Actions ve Docker
bağımlılıklarını haftalık kontrol etmek üzere yapılandırılmıştır.
