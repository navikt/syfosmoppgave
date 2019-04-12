package no.nav.syfo

import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun deleteDir(dir: Path) {
    if (Files.exists(dir)) {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}

fun cleanupDir(dir: Path, streamApplicationName: String) {
    deleteDir(dir)
    Files.createDirectories(dir)
    Files.createDirectories(dir.resolve(streamApplicationName))
}

val kafkaStreamsStateDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))
        .resolve("kafka-stream-integration-tests")

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

fun createOppgaveResponse(): OppgaveResponse = OppgaveResponse(
        id = 21312313
)

fun createRegisterJournal(msgId: String) = RegisterJournal().apply {
    messageId = msgId
    sakId = "test_sak"
    journalpostId = "test"
    journalpostKilde = "test"
}
