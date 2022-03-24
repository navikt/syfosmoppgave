package no.nav.syfo

import no.nav.syfo.model.JournalKafkaMessage
import no.nav.syfo.model.PrioritetType
import no.nav.syfo.model.ProduserOppgaveKafkaMessage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun deleteDir(dir: Path) {
    if (Files.exists(dir)) {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}

fun createProduceTask(msgId: String) = ProduserOppgaveKafkaMessage(
    messageId = msgId,
    aktoerId = "9127312",
    tildeltEnhetsnr = "9999",
    opprettetAvEnhetsnr = "9999",
    behandlesAvApplikasjon = "FS22",
    orgnr = "91203712",
    beskrivelse = "Test oppgave",
    temagruppe = "GRUPPE",
    tema = "TEMA",
    behandlingstema = "BEHANDLINGSTEMA",
    oppgavetype = "OPPGAVETYPE",
    behandlingstype = "BEHANDLINGSTYPE",
    mappeId = 12938,
    aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
    fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now().plusDays(10)),
    prioritet = PrioritetType.NORM,
    metadata = mapOf()
)

fun createRegisterJournal(msgId: String) = JournalKafkaMessage(
    messageId = msgId,
    journalpostId = "test",
    journalpostKilde = "test"
)
