package no.nav.syfo.model

data class RegistrerOppgaveKafkaMessage(
    val produserOppgave: ByteArray,
    val journalOpprettet: ByteArray
)
