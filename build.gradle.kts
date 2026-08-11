import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    idea

    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")

    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.flywaydb.flyway")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://plugins.gradle.org/m2/")
    maven("https://kotlin.bintray.com/kotlinx")

    maven("https://repo.spring.io/snapshot")
    maven("https://repo.spring.io/milestone")
    maven("https://repo.spring.io/plugins-snapshot")
    maven("https://repo.spring.io/plugins-release")
}

tasks.withType<Test> {
    environment("SPRING.PROFILES.ACTIVE", "test")
    useJUnitPlatform()
    // Forward the optional real-data validation file to the forked test JVM (RecruitmentNormalizerRealDataTest).
    // Unset in CI, so the guarded test simply skips.
    System.getProperty("normalizer.realdata.file")?.let { systemProperty("normalizer.realdata.file", it) }
}

tasks.withType<BootJar> {
    archiveFileName.set("carp-platform.jar")
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

allprojects {
    group = "dk.cachet"
    version = "2.5.0"
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_4)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // BOM
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}"))
//    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}"))

    // KOTLIN
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serializationJSONVersion")}")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // kotlinx-coroutines version is managed by the Spring Boot BOM (kotlin-coroutines.version)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${property("kotlinDatetimeVersion")}")

    // JACKSON
    implementation("tools.jackson.module:jackson-module-kotlin")

    // CARP CORE
    implementation("dk.cachet.carp.common:carp.common-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.protocols:carp.protocols.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.deployments:carp.deployments.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.studies:carp.studies.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.data:carp.data.core-jvm:${property("carpCoreVersion")}")

    // SPRING STARTERS
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") {
        exclude(module = "org.apache.tomcat:tomcat-jdbc")
    }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-freemarker")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // SECURITY
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-config")
    implementation("org.springframework.security:spring-security-taglibs")
    implementation("org.springframework.security:spring-security-core")
    implementation("com.c4-soft.springaddons:spring-addons-starter-oidc:${property("springAddonsVersion")}")

    // SPRING CLOUD
//    implementation("org.springframework.cloud:spring-cloud-starter-config")
//    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap:${property("springCloudStarterVersion")}")

    // SPRINGDOC
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${property("springdocVersion")}")

    // RSQL
    implementation("cz.jirutka.rsql:rsql-parser:${property("rsqlParserVersion")}")

    // COMMONS-IO
    implementation("commons-io:commons-io:${property("commonsIOVersion")}")

    // HIBERNATE

    implementation("io.hypersistence:hypersistence-utils-hibernate-73:${property("hibernateTypesVersion")}")

    // POSTGRESQL
    runtimeOnly("org.postgresql:postgresql")

    // FLYWAY
    // spring-boot-starter-flyway is required on Spring Boot 4: FlywayAutoConfiguration moved out of
    // spring-boot-autoconfigure into the dedicated spring-boot-flyway module, so flyway-core alone no
    // longer wires up on-boot migration. The starter pulls spring-boot-flyway + flyway-core (BOM-managed).
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:${property("flywayVersion")}")

    // S3
    implementation("software.amazon.awssdk:s3:${property("awsSDKVersion")}")

    // MICROMETER
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // GOOGLE Core Libraries
    implementation("com.google.guava:guava:${property("guavaVersion")}")

    // Webflux
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // GSON Library
    implementation("com.google.code.gson:gson:${property("gsonVersion")}")

    // Apache Commons Compress
    implementation("org.apache.commons:commons-compress:${property("commonsCompressVersion")}")

    // Unit Test
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit5"))
    testImplementation(kotlin("test-common"))
    testImplementation(kotlin("test-annotations-common"))
    // kotlinx-coroutines version is managed by the Spring Boot BOM (kotlin-coroutines.version)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

    testImplementation("com.c4-soft.springaddons:spring-addons-starter-oidc-test:${property("springAddonsVersion")}")
    testImplementation("com.ninja-squad:springmockk:${property("springMockkVersion")}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${property("okhttpVersion")}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("org.junit.vintage", "junit-vintage-engine")
        exclude("org.mockito", "mockito-core")
    }

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

detekt {
    autoCorrect = true
    allRules = false
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:${property("detektVersion")}")
        detektPlugins("dk.cachet.detekt.extensions:detekt-verify-implementation:1.2.8")
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    config.from(files("$rootDir/detekt.yml"))
    ignoreFailures = false
    buildUponDefaultConfig = true
}

configurations.matching { it.name.startsWith("detekt") || it.name.startsWith("ktlint") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
        }
    }
}

configure<KtlintExtension> {
    ignoreFailures.set(true)
    additionalEditorconfig.set(
        mapOf(
            "ktlint_standard_no-wildcard-imports" to "disabled",
            "max_line_length" to "120",
        ),
    )
}
