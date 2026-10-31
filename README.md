# Integration Catalog Backend

Integration Catalog, büyük ölçekli şirketlerin karmaşık sistem mimarilerini ve
entegrasyon ilişkilerini dokümante etmeyi hedefleyen, entegrasyon odaklı bir
**Enterprise Architecture (Kurumsal Mimari) Portalı** projesidir. Hangi sistemin
hangi sisteme, hangi formatta veri aktardığını ve alanların nasıl eşlendiğini
kayıt altına alarak tanımlanan veri akışlarının haritalandırılmasını sağlar.
Yapay zeka, alan eşleme önerileriyle bu dokümantasyon sürecini destekler.

**Bu proje bir veri taşıma motoru değildir.** Sistemler arasında veri aktarmaz,
entegrasyonları çalıştırmaz; timeout ve retry değerlerini katalog bilgisi olarak saklar.

Bu repo portalın REST API backend'ini içerir; frontend ayrı projededir.
Ortam ayarları, şifreli erişim bilgileri, kullanıcı daveti ve rol yönetimi de bu API üzerinden yönetilir.

**Teknolojiler:** Java 21 · Spring Boot 3.5.16 · Spring Security · Spring Data JPA · PostgreSQL

## Temel akışlar

### Sistem bağlantıları

Ortak katalogdan kaynak, hedef ve isteğe bağlı ara katman seçilir.

![Doğrudan ve ara katmanlı entegrasyonlarda istek ve yanıt yönleri](docs/images/system-connections.png)

### Entegrasyon kaydı

Sistemleri seç → `DRAFT` oluştur → ortam ayarlarını ekle → şema/örnekleri kaydet
→ isteğe bağlı alan eşlemelerini yap → tamamlama kontrolü → `COMPLETED`.

![Sistem seçiminden DRAFT kaydına, tamamlama kontrolüne ve COMPLETED durumuna entegrasyon kayıt süreci](docs/images/integration-workflow.png)

API örnekleri ve tamamlama kuralları: [Entegrasyon iş akışı](docs/integration-workflow.md).

### AI ile alan eşleme

JSON/XML/IDOC alanları manuel veya AI önerileriyle eşlenebilir; CSV/Excel yalnızca
örnek dosya olarak saklanır. AI varsayılan olarak kapalıdır; sağlayıcısı yerel
Ollama'dır. Alternatif Gemini, alan metaverisini/isteğe bağlı örnekleri Google'a gönderir.

![AI önerisi isteme, iş durumunu sorgulama ve kullanıcı onayıyla seçilen alan eşlemelerini kaydetme akışı](docs/images/ai-mapping-flow.png)

Kurulum ve API: [AI eşleme asistanı](docs/ai-mapping.md). Öneriler kullanıcı onayıyla kaydedilir.

## Başlangıç

Docker ve Docker Compose gerekir. Yerel geliştirme/test için JDK 21 ve repodaki Maven Wrapper kullanılır.

### 1. Ortam dosyasını hazırla

```sh
cp .env.example .env
```

`.env` değerlerini tamamlayın:

| Değişken | Değer |
|---|---|
| `POSTGRES_PASSWORD` | Veritabanı parolası |
| `CATALOG_ADMIN_USERNAME` | İlk yönetici adı; örnekte `admin` |
| `CATALOG_ADMIN_PASSWORD` | İlk yönetici parolası; 12–128 karakter |
| `CATALOG_CREDENTIALS_KEY` | `openssl rand -base64 32` ile bir kez üretilen anahtar |

Anahtarı kalıcı saklayın; mevcut veritabanının parolasını/anahtarını koruyun.
IDE/Maven `.env` dosyasını otomatik yüklemez.

### 2. Servisleri başlat

```sh
docker compose --profile mail up --build -d --wait --wait-timeout 180
docker compose ps
curl -i http://localhost:8080/actuator/health/readiness
```

`mail`, gerçek alıcılara gönderim yapmayan Mailpit test posta kutusunu açar.

### 3. API'ye bağlan

Aşağıdaki adresler varsayılan portlar içindir:

| Adres | Kullanım |
|---|---|
| `http://localhost:8080/api/v1` | API kökü; oturum ve rol kontrolü |
| `http://localhost:8080/swagger-ui/index.html` | Swagger; ADMIN oturumu gerekir |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON; ADMIN oturumu gerekir |
| `http://localhost:8080/actuator/health/readiness` | Giriş gerektirmeyen sağlık kontrolü |
| `http://localhost:8025` | Mailpit test posta kutusu |

Giriş: **CSRF al → form verisiyle giriş yap → CSRF yenile**. Oturum çerezini koruyun.
[Çalıştırılabilir istemci örneği](docs/frontend-integration.md#oturum-ve-csrf).

## Belgeler

Tüm rehberler [doküman dizininde](docs/README.md) bulunur.

| İhtiyaç | Rehber |
|---|---|
| Oturum ve ekran akışları | [Frontend entegrasyonu](docs/frontend-integration.md) |
| Endpoint ve doğrulamalar | [API sözleşmesi](docs/api-reference.md) |
| Env, IDE, sorun giderme | [Yapılandırma](docs/configuration.md) |
| Format ve dosya kuralları | [Payload](docs/payload-content.md) |
| Veri modeli, üretim sınırları | [Mimari](docs/architecture.md) |

## Testler

Veritabanı gerektiren uygulama açılış testini hariç tutarak:

```sh
./mvnw -B '-Dtest=*Tests,!IntegrationCatalogApplicationTests' test
```

`./mvnw test` ayrıca PostgreSQL, şifreleme anahtarı ve boş kullanıcı tablosunda
ilk yönetici ayarlarını gerektirir. [Opt-in DB testleri](docs/payload-content.md#veritabanı-geçişi-ve-test)
ayrıca etkinleştirilir. Docker image build'i test çalıştırmaz.

## Paylaşım kontrolleri ve CI

```sh
python3 scripts/check_repository.py
```

Kontrol, takip edilen dosya adlarını ve yerel Markdown bağlantılarını denetler.
[CI](.github/workflows/ci.yml), Java 21/PostgreSQL 16 ile tüm testleri çalıştırır ve JAR paketler.
[Dependabot](.github/dependabot.yml) haftalık bağımlılık kontrolü yapar.

## Geliştirme durumu

MVP/pilot aşamasındadır; [üretim kabulü](docs/architecture.md#yayın-ve-üretim-sınırları)
tamamlanmamıştır. Lisans henüz seçilmemiştir.
