[![Build status](https://github.com/navikt/syfosmoppgave/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)](https://github.com/navikt/syfosmoppgave/workflows/Deploy%20to%20dev%20and%20prod/badge.svg)
#  SYFO sm oppgave
This project contains creating task to Gsak for the sykmelding2013 message

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kotest
* Kafka

#### Requirements

* JDK 21

## Getting started
#### Compile and package application
##### syfosmoppgave
To build locally and run the integration tests you can simply run
``` bash 
cd syfosmoppgave
./gradlew shadowJar
```
or on windows 
`gradlew.bat shadowJar`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Contact

This project is maintained by navikt/teamsykmelding

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/syfosmoppgave/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)
