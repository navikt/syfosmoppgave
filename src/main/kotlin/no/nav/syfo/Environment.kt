package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val srvsyfosmoppgaveUsername: String = getEnvVar("SRVSYFOSMOPPGAVE_USERNAME"),
    val srvsyfosmoppgavePassword: String = getEnvVar("SRVSYFOSMOPPGAVE_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL"),
    val virksomhetOppgavebehandlingV3Endpointurl: String = getEnvVar("VIRKSOMHET_OPPGAVEBEHANDLING_V3_ENDPOINTURL")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
