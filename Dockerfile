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

# Ensure schema exists before starting the app (init.sql uses IF NOT EXISTS).
# Fail fast if psql fails so the container does not start with incomplete schema.
CMD sh -c 'set -e; echo "Ensuring DB schema..."; DB_HOST="${POSTGRES_HOST:-db}"; DB_PORT="${POSTGRES_PORT:-5432}"; DB_USER="${POSTGRES_USER:-postgres}"; DB_NAME="${POSTGRES_DB:-nikke_unionraid}"; i=0; while [ $i -lt 30 ]; do PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" -q >/dev/null 2>&1 && break; i=$((i+1)); echo "Waiting for database... ($i/30)"; sleep 1; done; PGPASSWORD="${POSTGRES_PASSWORD}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f /app/init.sql -v ON_ERROR_STOP=1; echo "Schema ensure completed successfully"; exec java -jar nikke-unionraid-bot.jar'