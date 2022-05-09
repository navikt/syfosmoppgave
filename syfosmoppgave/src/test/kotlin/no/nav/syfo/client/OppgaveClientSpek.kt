package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.syfo.LoggingMeta
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

class OppgaveClientSpek : FunSpec({
    val stsOidcClientMock = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val loggingMetadata = LoggingMeta("sykmeldingId", "123", "hendelsesId")

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/oppgave") {
                when {
                    call.request.queryParameters["journalpostId"] == "jpId" -> call.respond(
                        OppgaveResponse(
                            1,
                            listOf(
                                Oppgave(
                                    1, "9999",
                                    "12345678910", "jpId",
                                    "SYM", "BEH_EL_SYM"
                                )
                            )
                        )
                    )
                    call.request.queryParameters["journalpostId"] == "nyJpId" -> call.respond(OppgaveResponse(0, emptyList()))
                    else -> call.respond(HttpStatusCode.InternalServerError)
                }
            }
            post("/oppgave") {
                call.respond(OpprettOppgaveResponse(42))
            }
        }
    }.start()

    val oppgaveClient = OppgaveClient("$mockHttpServerUrl/oppgave", stsOidcClientMock, httpClient)

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    beforeSpec {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    context("OppgaveClient oppretter oppgave når det ikke finnes fra før") {
        test("Oppretter ikke oppgave hvis det finnes fra før") {
            val oppgave = oppgaveClient.opprettOppgave(lagOpprettOppgaveRequest("jpId"), "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 1
            oppgave.duplikat shouldBeEqualTo true
        }
        test("Oppretter oppgave hvis det ikke finnes fra før") {
            val oppgave = oppgaveClient.opprettOppgave(lagOpprettOppgaveRequest("nyJpId"), "sykmeldingId", loggingMetadata)

            oppgave.oppgaveId shouldBeEqualTo 42
            oppgave.duplikat shouldBeEqualTo false
        }
    }
})

fun lagOpprettOppgaveRequest(jpId: String): OpprettOppgave =
    OpprettOppgave(
        tildeltEnhetsnr = "0101",
        aktoerId = "12345678910",
        opprettetAvEnhetsnr = "9999",
        journalpostId = jpId,
        behandlesAvApplikasjon = "FS22",
        beskrivelse = "Masse beskrivelse",
        tema = "SYM",
        oppgavetype = "BEH_EL_SYM",
        aktivDato = LocalDate.of(2019, Month.JANUARY, 12),
        fristFerdigstillelse = LocalDate.of(2019, Month.JANUARY, 14),
        prioritet = "NORM"
    )
