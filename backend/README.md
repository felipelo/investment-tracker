# Investment Tracker — Backend

HTTP API and persistence layer for the [Investment Tracker](../README.md) project. This service will eventually back the local-first Canadian investment and Smith Maneuver tracker described in [REQUIREMENTS.md](../REQUIREMENTS.md).

Functional scope is intentionally **not** defined here yet. This document captures technology choices and the direction for local development, packaging, and deployment.

---

## Technology stack

| Layer | Choice | Notes |
|-------|--------|-------|
| Language | **Java 25** | LTS-aligned toolchain; pin exact JDK in build and Docker |
| Framework | **Spring Boot** | Web layer, dependency injection, configuration, data access |
| Build | **Maven** | Standard layout; reproducible builds for CI and native image |
| Database | **PostgreSQL** | Primary persistent store; version TBD |
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

The `native` profile is supplied by `spring-boot-starter-parent`; no extra plugin config is needed in `pom.xml`.

### Build the images

```bash
# JVM image
docker build -t investment-tracker:jvm .

# Native image (GraalVM AOT — expect several minutes and high RAM)
docker build -f Dockerfile.native -t investment-tracker:native .
```

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

Requires a local GraalVM 25 with `native-image` on `PATH`:

```bash
./mvnw -Pnative native:compile
./target/investment-tracker
```

Tradeoffs (native compile time, reflection/resource hints, JDBC driver compatibility) will be recorded here after the first real native build against actual code.

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
| Docker / native image | `Dockerfile` + `Dockerfile.native` ready |

Next step when ready: add a migration tool (Flyway/Liquibase) and the first health/readiness endpoint, then validate a real native build.
