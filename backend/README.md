# Investment Tracker — Backend

HTTP API and persistence layer for the [Investment Tracker](../README.md) project. This service will eventually back the local-first Canadian investment and Smith Maneuver tracker described in [REQUIREMENTS.md](../REQUIREMENTS.md).

Functional scope is intentionally **not** defined here yet. This document captures technology choices and the direction for local development, packaging, and deployment.

---

## Technology stack

| Layer | Choice | Notes |
|-------|--------|-------|
| Language | **Java 25** | `java.version` 25; native builds require GraalVM 25 |
| Framework | **Spring Boot 4.1** | Spring Framework 7, Jackson 3, Hibernate 7.4 |
| Build | **Maven** | Wrapper committed; same commands locally and in CI |
| Database | **PostgreSQL 17** | Schema managed by Liquibase; auditing via Hibernate Envers |
| API docs | **springdoc-openapi 3** | `/v3/api-docs`, `/swagger-ui.html` |
| Packaging | **Docker** | Multi-stage build targeting a **GraalVM native image** for a small, fast-starting runtime |

---

## Goals

- **Local development** — run the app against a local Postgres instance with minimal setup.
- **Reproducible builds** — same Maven commands locally and in CI.
- **Native image** — produce a container image from a Spring Boot native build (not a JVM-only fat JAR in production), with acceptable build-time vs. runtime tradeoffs documented once we try it.
- **Clear boundaries** — API, domain, and persistence separated enough to evolve without rewriting; details left for when requirements land.

---

## Planned layout (conventional)

Structure will follow standard Spring Boot + Maven conventions. Exact package names and modules can be decided when the first code is scaffolded.

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/          # Application entrypoint, API, domain, infrastructure
│   │   └── resources/     # application.yml, migrations, static config
│   └── test/
├── docker/                # Dockerfile, optional compose for local Postgres
└── README.md
```

---

## Local development (to be wired up)

Intended workflow once the project is bootstrapped:

1. Start Postgres (local install or `docker compose`).
2. Configure connection via environment variables or `application-local.yml`.
3. Run with Maven: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.
4. Apply schema changes via a migration tool (candidate: **Flyway** or **Liquibase** — not chosen yet).

---

## Native image & Docker

Two multi-stage Dockerfiles are provided. Both build with the committed Maven wrapper, so no host Maven/JDK is required.

| File | Output | Build speed | Image size | Startup |
|------|--------|-------------|------------|---------|
| `Dockerfile` | JVM fat-jar on `eclipse-temurin:25-jre` | fast | larger | normal |
| `Dockerfile.native` | GraalVM native binary on `distroless/base` | slow, memory-hungry | tiny | very fast |

The `native` profile is supplied by `spring-boot-starter-parent`, so it only needs the `native-maven-plugin` build args in `pom.xml`, not the full plugin setup.

### Build the images

```bash
# JVM image
docker build -t investment-tracker:jvm .

# Native image (GraalVM AOT — expect several minutes and high RAM)
docker build -f Dockerfile.native -t investment-tracker:native .
```

Both need to pull base images from `ghcr.io`, `gcr.io`, and Docker Hub. On a restricted network the
build hangs on the pull; use the local GraalVM build below instead.

### Run a built image against local Postgres

```bash
docker compose up -d postgres            # start the database
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e POSTGRES_HOST=host.docker.internal \
  investment-tracker:jvm
```

### Or build + run everything via compose

```bash
docker compose --profile full up --build   # app (JVM image) + Postgres
docker compose up                          # Postgres only (default)
```

### Native build locally (without Docker)

Requires GraalVM 25 — see [docs/MACHINE-SETUP.md](../docs/MACHINE-SETUP.md).

```bash
export JAVA_HOME=~/.local/graalvm/graalvm-community-openjdk-25.0.2+10.1/Contents/Home
./mvnw -Pnative native:compile -DskipTests
SPRING_PROFILES_ACTIVE=local ./target/investment-tracker
```

### Smoke test a native binary

Migrations, JSON, and the Envers audit trail are the parts most likely to break under AOT, so exercise
all three. Against a throwaway database (leaves your dev data alone):

```bash
docker run --rm -d --name it-smoke -p 5433:5432 \
  -e POSTGRES_DB=investment_tracker -e POSTGRES_USER=investment_tracker \
  -e POSTGRES_PASSWORD=investment_tracker postgres:17

SPRING_PROFILES_ACTIVE=local POSTGRES_PORT=5433 ./target/investment-tracker &

curl -s localhost:8080/api/v1/securities                    # Liquibase seed data
curl -s -X POST localhost:8080/api/v1/security-transactions -H 'Content-Type: application/json' \
  -d '{"date":"2026-02-02","securityId":1,"accountId":1,"action":"BUY","shares":10,"pricePerShare":25.50,"commission":4.95}'
docker exec it-smoke psql -tAU investment_tracker -d investment_tracker \
  -c "select (select count(*) from revinfo), (select count(*) from security_transaction_aud)"
```

The last query must return non-zero counts — that is the proof Envers still audits in the native image.

### Recorded tradeoffs

Measured on an Apple Silicon Mac with GraalVM CE 25.0.2:

| | JVM | Native |
|---|---|---|
| Build | ~17 s (`./mvnw verify`, 105 tests) | ~2 min 20 s compile, peak ~7.6 GB RSS |
| Artifact | ~60 MB fat jar + JRE base image | 211 MB binary, distroless runtime image |
| Startup | seconds | **0.9 s** |

Two AOT constraints are baked into the build:

- **No runtime bytecode provider.** Native images disable Hibernate's ByteBuddy provider, so lazy
  `@ManyToOne` associations and `getReferenceById()` cannot create proxies at runtime. The
  `hibernate-maven-plugin` enhances the entities at build time instead. The `test` profile sets
  `hibernate.bytecode.provider: none` so `./mvnw verify` fails on the JVM if new code reintroduces a
  runtime-proxy dependency.
- **Liquibase reflection.** The GraalVM metadata repository does not cover the accessors Liquibase
  reflects on while parsing changesets, so the build passes `-H:Preserve=package=liquibase.*`. It
  costs ~14 MB of image size and can be dropped once upstream metadata covers Liquibase 5.x.

Envers, springdoc, Jackson 3, and the PostgreSQL driver needed no hints of their own.

---

## Out of scope for this README

- REST resource design, DTOs, and business rules (see [REQUIREMENTS.md](../REQUIREMENTS.md) when ready).
- Frontend integration and auth flows.
- Production hosting, backups, and multi-user tenancy.

---

## What else can be specified now

These decisions do not require feature requirements and are worth locking in early:

| Area | Questions to answer |
|------|---------------------|
| **Spring Boot version** | Which 3.x line aligns with Java 25 and native-image support at scaffold time? |
| **JDK distribution** | Temurin, GraalVM, or other for dev vs. native build image? |
| **Maven wrapper** | Commit `mvnw` / `.mvn` for consistent builds? |
| **Postgres version** | e.g. 16 vs 17 for local and CI |
| **Schema migrations** | Flyway vs Liquibase; naming and location under `resources/db/migration` |
| **API style** | REST (OpenAPI) vs alternatives; versioning prefix (`/api/v1`) |
| **Configuration** | `application.yml` profiles (`local`, `test`, `prod`); secrets via env only |
| **Local Postgres** | `docker-compose.yml` in `backend/` or repo root; default ports and credentials |
| **Testing** | JUnit 5, Testcontainers for Postgres in integration tests |
| **Code quality** | Checkstyle/Spotless, minimum Java version in `pom.xml` |
| **CI** | GitHub Actions (or other): `mvn verify`, optional native build on main/tags |
| **Observability** | Structured logging (JSON), health/readiness (`/actuator/health`), metrics later |
| **Auth model** | Single-user local app vs. future multi-user — affects security scaffold even before features |
| **Docker naming** | Image name, tags, registry; whether native build runs in CI or release-only |
| **Monorepo boundaries** | Backend-only repo folder vs. shared OpenAPI/contracts with a future frontend |

---

## Status

| Item | State |
|------|--------|
| README | Draft |
| Maven project | Scaffolded (wrapper committed) |
| Spring Boot app | Minimal (entrypoint only, no endpoints) |
| Postgres / migrations | Compose + datasource config; no schema yet |
| Docker / native image | `Dockerfile` + `Dockerfile.native` ready; native binary built and smoke-tested locally (container build unverified — registries unreachable here) |

Next step when ready: add the first health/readiness endpoint.
