package no.nav.syfo.model

data class RegistrerOppgaveKafkaMessage(
    val produserOppgave: ProduserOppgaveKafkaMessage,
    val journalOpprettet: JournalKafkaMessage
)
