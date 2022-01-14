package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmoppgave"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val securityTokenServiceUrl: String = getEnvVar("SECURITYTOKENSERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val oppgavebehandlingUrl: String = getEnvVar("OPPGAVEBEHANDLING_URL"),
    val privatRegistrerOppgave: String = "teamsykmelding.privat-registrer-oppgave",
    val retryOppgaveAivenTopic: String = "teamsykmelding.privat-oppgave-retry"
)

data class Credentials(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
