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
    version = "2.2.0"
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // BOM
    implementation(platform("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}"))
//    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}"))

    // KOTLIN
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${property("kotlinVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${property("serializationJSONVersion")}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("kotlinCoroutinesVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${property("kotlinCoroutinesVersion")}")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:${property("kotlinDatetimeVersion")}")

    // JACKSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${property("jacksonVersion")}")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${property("jacksonVersion")}")

    // CARP CORE
    implementation("dk.cachet.carp.common:carp.common-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.protocols:carp.protocols.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.deployments:carp.deployments.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.studies:carp.studies.core-jvm:${property("carpCoreVersion")}")
    implementation("dk.cachet.carp.data:carp.data.core-jvm:${property("carpCoreVersion")}")

    // SPRING STARTERS
    implementation("org.springframework.boot:spring-boot-maven-plugin:${property("springBootVersion")}")
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
    implementation("org.hibernate:hibernate-core:${property("hibernateVersion")}")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:${property("hibernateTypesVersion")}")

    // POSTGRESQL
    runtimeOnly("org.postgresql:postgresql")

    // FLYWAY
    implementation("org.flywaydb:flyway-core:${property("flywayVersion")}")
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
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("kotlinCoroutinesVersion")}")

    testImplementation("com.c4-soft.springaddons:spring-addons-starter-oidc-test:${property("springAddonsVersion")}")
    testImplementation("com.ninja-squad:springmockk:${property("springMockkVersion")}")
    testImplementation("com.squareup.okhttp3:mockwebserver:${property("okhttpVersion")}")
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
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    config.from(files("$rootDir/detekt.yml"))
    ignoreFailures = false
    buildUponDefaultConfig = true
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
