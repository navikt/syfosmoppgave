package no.nav.syfo.application

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.routing
import no.nav.syfo.Environment
import no.nav.syfo.application.api.registerNaisApi
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.service.registerOppgaveApi

fun createApplicationEngine(
    env: Environment,
    applicationState: ApplicationState,
    oppgaveClient: OppgaveClient,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
    embeddedServer(Netty, env.applicationPort) {
        routing {
            registerNaisApi(applicationState)
            registerOppgaveApi(oppgaveClient)
        }
    }
