# Backend dokümanları

Bu belgeler repodaki mevcut uygulamanın davranışını açıklar. Swagger DTO ve
endpoint incelemek için, bu belgeler ise iş kuralları, güvenlik, ekran akışları ve
işletim için kullanılır. API veya iş kuralı değiştiğinde ilgili belge aynı değişiklikte güncellenmelidir.

| Belge | İçerik / hedef okuyucu |
|---|---|
| [Proje başlangıcı](../README.md) | Kapsam, hızlı kurulum, adresler ve test komutu |
| [Frontend entegrasyonu](frontend-integration.md) | Frontend geliştiricisi için ilk okuma; istek yardımcısı ve ekran akışları |
| [Ayrı frontend projesine devir](frontend-handoff.md) | Yerel proxy, ilk oturum, açık ürün kararları ve kabul kontrolleri |
| [Entegrasyon iş akışı](integration-workflow.md) | Örnek kayıt, şema matrisi, DRAFT/COMPLETED ve inceleme |
| [API sözleşmesi](api-reference.md) | Endpoint, HTTP durumu, veri biçimi ve hata sözleşmesi |
| [Payload içeriği ve örnek dosyalar](payload-content.md) | JSON/XML/IDOC alanları, CSV/Excel örnekleri, upload/download, migration |
| [Giriş, roller ve credential güvenliği](authentication.md) | Oturum, CSRF, yetkiler, şifreleme ve eski veri dönüşümü |
| [Kullanıcı yönetimi](user-management.md) | Davet, doğrulama, parola, e-posta ve posta kuyruğu |
| [Katalog yönetimi](catalog-management.md) | Source/destination/middleware güncelleme, aktiflik ve silme |
| [Yapılandırma ve işletim](configuration.md) | Ortam değişkenleri, IDE çalıştırma, timeout ve sorun giderme |
| [Docker](docker.md) | Compose, volume, Mailpit ve container yaşam döngüsü |
| [Health check](health-checks.md) | Liveness, readiness, erişim ve sağlık sınırları |
| [Mimari ve doğrulama](architecture.md) | Veri ilişkileri, transaction yaklaşımı ve mevcut sınırlar |
| [Yayın hazırlığı](release-readiness.md) | Public GitHub paylaşımı, yerel inceleme kapsamı ve üretim öncesi eksikler |

Frontend çalışmasına önce frontend rehberi, ardından entegrasyon iş akışı ve API
sözleşmesiyle başlayın. Kurulum için proje başlangıcı → Docker → yapılandırma
sırasını izleyin. Kullanıcı ekranları için kullanıcı yönetimi belgesini de okuyun.

## Kodla karşılaştırma noktaları

- Endpoint ve HTTP yanıtları: `src/main/java/com/company/integration_catalog/controller/`.
- Alan doğrulamaları: aynı paket kökündeki `dto/` ve controller içindeki request sınıfları.
- Tamamlama: `validation/IntegrationCompletionValidator.java`.
- İş kuralları: `service/`, `service/impl/`; yetkilendirme: `security/` ve `config/`.
- Çalışma ayarları: `src/main/resources/application.properties`, `docker-compose.yml`, `.env.example`, `Dockerfile`.

Örneklerdeki ID'ler temsilidir; kendi API yanıtınızdaki ID'leri kullanın.
Örnek secret ve parolalar üretim değeri değildir. Frontend önerileri uygulanmış
bir arayüz anlamına gelmez; backend'in sunduğu API üzerine kurulacak davranışı tarif eder.
