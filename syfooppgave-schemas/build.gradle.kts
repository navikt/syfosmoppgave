group = "no.nav.syfo"
version = "1.1-SNAPSHOT"

val avroVersion = "1.8.2"

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath ("com.commercehub.gradle.plugin:gradle-avro-plugin:0.16.0")
    }
}

plugins {
    id("com.commercehub.gradle.plugin.avro") version "0.9.1"
    `maven-publish`
}


repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation ("org.apache.avro:avro:$avroVersion")
}

avro {
    fieldVisibility = "PRIVATE"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            val releaseType = when (project.version.toString().endsWith("-SNAPSHOT", false)){
                true -> "snapshots"
                else -> "releases" }
            url = uri("https://repo.adeo.no/repository/maven-$releaseType/")
        }
    }
}