import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


group = "no.nav.syfo"
version = "1.0.0"

val avroVersion = "1.8.2"
val confluentVersion = "5.0.0"
val coroutinesVersion = "1.3.4"
val jacksonVersion = "2.9.9"
val kafkaVersion = "2.0.0"
val kafkaEmbeddedVersion = "2.3.0"
val kluentVersion = "1.52"
val ktorVersion = "1.3.2"
val logstashEncoderVersion = "6.1"
val logbackVersion = "1.2.3"
val prometheusVersion = "0.8.0"
val smCommonVersion = "1.125b076"
val spekVersion = "2.0.9"
val syfoAvroSchemasVersion = "c8be932543e7356a34690ce7979d494c5d8516d8"
val testContainerKafkaVersion = "1.15.3"
val mockVersion = "1.9.3"

plugins {
    id("org.jmailen.kotlinter") version "2.2.0"
    kotlin("jvm") version "1.3.72"
    id("com.diffplug.gradle.spotless") version "3.18.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("org.sonarqube") version "2.8"
    jacoco
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion")
    implementation("org.apache.avro:avro:$avroVersion")

    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")
    implementation("no.nav.helse:syfosm-common-networking:$smCommonVersion")

    implementation("no.nav.syfo.schemas:syfosmoppgave-avro:$syfoAvroSchemasVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    testImplementation("io.mockk:mockk:$mockVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedVersion")
    testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")

    testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

}

sonarqube {
    properties {
        property("sonar.projectKey", "syfosmoppgave")
        property("sonar.organization", "navit")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.login", System.getenv("SONAR_TOKEN") )
    }
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = true
    }
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {

        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "12"
        kotlinOptions.freeCompilerArgs = listOf("-Xnormalize-constructor-calls=enable")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
    }
    withType<JacocoReport> {
        classDirectories.setFrom(
                sourceSets.main.get().output.asFileTree.matching {
                    exclude()
                }
        )

    }
    "check" {
        dependsOn("formatKotlin")
    }
}
