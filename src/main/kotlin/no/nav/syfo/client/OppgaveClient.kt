package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OppgaveResultat
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse

@KtorExperimentalAPI
class OppgaveClient constructor(private val url: String, private val oidcClient: StsOidcClient, private val httpClient: HttpClient) {
    private suspend fun opprettOppgave(opprettOppgave: OpprettOppgave, msgId: String): OpprettOppgaveResponse {
        try {
            return httpClient.post<OpprettOppgaveResponse>(url) {
                contentType(ContentType.Application.Json)
                val oidcToken = oidcClient.oidcToken()
                header("Authorization", "Bearer ${oidcToken.access_token}")
                header("X-Correlation-ID", msgId)
                body = opprettOppgave
            }
        } catch (ex: Exception) {
            log.error("Could not OpprettOppgave for \naktorID: ${opprettOppgave.aktoerId}\njournalPostid=${opprettOppgave.journalpostId}\n$opprettOppgave", ex)
            throw ex
        }
    }

    private suspend fun hentOppgave(opprettOppgave: OpprettOppgave, msgId: String): OppgaveResponse {
        return httpClient.get<OppgaveResponse>(url) {
            val oidcToken = oidcClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            header("X-Correlation-ID", msgId)
            parameter("tema", opprettOppgave.tema)
            parameter("oppgavetype", opprettOppgave.oppgavetype)
            parameter("journalpostId", opprettOppgave.journalpostId)
            parameter("aktoerId", opprettOppgave.aktoerId)
            parameter("statuskategori", "AAPEN")
            parameter("sorteringsrekkefolge", "ASC")
            parameter("sorteringsfelt", "FRIST")
            parameter("limit", "10")
        }
    }

    suspend fun opprettOppgave(opprettOppgave: OpprettOppgave, msgId: String, loggingMeta: LoggingMeta): OppgaveResultat {
        val oppgaveResponse = hentOppgave(opprettOppgave, msgId)
        if (oppgaveResponse.antallTreffTotalt > 0) {
            log.info("Det finnes allerede en åpen oppgave for journalpost {} på brukeren, {}", opprettOppgave.journalpostId, fields(loggingMeta))
            return OppgaveResultat(oppgaveResponse.oppgaver.first().id, true)
        }
        log.info("Oppretter oppgave {}", fields(loggingMeta))
        return OppgaveResultat(opprettOppgave(opprettOppgave, msgId).id, false)
    }
}
