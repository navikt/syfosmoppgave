package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmoppgave-stream"),
    val oppgaveJournalOpprettet: String = "teamsykmelding.oppgave-journal-opprettet",
    val oppgaveProduserOppgave: String = "teamsykmelding.oppgave-produser-oppgave",
    val privatRegistrerOppgave: String = "teamsykmelding.privat-registrer-oppgave",
    val applicationId: String = getEnvVar("KAFKA_STREAMS_APPLICATION_ID"),
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
