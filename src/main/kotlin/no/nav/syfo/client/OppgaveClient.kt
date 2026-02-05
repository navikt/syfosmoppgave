package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.log
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OppgaveResultat
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.securelog

data class FeilregistrerOppgaveRequest(
    val status: String = "FEILREGISTRERT",
    val versjon: Int,
)

class OppgaveClient(
    private val url: String,
    private val accessTokenClient: AccessTokenClient,
    private val scope: String,
    private val httpClient: HttpClient,
) {
    private suspend fun opprettOppgave(
        opprettOppgave: OpprettOppgave,
        msgId: String
    ): OpprettOppgaveResponse {
        try {
            return httpClient
                .post(url) {
                    contentType(ContentType.Application.Json)
                    val token = accessTokenClient.getAccessToken(scope)
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-ID", msgId)
                    setBody(opprettOppgave)
                }
                .body()
        } catch (ex: Exception) {
            log.error(
                "Could not OpprettOppgave for journalPostid=${opprettOppgave.journalpostId}",
                ex
            )
            securelog.info("opprettOppgave: $opprettOppgave")
            throw ex
        }
    }

    suspend fun hentOppgave(
        opprettOppgave: OpprettOppgave,
        msgId: String,
        statusKategori: String = "AAPEN"
    ): OppgaveResponse {
        try {
            return httpClient
                .get(url) {
                    val token = accessTokenClient.getAccessToken(scope)
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-ID", msgId)
                    parameter("tema", opprettOppgave.tema)
                    parameter("oppgavetype", opprettOppgave.oppgavetype)
                    parameter("journalpostId", opprettOppgave.journalpostId)
                    parameter("aktoerId", opprettOppgave.aktoerId)
                    parameter("statuskategori", statusKategori)
                    parameter("sorteringsrekkefolge", "ASC")
                    parameter("sorteringsfelt", "FRIST")
                    parameter("limit", "10")
                }
                .body<OppgaveResponse>()
        } catch (ex: Exception) {
            log.error(
                "Could not hentOppgave for \njournalPostid=${opprettOppgave.journalpostId}",
                ex
            )
            securelog.info("opprettOppgave: $opprettOppgave")
            throw ex
        }
    }

    suspend fun feilregistrerOppgave(oppgaveId: Int, versjon: Int, msgId: String): Oppgave {
        try {
            return httpClient
                .patch("$url/$oppgaveId") {
                    val token = accessTokenClient.getAccessToken(scope)
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-ID", msgId)
                    setBody(FeilregistrerOppgaveRequest(versjon = versjon))
                }
                .body<Oppgave>()
        } catch (ex: Exception) {
            log.error("Could not feilregistrere oppgave for oppgaveId: $oppgaveId", ex)
            throw ex
        }
    }

    suspend fun opprettOppgave(
        opprettOppgave: OpprettOppgave,
        msgId: String,
        loggingMeta: LoggingMeta
    ): OppgaveResultat {
        val oppgaveResponse = hentOppgave(opprettOppgave, msgId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info(
                "Det finnes allerede en åpen oppgave for journalpost {} på brukeren, {}",
                opprettOppgave.journalpostId,
                fields(loggingMeta)
            )
            return OppgaveResultat(oppgaveResponse.oppgaver.first().id, true)
        }
        log.info("Oppretter oppgave {}", fields(loggingMeta))
        return OppgaveResultat(opprettOppgave(opprettOppgave, msgId).id, false)
    }
}
