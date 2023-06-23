package no.nav.syfo.retry

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.metrics.RETRY_OPPGAVE_COUNTER
import no.nav.syfo.model.OpprettOppgave
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaRetryPublisher(
    private val kafkaRetryProducer: KafkaProducer<String, OppgaveRetryKafkaMessage>,
    private val retryTopic: String
) {
    fun publishOppgaveToRetryTopic(
        opprettOppgave: OpprettOppgave,
        messageId: String,
        loggingMeta: LoggingMeta
    ) {
        val kafkaMessage = OppgaveRetryKafkaMessage(loggingMeta, opprettOppgave)
        try {
            kafkaRetryProducer.send(ProducerRecord(retryTopic, messageId, kafkaMessage)).get()
            log.info("publish to retrytopic for opprett oppgave {}", fields(loggingMeta))
            RETRY_OPPGAVE_COUNTER.inc()
        } catch (ex: Exception) {
            log.error(
                "Error sending to retry topic for message {} {}",
                messageId,
                fields(loggingMeta)
            )
            throw ex
        }
    }
}
