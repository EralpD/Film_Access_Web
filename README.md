# Film Access

Film Access is a Spring Boot web application for discovering films, building a personal archive, and receiving AI-assisted recommendations. It combines OMDb film data with a local PostgreSQL catalog, `pgvector` similarity search, fuzzy text matching, and a Gemini-powered natural-language assistant named Mio.

## Features

- Email and password registration and authentication
- OMDb search and detailed film pages
- A local film catalog with filters, sorting, and pagination
- Personal archives with add, remove, filter, sort, and pagination operations
- Hybrid search across titles, genres, actors, directors, spelling variants, and meaning
- Personalized home-page recommendations based on a user's saved films
- Mio, a Gemini-powered natural-language film assistant
- Optional Redis caching for query embeddings
- An administrator dashboard with XLSX user/archive export
- Versioned database migrations with Flyway
- Role-based authorization, CSRF protection, and password hashing

## Technology stack

- Java 21
- Spring Boot 4.1.0 and Spring AI 2.0.0
- Spring MVC, Thymeleaf, Spring Security, and Spring Data JPA
- PostgreSQL 17 with pgvector, `pg_trgm`, and `fuzzystrmatch`
- Redis 7.4 as an optional cache
- Flyway database migrations
- JUnit, AssertJ, Mockito, and Testcontainers
- Docker Compose and GitHub Actions

## Prerequisites

- JDK 21
- Docker Desktop, or Docker Engine with Docker Compose
- API keys for OMDb, OpenAI, and Google Gemini

You do not need a system-wide Maven installation. The repository includes Maven Wrapper scripts for Windows, Linux, and macOS.

## Quick start

1. Clone the repository and enter the project directory.
2. Create your local environment file from the committed template.

   Windows PowerShell:

   ```powershell
   Copy-Item .env.example .env
   ```

   Linux/macOS:

   ```bash
   cp .env.example .env
   ```

3. Replace the placeholder API keys and database password in `.env`.
4. Start the pgvector-enabled PostgreSQL service.

   ```bash
   docker compose up -d postgres
   ```

5. Run the application.

   Windows:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Linux/macOS:

   ```bash
   ./mvnw spring-boot:run
   ```

6. Open [http://localhost:8080](http://localhost:8080).

Flyway applies the database migrations automatically when the application starts. The database name in `DATABASE_URL` must match `POSTGRES_DB`.

## API key setup

All API keys are loaded server-side from `.env` through `spring.config.import`. They must never be placed in Java source files, templates, browser-side JavaScript, screenshots, or commits.

### OMDb

1. Request an API key from the [OMDb API key page](https://www.omdbapi.com/apikey.aspx).
2. Add it to `.env`:

   ```dotenv
   OMDB_API_KEY=your-real-omdb-api-key
   ```

The application uses this key for remote film searches and for retrieving complete film metadata before a title is stored in the local catalog or archive.

### OpenAI

1. Create an API key in the [OpenAI platform](https://platform.openai.com/api-keys).
2. Ensure the associated project has API access and sufficient usage quota.
3. Add the key to `.env`:

   ```dotenv
   OPENAI_API_KEY=your-real-openai-api-key
   ```

OpenAI is used only for embeddings. The configured model is `text-embedding-3-small` with 1,536 dimensions. It powers semantic archive/catalog search and film similarity features; it is not used for Mio's conversational interpretation.

### Google Gemini

1. Create or view a key in [Google AI Studio](https://aistudio.google.com/app/apikey). New deployments should use a key type supported by the current Gemini API requirements.
2. Restrict and protect the key according to the [official Gemini API key guidance](https://ai.google.dev/gemini-api/docs/api-key).
3. Add it to `.env`:

   ```dotenv
   GEMINI_API_KEY=your-real-gemini-api-key
   ```

Gemini is used by Mio to interpret natural-language film requests. The configured chat model is `gemini-3.5-flash-lite`. The application does not enable Google Search retrieval for these requests.

After changing any key, restart the Spring Boot process. If a key has ever been committed, pasted into a public issue, or shared in logs, revoke it and create a replacement; deleting it from the latest commit alone does not remove it from Git history.

## Environment variables

The committed [.env.example](.env.example) contains safe placeholders and working local defaults. Copy it to `.env`, then edit only the local copy.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `OMDB_API_KEY` | Yes | — | OMDb search and film metadata |
| `OPENAI_API_KEY` | Yes | — | Film and query embeddings |
| `GEMINI_API_KEY` | Yes | — | Mio natural-language interpretation |
| `POSTGRES_DB` | Yes | `film_archive` in the example | Database created by Docker Compose |
| `POSTGRES_USER` | Yes | `filmApp` in the example | PostgreSQL username used by Docker and Spring |
| `POSTGRES_PASSWORD` | Yes | — | PostgreSQL password used by Docker and Spring |
| `DATABASE_URL` | Yes | `jdbc:postgresql://localhost:5432/film_archive` | JDBC connection URL |
| `SEMANTIC_BACKFILL_ON_STARTUP` | No | `false` | Index missing or stale film embeddings at startup |
| `SEMANTIC_EMBEDDING_BATCH_SIZE` | No | `64` | Number of films sent in an embedding batch |
| `REDIS_QUERY_CACHE_ENABLED` | No | `false` | Enable the shared Redis query-embedding cache |
| `REDIS_URL` | No | `redis://127.0.0.1:6380` | Redis connection URL |
| `ARCHIVE_SEARCH_SPELLING_CANDIDATES` | No | `200` | Maximum fuzzy-search candidate pool |
| `ARCHIVE_SEARCH_REPAIR_ENABLED` | No | `true` | Repair missing embeddings for saved films in the background |
| `CATALOG_REFRESH_ON_STARTUP` | No | `false` | Refresh existing catalog metadata on startup |
| `CATALOG_METADATA_REFRESH_ENABLED` | No | `true` | Enable background metadata maintenance |
| `CATALOG_BOOTSTRAP_ENABLED` | No | `true` | Allow Mio's empty-result flow to trigger catalog import |

Spring configuration also accepts ordinary environment variables supplied by a hosting platform. In production, set these values with the platform's secret manager instead of uploading a `.env` file.

## Semantic and hybrid search

### How film indexing works

For each film, the application builds embedding input from the available title, year, type, genres, director, actors, and plot. OpenAI converts that text into a 1,536-dimensional vector. PostgreSQL stores the vector together with the embedding model name and a hash of the source content.

The hash allows the application to detect stale embeddings when searchable metadata changes. A stored vector is considered current only when its model and content hash match the active configuration.

The required PostgreSQL extensions and indexes are created through Flyway migrations. Use the supplied `pgvector/pgvector` Docker image; a plain PostgreSQL image does not include the required vector extension.

### How a search is evaluated

Archive and catalog search can combine several signals:

- Exact and partial title matches
- Actor and director matches
- Normalized genre matches
- PostgreSQL trigram and Levenshtein spelling similarity
- Cosine similarity between the query embedding and current film embeddings
- Explicit year and film-type filters

Lexical and semantic rankings are merged into a hybrid result set. A result can therefore be found by its exact metadata, an approximate spelling such as a mistyped title, or a descriptive query such as `slow science-fiction films about memory`.

The default semantic similarity threshold is `0.35`, the fuzzy threshold is `0.35`, and an embedding provider call has a four-second timeout. These values are configured in `src/main/resources/application.properties` and should be calibrated with representative production queries before being changed.

### Initial indexing and backfill

New or refreshed films are queued for embedding maintenance. To index existing films immediately on the next application start, temporarily set:

```dotenv
SEMANTIC_BACKFILL_ON_STARTUP=true
SEMANTIC_EMBEDDING_BATCH_SIZE=64
```

Start the application, wait for the backfill to finish, and then return `SEMANTIC_BACKFILL_ON_STARTUP` to `false` so every restart does not perform the same startup scan. Embedding requests consume OpenAI API quota.

Saved films can also be repaired by the background archive repair worker when `ARCHIVE_SEARCH_REPAIR_ENABLED=true`. The repair path updates saved films only; an archive search does not import unrelated catalog titles.

### Degraded behavior

Semantic search is designed to fail softly. If OpenAI is unavailable, times out, or rejects the key, the application continues with title, person, genre, and approximate-spelling matches and displays a notice that semantic results are unavailable or incomplete. Films without a current embedding remain eligible for non-semantic matches.

### Query embedding caches

Successful query embeddings are cached in memory for 15 minutes, with a default maximum of 512 entries. Concurrent requests for the same query share one provider call. Failed embeddings are not cached, and provider failures trigger a short cooldown.

Redis can be enabled as a best-effort shared second-level cache:

```bash
docker compose --profile cache up -d redis
```

Then update `.env`:

```dotenv
REDIS_QUERY_CACHE_ENABLED=true
REDIS_URL=redis://127.0.0.1:6380
```

The Redis cache stores only serialized query vectors with expiration metadata. It does not store film result lists, user records, credentials, or failed embeddings. If Redis becomes unavailable, search falls back to the in-process cache and continues operating.

## Mio and catalog bootstrap

Mio sends the user's natural-language request to Gemini and uses the interpreted intent to select local film candidates. If Mio returns an empty result and catalog bootstrap is enabled, the HTTP response is completed first and a background catalog import may start afterward.

The importer reads a local IMDb `title.basics.tsv.gz` dataset and ranks eligible films using IMDb ratings data. The `film-data` directory is intentionally excluded from Git. Dataset paths, limits, delays, retry behavior, and operational details are documented in [film-catalog-bootstrap.md](film-catalog-bootstrap.md).

## Running tests

Some integration tests start temporary PostgreSQL/pgvector and Redis containers through Testcontainers. Docker must be running.

Run the complete build and verification suite:

Windows:

```powershell
.\mvnw.cmd -B clean verify
```

Linux/macOS:

```bash
./mvnw -B clean verify
```

Run a single test class:

```powershell
.\mvnw.cmd -B "-Dtest=UserFilmRepositoryTest" test
```

Tests use temporary containers and mocked provider clients where appropriate. They do not require real OMDb, OpenAI, or Gemini keys.

## Continuous integration

The GitHub Actions workflow in `.github/workflows/maven.yml` runs for pushes to `main` and pull requests targeting `main`. It:

1. Checks out the repository.
2. Installs Temurin JDK 21 and restores the Maven dependency cache.
3. Makes the Unix Maven Wrapper executable.
4. Runs `./mvnw -B clean verify` once to compile, test, and package the application.

Testcontainers uses Docker on the GitHub-hosted Ubuntu runner. No persistent CI database or external-provider API secrets are required for the test suite.

## Project structure

```text
src/main/java/com/example/
├── admin/       Admin dashboard and Excel exports
├── archive/     Personal archive, filtering, and hybrid search
├── buddy/       Mio recommendation flow
├── film/        Local catalog and maintenance jobs
├── home/        Personalized home-page recommendations
├── omdb/        OMDb client and film details
├── search/      Embedding, semantic search, and caches
└── user/        Registration, authentication, profile, and settings

src/main/resources/
├── db/migration/  Flyway migrations
├── static/        CSS and JavaScript
└── templates/     Thymeleaf templates
```

## Security

- `/register`, `/login`, and static assets are public; other application routes require authentication.
- `/admin/**` requires the `ADMIN` role.
- Spring Security CSRF protection is active for state-changing form submissions.
- Passwords are hashed before persistence.
- Responses deny framing with `X-Frame-Options: DENY`.
- API keys remain server-side and `.env` is excluded by `.gitignore`.

For production, use unique database credentials, HTTPS, restricted provider keys, and your deployment platform's secret-management facility.

## Troubleshooting

- **The application cannot connect to PostgreSQL:** confirm that `docker compose ps` reports a healthy `postgres` service and that `DATABASE_URL`, `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` agree.
- **A migration reports that `vector` is unavailable:** use the supplied pgvector image instead of plain PostgreSQL.
- **Semantic results are missing:** verify `OPENAI_API_KEY`, provider quota, and whether the relevant films have current embeddings. Run a one-time startup backfill if needed.
- **Mio cannot interpret a request:** verify `GEMINI_API_KEY`, key restrictions, model access, and quota.
- **Remote film search fails:** verify `OMDB_API_KEY` and OMDb usage limits.
- **Redis warnings appear:** Redis is optional. Disable `REDIS_QUERY_CACHE_ENABLED` or start the cache profile; lexical and semantic search can continue without Redis.
- **Testcontainers cannot start:** start Docker and confirm the current user can access the Docker daemon.
