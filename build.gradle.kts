import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.0"
val jacksonVersion = "2.13.2"
val kafkaVersion = "2.8.0"
val kluentVersion = "1.68"
val ktorVersion = "1.6.8"
val logstashEncoderVersion = "7.0.1"
val logbackVersion = "1.2.11"
val prometheusVersion = "0.15.0"
val smCommonVersion = "1.a92720c"
val spekVersion = "2.0.17"
val testContainerKafkaVersion = "1.16.2"
val mockVersion = "1.12.3"
val kotlinVersion = "1.6.0"

plugins {
    id("org.jmailen.kotlinter") version "3.6.0"
    kotlin("jvm") version "1.6.0"
    id("com.diffplug.spotless") version "5.16.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

val githubUser: String by project
val githubPassword: String by project

subprojects {
    group = "no.nav.syfo"
    version = "1.0.0"
    apply(plugin = "org.jmailen.kotlinter")
    apply(plugin = "kotlin")
    apply(plugin = "com.diffplug.spotless")
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

        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-jackson:$ktorVersion")

        implementation("io.ktor:ktor-client-apache:$ktorVersion")
        implementation("io.ktor:ktor-client-auth-basic:$ktorVersion")
        implementation("io.ktor:ktor-client-jackson:$ktorVersion")

        implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-networking:$smCommonVersion")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

        implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
        implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

        testImplementation("io.mockk:mockk:$mockVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
        testImplementation("org.testcontainers:kafka:$testContainerKafkaVersion")

        testRuntimeOnly("org.spekframework.spek2:spek-runtime-jvm:$spekVersion")
        testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
            exclude(group = "org.jetbrains.kotlin")
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
            kotlinOptions.jvmTarget = "17"
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

        "check" {
            dependsOn("formatKotlin")
        }
    }

}

