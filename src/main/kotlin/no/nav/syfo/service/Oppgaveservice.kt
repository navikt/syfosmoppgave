package no.nav.syfo.service

import io.ktor.client.features.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.wrapExceptions

@KtorExperimentalAPI
suspend fun handleRegisterOppgaveRequest(
    oppgaveClient: OppgaveClient,
    produceTask: ProduceTask,
    registerJournal: RegisterJournal,
    loggingMeta: LoggingMeta,
    kafkaRetryPublisher: KafkaRetryPublisher
) {
    wrapExceptions(loggingMeta) {
        log.info("Received a SM2013, going to create oppgave, {}", fields(loggingMeta))
        val opprettOppgave = OpprettOppgave(
                aktoerId = produceTask.aktoerId,
                opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
                journalpostId = registerJournal.journalpostId,
                behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
                saksreferanse = registerJournal.sakId,
                beskrivelse = produceTask.beskrivelse,
                tema = produceTask.tema,
                oppgavetype = produceTask.oppgavetype,
                behandlingstype = if (produceTask.behandlingstype != "ANY") produceTask.behandlingstype else { null },
                aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
                fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
                prioritet = produceTask.prioritet.name
        )

        try {
            opprettOppgave(oppgaveClient, opprettOppgave, loggingMeta, registerJournal.messageId)
        } catch (ex: ServerResponseException) {
            when (ex.response.status) {
                HttpStatusCode.InternalServerError -> {
                    log.error("Noe gikk galt ved oppretting av oppgave, error melding: {}, {}", ex.message, fields(loggingMeta))
                    kafkaRetryPublisher.publishOppgaveToRetryTopic(opprettOppgave, registerJournal.messageId, loggingMeta)
                }
                else -> {
                    log.error("Noe gikk galt ved oppretting av oppgave, error melding: {}, {}", ex.message, fields(loggingMeta))
                    throw ex
                }
            }
        }
    }
}

@KtorExperimentalAPI
suspend fun opprettOppgave(oppgaveClient: OppgaveClient, opprettOppgave: OpprettOppgave, loggingMeta: LoggingMeta, messageId: String) {
    val oppgaveResultat = oppgaveClient.opprettOppgave(opprettOppgave, messageId, loggingMeta)
    if (!oppgaveResultat.duplikat) {
        OPPRETT_OPPGAVE_COUNTER.inc()
        log.info("Opprettet oppgave med {}, {}, {}, {}",
                keyValue("oppgaveId", oppgaveResultat.oppgaveId),
                keyValue("sakid", opprettOppgave.saksreferanse),
                keyValue("journalpost", opprettOppgave.journalpostId),
                fields(loggingMeta))
    }
}
