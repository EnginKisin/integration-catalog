# Backend dokümanları

[Proje README'si](../README.md) hızlı kurulum, adresler ve test komutlarının
başlangıç noktasıdır. Aşağıdaki belgeler konularına göre ayrılır; aynı sözleşmenin
ayrıntılarını başka dosyada tekrar etmek yerine ilgili belgeye bağlantı verilir.
Kod veya API değiştiğinde ilgili belge aynı değişiklikte güncellenmelidir.

| Belge | İçerik |
|---|---|
| [Yapılandırma ve işletim](configuration.md) | Docker, env değişiklikleri, IDE, health, volume ve sorun giderme |
| [Hata sözleşmesi ve katalog](error-codes.md) | İngilizce hata zarfı, kod/endpoint dizini, alan ihlalleri, requestId, 429 ve AI iş hataları |
| [API sözleşmesi](api-reference.md) | Bütün controller endpoint'leri, yanıtlar, enum'lar ve doğrulamalar |
| [Frontend entegrasyonu](frontend-integration.md) | Ayrı proje/proxy, oturum/CSRF yardımcısı, ekran akışları ve kabul kontrolleri |
| [Ortak sistem kataloğu](systems-frontend.md) | Sistem CRUD/aktiflik/kullanım, entegrasyon seçimleri, harita ve ortam filtresi |
| [Entegrasyon iş akışı](integration-workflow.md) | Oluşturma, config, şema matrisi, DRAFT/COMPLETED ve inceleme |
| [Payload ve örnek dosyalar](payload-content.md) | Formatlar, bağımsız hedef, upload/download, alan yolları ve DB testleri |
| [AI eşleme](ai-mapping.md) | Ollama/Gemini kurulumu, asenkron öneri API'si ve kullanıcı onayı |
| [Giriş ve credential güvenliği](authentication.md) | Roller, oturum, secret izinleri, şifreleme ve eski veri dönüşümü |
| [Kullanıcı yaşam döngüsü](user-management.md) | Davet, reset, e-posta doğrulama, SMTP ve outbox |
| [Mimari ve doğrulama](architecture.md) | Veri ilişkileri, transaction'lar, test kapsamı ve üretim sınırları |

Frontend geliştiricisi frontend rehberi → API dizini → ilgili alan sözleşmesi
sırasını izleyebilir. Kurulum/işletim için yapılandırma belgesi, kapsam ve yayın
kararları için mimari belgesi kullanılır. Bu repo frontend uygulaması içermez.

## Kodla karşılaştırma noktaları

- Endpoint ve HTTP yanıtları: `src/main/java/com/company/integration_catalog/controller/`.
- Alan doğrulamaları: aynı paket kökündeki `dto/` ve controller içindeki request sınıfları.
- Tamamlama: `validation/IntegrationCompletionValidator.java`.
- İş kuralları: `service/`, `service/impl/`; yetkilendirme: `security/` ve `config/`.
- Çalışma ayarları: `src/main/resources/application.properties`, `docker-compose.yml`, `.env.example`, `Dockerfile`.
- AI sözleşmesi: `ai/AiMappingDtos.java`, `ai/AiMappingService.java` ve sağlayıcı adaptörleri.

Örneklerdeki ID'ler temsilidir; kendi API yanıtınızdaki ID'leri kullanın.
Örnek secret ve parolalar üretim değeri değildir. Frontend önerileri uygulanmış
bir arayüz anlamına gelmez; backend'in sunduğu API üzerine kurulacak davranışı tarif eder.
