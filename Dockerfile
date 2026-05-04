FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Copy Spring Boot executable JAR
COPY kotlin/build/libs/*-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the Spring Boot application
CMD ["java", "-jar", "app.jar"]