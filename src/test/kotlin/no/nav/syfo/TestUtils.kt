package no.nav.syfo

import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun createProduceTask(msgId: String) = ProduceTask().apply {
    messageId = msgId
    aktoerId = "9127312"
    tildeltEnhetsnr = "9999"
    opprettetAvEnhetsnr = "9999"
    behandlesAvApplikasjon = "FS22"
    orgnr = "91203712"
    beskrivelse = "Test oppgave"
    temagruppe = "GRUPPE"
    tema = "TEMA"
    behandlingstema = "BEHANDLINGSTEMA"
    oppgavetype = "OPPGAVETYPE"
    behandlingstype = "BEHANDLINGSTYPE"
    mappeId = 12938
    aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
    fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(10))
    prioritet = PrioritetType.NORM
    metadata = mapOf()
}

fun createOppgaveResponse(): OpprettOppgaveResponse = OpprettOppgaveResponse(
        tildeltEnhetsnr = "9999",
        opprettetAvEnhetsnr = "TODO",
        aktoerId = "TODO",
        journalpostId = "TODO",
        journalpostkilde = "TODO",
        behandlesAvApplikasjon = "TODO",
        saksreferanse = "TODO",
        orgnr = "TODO",
        beskrivelse = "TODO",
        temagruppe = "TODO",
        tema = "TODO",
        behandlingstema = "TODO",
        oppgavetype = "TODO",
        behandlingstype = "TODO",
        mappeId = 123,
        aktivDato = LocalDate.now(),
        fristFerdigstillelse = LocalDate.now(),
        prioritet = "TODO",
        metadata = mapOf(),
        opprettetTidspunkt = LocalDateTime.now(),
        opprettetAv = "TODO",
        endretAv = "TODO",
        ferdigstiltTidspunkt = LocalDateTime.now(),
        endretTidspunkt = "TODO",
        status = "UNDER_BEHANDLING",
        id = "oppgave-12837u12"
)

fun createRegisterJournal(msgId: String) = RegisterJournal().apply {
    messageId = msgId
    sakId = "test_sak"
    journalpostId = "test"
    journalpostKilde = "test"
}
