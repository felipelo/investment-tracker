# syntax=docker/dockerfile:1

# JVM image: standard fat-jar build. Fast to build, larger runtime than native.

# --- Build stage ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy wrapper + pom first so dependency resolution is cached across source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
