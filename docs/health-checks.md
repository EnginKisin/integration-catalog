# Health check

Spring Boot Actuator yalnızca health endpoint'ini dışarı açar:

| Endpoint | Kontrol | Kullanım |
|---|---|---|
| `GET /actuator/health` | Genel sağlık | İzleme |
| `GET /actuator/health/liveness` | Uygulama yaşam durumu | Yeniden başlatma kararı |
| `GET /actuator/health/readiness` | Hazırlık durumu ve PostgreSQL bağlantısı | Trafik yönlendirme |

Üçü de giriş gerektirmeden çağrılır; bileşenler, bağlantı adresleri ve hata ayrıntıları
ADMIN dahil hiçbir kullanıcıya bu yanıtlarda gösterilmez. Diğer Actuator yolları kapalıdır.
Sağlıklı yanıtta `status=UP`, HTTP 200; DOWN veya OUT_OF_SERVICE durumunda HTTP 503 döner.

PostgreSQL bağlantısı kesilirse readiness başarısız olur, liveness bundan etkilenmez.
Veritabanı sorunu nedeniyle uygulamayı sürekli yeniden başlatmak yerine trafik durdurulabilir.
Readiness Spring Boot'un başlatma/kapatma hazırlık durumunu da dikkate alır. Şifreli
kayıt dönüşümü gibi başlangıç işlemleri bitmeden uygulama hazır kabul edilmez.

Yerel kontrol:

```sh
curl -i http://localhost:8080/actuator/health/liveness
curl -i http://localhost:8080/actuator/health/readiness
```

Bu endpoint'ler veritabanındaki her kaydın doğruluğunu veya dış entegrasyonların
çalıştığını garanti etmez. Docker Compose, uygulama container'ının readiness
endpoint'ini Dockerfile HEALTHCHECK ile takip eder. Başlatma ve sınırlar için
[Docker kurulumu](docker.md) belgesine bakın.
SMTP health kontrolü kapalıdır; posta kuyruğu ADMIN'in
`GET /api/v1/users/mail-status` endpoint'inden ayrıca izlenir.
Health kontrolü yetkilendirme/CSRF akışının uçtan uca çalıştığını sınamaz.

`HealthEndpointTests`, gerçek Actuator ve güvenlik filtreleriyle endpoint erişimini,
gizliliği, availability durumlarını ve simüle edilen veritabanı kesintisini doğrular.
Gerçek PostgreSQL bağlantı kesintisi bu testte kullanılmaz.

Referans: [Spring Boot Actuator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html).
