package no.nav.syfo.service

import io.ktor.client.features.ServerResponseException
import io.ktor.http.HttpStatusCode
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.log
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.ProduserOppgaveKafkaMessage
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.wrapExceptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter

suspend fun handleRegisterOppgaveRequest(
    oppgaveClient: OppgaveClient,
    opprettOppgave: OpprettOppgave,
    messageId: String,
    loggingMeta: LoggingMeta,
    kafkaRetryPublisher: KafkaRetryPublisher,
    source: String = "aiven"
) {
    wrapExceptions(loggingMeta) {
        log.info("$source: Received a SM2013, going to create oppgave, {}", fields(loggingMeta))

        try {
            opprettOppgave(oppgaveClient, opprettOppgave, loggingMeta, messageId, source)
        } catch (ex: ServerResponseException) {
            when (ex.response.status) {
                HttpStatusCode.InternalServerError -> {
                    log.error(
                        "$source: Noe gikk galt ved oppretting av oppgave, error melding: {}, {}",
                        ex.message,
                        fields(loggingMeta)
                    )
                    kafkaRetryPublisher.publishOppgaveToRetryTopic(opprettOppgave, messageId, loggingMeta)
                }
                else -> {
                    log.error(
                        "$source: Noe gikk galt ved oppretting av oppgave, error melding: {}, {}",
                        ex.message,
                        fields(loggingMeta)
                    )
                    throw ex
                }
            }
        }
    }
}

suspend fun opprettOppgave(
    oppgaveClient: OppgaveClient,
    opprettOppgave: OpprettOppgave,
    loggingMeta: LoggingMeta,
    messageId: String,
    source: String
) {
    val oppgaveResultat = oppgaveClient.opprettOppgave(opprettOppgave, messageId, loggingMeta)
    if (!oppgaveResultat.duplikat) {
        OPPRETT_OPPGAVE_COUNTER.inc()
        log.info(
            "$source: Opprettet oppgave med {}, {}, {}, {}",
            keyValue("oppgaveId", oppgaveResultat.oppgaveId),
            keyValue("sakid", opprettOppgave.saksreferanse),
            keyValue("journalpost", opprettOppgave.journalpostId),
            fields(loggingMeta)
        )
    }
}

fun opprettOppgave(
    produceTask: ProduserOppgaveKafkaMessage,
    registerJournal: JournalKafkaMessage
) = OpprettOppgave(
    aktoerId = produceTask.aktoerId,
    opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
    journalpostId = registerJournal.journalpostId,
    behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
    saksreferanse = registerJournal.sakId,
    beskrivelse = produceTask.beskrivelse,
    tema = produceTask.tema,
    oppgavetype = produceTask.oppgavetype,
    behandlingstype = if (produceTask.behandlingstype != "ANY") produceTask.behandlingstype else {
        null
    },
    behandlingstema = if (produceTask.behandlingstema != "ANY") produceTask.behandlingstema else {
        null
    },
    aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
    fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
    prioritet = produceTask.prioritet.name,
    tildeltEnhetsnr = if (produceTask.tildeltEnhetsnr.isNotEmpty()) {
        produceTask.tildeltEnhetsnr
    } else null,
    tilordnetRessurs = produceTask.metadata["tilordnetRessurs"]
)

fun opprettOppgave(
    produceTask: ProduceTask,
    registerJournal: RegisterJournal
) = OpprettOppgave(
    aktoerId = produceTask.aktoerId,
    opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
    journalpostId = registerJournal.journalpostId,
    behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
    saksreferanse = registerJournal.sakId,
    beskrivelse = produceTask.beskrivelse,
    tema = produceTask.tema,
    oppgavetype = produceTask.oppgavetype,
    behandlingstype = if (produceTask.behandlingstype != "ANY") produceTask.behandlingstype else {
        null
    },
    behandlingstema = if (produceTask.behandlingstema != "ANY") produceTask.behandlingstema else {
        null
    },
    aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
    fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
    prioritet = produceTask.prioritet.name,
    tildeltEnhetsnr = if (produceTask.tildeltEnhetsnr.isNotEmpty()) {
        produceTask.tildeltEnhetsnr
    } else null,
    tilordnetRessurs = produceTask.metadata["tilordnetRessurs"]
)
