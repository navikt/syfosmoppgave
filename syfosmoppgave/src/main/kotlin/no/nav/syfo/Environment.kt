package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmoppgave"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val journalCreatedTopic: String = getEnvVar("KAFKA_JOURNAL_CREATED_TOPIC", "aapen-syfo-oppgave-journalOpprettet"),
    val oppgaveTopic: String = getEnvVar("KAFKA_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val retryOppgaveTopic: String = "privat-syfo-oppgave-retry"
) : KafkaConfig

data class Credentials(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
