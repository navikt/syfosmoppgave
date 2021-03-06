package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.model.Oppgave
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OppgaveResultat
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.OpprettOppgaveResponse
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object OppgaveClientSpek : Spek({
    val stsOidcClientMock = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
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
        install(ContentNegotiation) {
            jackson {}
        }
        routing {
            get("/oppgave") {
                when {
                    call.request.queryParameters["journalpostId"] == "jpId" -> call.respond(OppgaveResponse(1,
                            listOf(Oppgave(1, "9999",
                                    "12345678910", "jpId", "sakId",
                                    "SYM", "BEH_EL_SYM"))))
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

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(10), TimeUnit.SECONDS.toMillis(10))
    }

    beforeGroup {
        coEvery { stsOidcClientMock.oidcToken() } returns OidcToken("token", "type", 300L)
    }

    describe("OppgaveClient oppretter oppgave når det ikke finnes fra før") {
        it("Oppretter ikke oppgave hvis det finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettOppgave(lagOpprettOppgaveRequest("jpId"), "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 1
            oppgave?.duplikat shouldEqual true
        }
        it("Oppretter oppgave hvis det ikke finnes fra før") {
            var oppgave: OppgaveResultat? = null
            runBlocking {
                oppgave = oppgaveClient.opprettOppgave(lagOpprettOppgaveRequest("nyJpId"), "sykmeldingId", loggingMetadata)
            }

            oppgave?.oppgaveId shouldEqual 42
            oppgave?.duplikat shouldEqual false
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
                saksreferanse = "sakId",
                beskrivelse = "Masse beskrivelse",
                tema = "SYM",
                oppgavetype = "BEH_EL_SYM",
                aktivDato = LocalDate.of(2019, Month.JANUARY, 12),
                fristFerdigstillelse = LocalDate.of(2019, Month.JANUARY, 14),
                prioritet = "NORM"
        )
