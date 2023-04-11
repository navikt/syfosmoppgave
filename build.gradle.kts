import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.4"
val jacksonVersion = "2.14.2"
val kafkaVersion = "3.4.0"
val kluentVersion = "1.68"
val ktorVersion = "2.2.4"
val logstashEncoderVersion = "7.3"
val logbackVersion = "1.4.4"
val prometheusVersion = "0.15.0"
val smCommonVersion = "1.9df1108"
val kotestVersion = "5.4.2"
val testContainerKafkaVersion = "1.18.0"
val mockVersion = "1.13.4"
val kotlinVersion = "1.8.20"

plugins {
    id("org.jmailen.kotlinter") version "3.10.0"
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

val githubUser: String by project
val githubPassword: String by project

subprojects {
    group = "no.nav.syfo"
    version = "1.0.0"
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "kotlin")
    apply(plugin = "com.github.johnrengelman.shadow")

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
            credentials {
                username = githubUser
                password = githubPassword
            }
        }
        maven(url = "https://packages.confluent.io/maven/")
    }
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
        implementation("io.prometheus:simpleclient_common:$prometheusVersion")

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

        implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

        implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
        implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

        testImplementation("io.mockk:mockk:$mockVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
        testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")
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
            kotlinOptions.jvmTarget = "17"
        }

        withType<Test> {
            useJUnitPlatform {
            }
            testLogging {
                events("skipped", "failed")
                showStackTraces = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

        "check" {
            dependsOn("formatKotlin")
        }
    }
}
