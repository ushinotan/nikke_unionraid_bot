# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

# Prepare Gradle wrapper
COPY kotlin/gradlew ./gradlew
COPY kotlin/gradle/wrapper/ gradle/wrapper/
RUN chmod +x ./gradlew

# Cache dependencies
COPY kotlin/build.gradle.kts kotlin/settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q || true

# Build application
COPY kotlin/src/ src/
RUN ./gradlew bootJar --no-daemon -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/build/libs/*-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "app.jar"]