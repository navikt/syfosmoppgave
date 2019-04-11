package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResult

@KtorExperimentalAPI
class OppgaveClient constructor(val url: String, val oidcClient: StsOidcClient) {
    private val client: HttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }
    }

    fun createOppgave(createOppgave: OpprettOppgave): Deferred<OpprettOppgaveResult> = client.async {
        // TODO: Remove this workaround whenever ktor issue #1009 is fixed
        client.post<HttpResponse>(url) {
            accept(ContentType.Application.Json)
            this.header("Authorization", "Bearer ${oidcClient.oidcToken()}")
            this.header("X-Correlation-ID", createOppgave.saksreferanse)
            body = createOppgave
        }.use { it.call.response.receive<OpprettOppgaveResult>() }
    }
}
