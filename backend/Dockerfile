# JVM image: standard fat-jar build. Fast to build, larger runtime than native.
#
# The build context is the repository root, because the image bundles the SPA:
#   docker build -f backend/Dockerfile -t investment-tracker:jvm .

# --- Frontend stage ---
FROM node:24 AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy wrapper + pom first so dependency resolution is cached across source changes.
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY backend/src/ src/
# Serving the bundle from the API's own origin is what lets the SPA keep its relative
# /api/v1 base URL: no CORS, no second host, one credential prompt.
COPY --from=web /web/dist/ src/main/resources/static/
RUN ./mvnw -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
# LocalDate.now() / Year.now() decide the dashboard's "today" and the default tax year, so the
# JVM's zone has to be the one the records are kept in rather than the container default (UTC).
ENV TZ=America/Toronto
COPY --from=build /workspace/target/*.jar app.jar
USER 1000:1000
EXPOSE 8080
# No HEALTHCHECK: this base image ships no HTTP client and adding one just to probe
# /actuator/health/readiness is not worth the layer. Point the orchestrator's HTTP probe at it.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
