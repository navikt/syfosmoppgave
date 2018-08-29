package no.nav.syfo

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val srvsyfooppgavegsakUsername: String = getEnvVar("SRVSYFOOPPGAVEGSAK_USERNAME"),
        val srvsyfooppgavegsakPassword: String = getEnvVar("SRVSYFOOPPGAVEGSAK_PASSWORD"),
        val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val kafkaSM2013OppgaveGsakTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "privat-syfomottak-sm2013-oppgaveGsak"),
        val kafkaSM2013OppgaveGsakITTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_IT_TOPIC", "privat-syfomottak-sm2013-oppgaveGsakInfotrygd"),
        val kafkaSM2013OppgaveGsakPMTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_PM_TOPIC", "privat-syfomottak-sm2013-oppgaveGsakPapirmottak")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
