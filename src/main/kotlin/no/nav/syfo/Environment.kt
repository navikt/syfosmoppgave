package no.nav.syfo

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmoppgave"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL", "http://security-token-service/rest/v1/sts/token"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL")
)

data class Credentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
