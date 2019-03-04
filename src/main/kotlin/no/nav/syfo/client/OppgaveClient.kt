package no.nav.syfo.client

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse

@KtorExperimentalAPI
class OppgaveClient constructor(val url: String, val oidcClient: StsOidcClient) {
    private val client: HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
            }
        }
    }

    fun createOppgave(createOppgave: OpprettOppgave) : Deferred<OpprettOppgaveResponse> = client.async {
        client.post<OpprettOppgaveResponse>(url) {
            this.header("Authorization", "Bearer ${oidcClient.oidcToken()}")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            body = createOppgave
        }
    }
}
