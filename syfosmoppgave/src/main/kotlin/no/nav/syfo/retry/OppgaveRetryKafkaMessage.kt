package no.nav.syfo.retry

import no.nav.syfo.LoggingMeta
import no.nav.syfo.model.OpprettOppgave

data class OppgaveRetryKafkaMessage(
    val loggingMeta: LoggingMeta,
    val opprettOppgave: OpprettOppgave
)
