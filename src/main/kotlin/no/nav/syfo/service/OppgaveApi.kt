package no.nav.syfo.service

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.model.RegistrerOppgaveKafkaMessage

fun Route.registerOppgaveApi(oppgaveClient: OppgaveClient) {

    authenticate {
        post("/api/oppgave") {
            val request = call.receive<RegistrerOppgaveKafkaMessage>()

            val oppgaveResponse =
                oppgaveClient.hentOppgave(
                    opprettOppgave(request.produserOppgave, request.journalOpprettet),
                    request.produserOppgave.messageId
                )

            call.respond(oppgaveResponse)
        }
    }
}
