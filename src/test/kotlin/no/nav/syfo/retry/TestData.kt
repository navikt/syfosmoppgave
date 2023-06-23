package no.nav.syfo.retry

import java.time.LocalDate
import no.nav.syfo.LoggingMeta
import no.nav.syfo.model.OpprettOppgave

fun getKafkaRetryMessage(): OppgaveRetryKafkaMessage {
    return OppgaveRetryKafkaMessage(
        loggingMeta = LoggingMeta("123", "messageId", "123"),
        opprettOppgave =
            OpprettOppgave(
                tildeltEnhetsnr = null,
                oppgavetype = "type",
                opprettetAvEnhetsnr = "123",
                prioritet = "ja",
                behandlesAvApplikasjon = "123",
                aktoerId = "aktor",
                aktivDato = LocalDate.now(),
                beskrivelse = "beskrivelse",
                fristFerdigstillelse = LocalDate.now().plusDays(3),
                journalpostId = "",
                tema = "team",
                tilordnetRessurs = null,
            ),
    )
}
