# Film Access

Film Access; film keşfetme, kişisel arşiv oluşturma ve yapay zekâ destekli öneriler alma özelliklerini bir araya getiren Spring Boot tabanlı bir web uygulamasıdır. Film bilgileri OMDb üzerinden alınır; arşiv ve katalog aramalarında PostgreSQL, `pgvector` ve metin benzerliği özellikleri kullanılır.

## Özellikler

- E-posta ve parola ile kayıt/giriş
- OMDb üzerinde film arama ve film detaylarını görüntüleme
- Kişisel film arşivine ekleme, silme, filtreleme, sıralama ve sayfalama
- Vektör, bulanık metin ve metadata tabanlı hibrit arama
- İzleme geçmişine göre kişiselleştirilmiş ana sayfa önerileri
- Gemini destekli doğal dil film asistanı Mio
- İsteğe bağlı Redis sorgu embedding önbelleği
- Yönetici paneli ve kullanıcı/arşiv verilerini XLSX olarak dışa aktarma
- Flyway ile sürümlü veritabanı şeması
- Spring Security, rol bazlı yetkilendirme ve CSRF koruması

## Teknolojiler

- Java 21
- Spring Boot 4.1.0 ve Spring AI 2.0.0
- Spring MVC, Thymeleaf, Spring Security ve Spring Data JPA
- PostgreSQL 17, pgvector, Flyway
- Redis 7.4 (isteğe bağlı)
- Testcontainers, JUnit 5, AssertJ ve Mockito
- Docker Compose ve GitHub Actions

## Gereksinimler

- JDK 21
- Docker Desktop veya Docker Engine + Compose
- OMDb, OpenAI ve Google Gemini API anahtarları

Maven'ın ayrıca kurulması gerekmez; projedeki Maven Wrapper kullanılır.

## Kurulum

1. Depoyu klonlayın ve proje dizinine geçin.
2. Örnek ortam dosyasını kopyalayın:

   ```powershell
   Copy-Item .env.example .env
   ```

   Linux/macOS:

   ```bash
   cp .env.example .env
   ```

3. `.env` içindeki API anahtarlarını ve PostgreSQL bilgilerini kendi ortamınıza göre doldurun. `DATABASE_URL` içindeki veritabanı adı `POSTGRES_DB` ile aynı olmalıdır.
4. pgvector destekli PostgreSQL'i başlatın:

   ```bash
   docker compose up -d postgres
   ```

5. Uygulamayı çalıştırın.

   Windows:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Linux/macOS:

   ```bash
   ./mvnw spring-boot:run
   ```

Uygulama varsayılan olarak [http://localhost:8080](http://localhost:8080) adresinde açılır. İlk çalıştırmada Flyway veritabanını otomatik olarak günceller.

### Redis önbelleği

Redis zorunlu değildir. Sorgu embedding önbelleğini etkinleştirmek için:

```bash
docker compose --profile cache up -d redis
```

Ardından `.env` içinde `REDIS_QUERY_CACHE_ENABLED=true` kullanın. Varsayılan bağlantı adresi `redis://127.0.0.1:6380`'dır.

## Ortam değişkenleri

| Değişken | Zorunlu | Açıklama |
| --- | --- | --- |
| `OMDB_API_KEY` | Evet | Film arama ve detay bilgileri için OMDb anahtarı |
| `OPENAI_API_KEY` | Evet | Film ve sorgu embedding'leri için OpenAI anahtarı |
| `GEMINI_API_KEY` | Evet | Mio doğal dil yorumlama servisi için Gemini anahtarı |
| `POSTGRES_DB` | Evet | Docker Compose PostgreSQL veritabanı adı |
| `POSTGRES_USER` | Evet | Docker Compose PostgreSQL kullanıcısı |
| `POSTGRES_PASSWORD` | Evet | Docker Compose PostgreSQL parolası |
| `DATABASE_URL` | Evet | Uygulamanın JDBC PostgreSQL bağlantı adresi |
| `REDIS_QUERY_CACHE_ENABLED` | Hayır | İkinci seviye Redis önbelleğini açar; varsayılan `false` |
| `REDIS_URL` | Hayır | Redis bağlantı adresi |
| `SEMANTIC_BACKFILL_ON_STARTUP` | Hayır | Eksik embedding'leri başlangıçta tamamlar |
| `CATALOG_REFRESH_ON_STARTUP` | Hayır | Mevcut katalog metadata'sını başlangıçta yeniler |
| `CATALOG_BOOTSTRAP_ENABLED` | Hayır | Mio boş sonuç verdiğinde katalog yüklemesini etkinleştirir |

Diğer katalog yükleme seçenekleri için [film-catalog-bootstrap.md](film-catalog-bootstrap.md) dosyasına bakın. `.env` dosyasını veya gerçek API anahtarlarını Git'e göndermeyin.

## Testler

Entegrasyon testlerinin bir bölümü Testcontainers ile geçici PostgreSQL/pgvector konteyneri açar; bu nedenle Docker çalışıyor olmalıdır.

Tüm doğrulama paketini çalıştırmak için:

```powershell
.\mvnw.cmd -B clean verify
```

Linux/macOS:

```bash
./mvnw -B clean verify
```

Tek bir test sınıfını çalıştırma örneği:

```powershell
.\mvnw.cmd -B "-Dtest=UserFilmRepositoryTest" test
```

## CI

`.github/workflows/maven.yml`, `main` dalına gönderilen commit'lerde ve bu dala açılan pull request'lerde çalışır. Akış:

1. Depoyu checkout eder.
2. Temurin JDK 21'i ve Maven önbelleğini hazırlar.
3. Maven Wrapper ile `clean verify` çalıştırarak derleme ve testleri tek adımda doğrular.

Testcontainers, GitHub'ın Ubuntu runner'ındaki Docker servisini otomatik kullanır; ayrıca kalıcı test veritabanı veya CI secret'ı gerekmez.

## Proje yapısı

```text
src/main/java/com/example/
├── admin/       Yönetici paneli ve Excel dışa aktarma
├── archive/     Kişisel arşiv, filtreleme ve hibrit arama
├── buddy/       Mio öneri akışı
├── film/        Yerel film kataloğu ve bakım işleri
├── home/        Ana sayfa önerileri
├── omdb/        OMDb istemcisi ve film detayları
├── search/      Embedding ve semantik arama servisleri
└── user/        Kayıt, kimlik doğrulama, profil ve ayarlar

src/main/resources/
├── db/migration/  Flyway migration'ları
├── static/        CSS ve JavaScript dosyaları
└── templates/     Thymeleaf şablonları
```

## Güvenlik

- `/register`, `/login` ve statik dosyalar dışındaki uçlar kimlik doğrulama ister.
- `/admin/**` yalnızca `ADMIN` rolüne açıktır.
- Durum değiştiren formlarda Spring Security CSRF koruması aktiftir.
- Parolalar uygulama katmanında hash'lenerek saklanır.
- Frame kullanımına karşı `X-Frame-Options: DENY` uygulanır.

Üretim ortamında veritabanı bilgilerini ve API anahtarlarını yalnızca güvenli secret/env yönetimi üzerinden sağlayın.
