package no.nav.syfo

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmoppgave"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val privatRegistrerOppgave: String = "teamsykmelding.privat-registrer-oppgave",
    val retryOppgaveAivenTopic: String = "teamsykmelding.privat-oppgave-retry",
    val aadAccessTokenUrl: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val oppgaveScope: String = getEnvVar("OPPGAVE_SCOPE"),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName)
        ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
