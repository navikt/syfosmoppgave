FROM navikt/java:10
COPY build/libs/syfosmoppgave-*-all.jar app.jar
ENV JAVA_OPTS="'-Dlogback.configurationFile=logback-remote.xml'"
