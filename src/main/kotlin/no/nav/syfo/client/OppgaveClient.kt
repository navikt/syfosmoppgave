package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OpprettOppgave

@KtorExperimentalAPI
class OppgaveClient constructor(private val url: String, private val oidcClient: StsOidcClient, private val httpClient: HttpClient) {
    suspend fun createOppgave(createOppgave: OpprettOppgave, msgId: String): OppgaveResponse = retry("create_oppgave") {
        httpClient.post<OppgaveResponse>(url) {
            contentType(ContentType.Application.Json)
            val oidcToken = oidcClient.oidcToken()
            this.header("Authorization", "Bearer ${oidcToken.access_token}")
            this.header("X-Correlation-ID", msgId)
            body = createOppgave
        }
    }
}
