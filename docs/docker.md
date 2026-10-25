# Docker Compose ile çalıştırma

Kubernetes gerekmez. Compose, PostgreSQL ve Java uygulamasını aynı ağda çalıştırır.
Uygulama container'ı veritabanına `postgres:5432` adresinden bağlanır.

## İlk kurulum

1. Docker Desktop / Docker Engine'i çalıştırın.
2. `.env.example` dosyasını `.env` olarak kopyalayın; boş değerleri doldurun.
3. İlk kullanıcı için `CATALOG_ADMIN_USERNAME` ve 12–128 karakterlik
   `CATALOG_ADMIN_PASSWORD` belirleyin.
4. `CATALOG_CREDENTIALS_KEY` için bir kez `openssl rand -base64 32` ile anahtar
   üretin; aynı anahtarı sonraki çalıştırmalarda koruyun.
5. `POSTGRES_PASSWORD` belirleyin. Daha önce oluşturulmuş volume varsa mevcut
   veritabanı parolasını kullanın; environment değerini değiştirmek mevcut PostgreSQL
   kullanıcısının parolasını değiştirmez. Aynı veritabanıyla IDE'den çalıştırırken
   `SPRING_DATASOURCE_PASSWORD` değerini de buna göre tanımlayın.

`.env` Git'e ve Docker build context'ine dahil edilmez. Anahtar ve parolalar image'a
yazılmaz; container'a runtime environment olarak verilir. Docker yönetim yetkisi
olan kişiler container environment'ına erişebilir; sunucuda yönetilen secret deposu
kullanılabilir. `.env` dosyasını paylaşmayın ve yerel erişimini sınırlayın.

```sh
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
curl -i http://localhost:8080/actuator/health/readiness
```

Yerel davet ve parola e-postaları için `docker compose --profile mail up --build -d`
kullanın. Mailpit kutusu `http://localhost:8025` adresindedir; mesajlar gerçek
alıcılara gönderilmez. Gerçek SMTP sunucusu kullanırken mail profili gerekmez.
SMTP ve frontend bağlantısı ayarları: [kullanıcı yönetimi](user-management.md).
Tüm ortam değişkenleri ve IDE'den çalıştırma farkları:
[yapılandırma ve işletim](configuration.md). Genel başlangıç noktası:
[backend doküman dizini](README.md).

İlk build bağımlılıkları ve base image'ları indirir. Varsayılan portlar 8080 ve 5432;
yalnızca yerel makineye açılır. `.env` ile APP_PORT / POSTGRES_PORT değiştirilebilir.
Uygulama image'ı Java 21 ile çok aşamalı build kullanır, çalışma aşamasında JRE ve
uygulama JAR'ı bulunur; root olmayan kullanıcıyla çalışır. Build testleri çalıştırmaz;
testler image oluşturmadan önce geliştirme/CI adımında çalıştırılmalıdır.

Eski açık credential kayıtları varsa [dönüşüm adımlarını](authentication.md) izleyin.
Mevcut DB'yi payload/dosya desteğine yükseltmeden önce
[SQL migration'ını](migrations/20261025-payload-content.sql) uygulayın; özellikle
EXCEL formatı için eski CHECK kısıtları genişletilmelidir. Yeni/boş DB kurulumunu
mevcut DB yükseltmesiyle aynı işlem olarak kabul etmeyin.
Bir defalık dönüşüm için `.env` içinde `CATALOG_CREDENTIALS_MIGRATE=true` tanımlayın,
başarılı başlatmadan sonra false yapın ve `docker compose up -d` ile yeniden uygulayın.
Anahtarı kaldırmayın. İlk admin oluşturulduktan sonra admin bootstrap değişkenleri
boşaltılabilir; bu mevcut hesabın parolasını değiştirmez.

## Sağlık ve yeniden başlatma akışı

1. PostgreSQL başlar, `pg_isready` kontrolü başarılı olur.
2. Compose uygulamayı başlatır (`depends_on: service_healthy`).
3. Dockerfile HEALTHCHECK, her 15 saniyede readiness endpoint'ini kontrol eder.
   İlk 60 saniye başlangıç toleransı vardır; üç başarısız denemede container
   `unhealthy` olur. Her kontrol en fazla 8 saniye bekler; DB bağlantı havuzu
   bağlantı bekleme sınırı 5 saniyedir.
4. Uygulama süreci kapanırsa `restart: unless-stopped` yeniden başlatır.
   Elle durdurulan servis kendiliğinden yeniden başlatılmaz.

**Compose, yalnızca unhealthy olduğu için çalışan container'ı yeniden başlatmaz
ve published port'a gelen trafiği otomatik kesmez.** Readiness sağlık durumunu
gösterir; DB kesintisi liveness'ı bozmaz. Askıda kalan ama kapanmayan Java sürecini
otomatik kurtaran watchdog veya health'e göre trafik yöneten proxy bu kurulumda yoktur.
Docker socket'ini başka bir izleme container'ına açan ek servis eklenmedi.

```sh
docker compose logs --tail=100 app
docker compose restart app
docker compose down
```

`docker compose down` veritabanı volume'unu korur. `down -v` veri siler; normal
durdurma için kullanmayın. Mevcut `postgres_data` volume adı korunmuştur; eski
verileri kullanmak için aynı Compose proje adı ve dizinini kullanın. IDE'de çalışan
uygulamayı 8080 portunu kullanmadan önce durdurun veya APP_PORT'u değiştirin.

Bu yerel kurulum HTTP kullanır. HTTPS reverse proxy ile sunucuya taşırken
`SESSION_COOKIE_SECURE=true` tanımlayın ve erişim/port ayarlarını dağıtıma göre düzenleyin.
Docker daemon çalışmayan ortamda Compose yapılandırması ve JAR paketi doğrulanabilir;
image build, health geçişleri ve container'ların birlikte çalışması Docker üzerinde
ayrıca doğrulanmalıdır.

Referanslar: [Compose başlangıç sırası](https://docs.docker.com/compose/how-tos/startup-order/)
ve [Dockerfile HEALTHCHECK](https://docs.docker.com/reference/dockerfile/#healthcheck).
