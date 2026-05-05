plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.devtools.ksp") version "2.3.7"
    id("org.komapper.gradle") version "6.1.0"
}

group = "gg.nikke.raid.bot"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Database
    implementation("org.komapper:komapper-spring-boot-starter-jdbc:6.1.0")
    implementation("org.komapper:komapper-dialect-postgresql-jdbc:6.1.0")
    implementation("org.postgresql:postgresql:42.7.10")
    implementation("com.zaxxer:HikariCP:7.0.2")

    // KSP for Komapper code generation
    ksp("org.komapper:komapper-processor:6.1.0")

    implementation("net.dv8tion:JDA:6.4.1") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("nikke-unionraid-bot.jar")
}
