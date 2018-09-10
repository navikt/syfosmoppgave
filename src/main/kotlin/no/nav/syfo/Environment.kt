package no.nav.syfo

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
        val srvsyfosmgsakUsername: String = getEnvVar("SRVSYFOSMGSAK_USERNAME"),
        val srvsyfosmgsakPassword: String = getEnvVar("SRVSYFOSMGSAK_PASSWORD"),
        val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
        val smInfotrygdManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_IT_TOPIC", "privat-syfo-sminfotrygd-manuellBehandling"),
        val smPaperManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_PM_TOPIC", "privat-syfo-smpapir-manuellBehandling")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
