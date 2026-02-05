package no.nav.syfo.service

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.ProduserOppgaveKafkaMessage

data class FeilregistrerOppgaveRequest(val oppgaveId: Int, val version: Int, val msgId: String)

data class OppgaveApiRequest(
    val produserOppgave: ProduserOppgaveKafkaMessage,
    val journalOpprettet: JournalKafkaMessage,
    val statusKategori: String? = null,
)

fun Route.registerOppgaveApi(oppgaveClient: OppgaveClient) {

    authenticate {
        post("/api/oppgave") {
            val request = call.receive<OppgaveApiRequest>()

            val oppgaveResponse =
                oppgaveClient.hentOppgave(
                    opprettOppgave(request.produserOppgave, request.journalOpprettet),
                    request.produserOppgave.messageId,
                    request.statusKategori ?: "AAPEN"
                )

            call.respond(oppgaveResponse)
        }

        post("/api/oppgave/feilregistrer") {
            val request = call.receive<FeilregistrerOppgaveRequest>()
            call.respond(
                oppgaveClient.feilregistrerOppgave(
                    request.oppgaveId,
                    request.version,
                    request.msgId
                )
            )
        }
    }
}
