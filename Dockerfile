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

COPY --from=builder /build/build/libs/nikke-unionraid-bot.jar nikke-unionraid-bot.jar
COPY init.sql /app/init.sql

# Expose port
EXPOSE 8080

# Ensure schema exists (for cases where docker-entrypoint-initdb.d bind didn't apply, e.g. devcontainer),
# then start the app. init.sql uses IF NOT EXISTS so safe to run multiple times.
CMD sh -c 'echo "Ensuring DB schema..."; PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "${POSTGRES_HOST:-db}" -p "${POSTGRES_PORT:-5432}" -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-nikke_unionraid}" -f /app/init.sql -v ON_ERROR_STOP=1 2>&1 || echo "Schema ensure completed (or skipped)"; exec java -jar nikke-unionraid-bot.jar'