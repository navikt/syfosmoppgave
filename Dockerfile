FROM navikt/java:12
COPY build/libs/syfosmoppgave-*.jar app.jar
ENV JAVA_OPTS='-Dlogback.configurationFile=logback.xml'