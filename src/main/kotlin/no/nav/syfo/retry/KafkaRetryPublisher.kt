package no.nav.syfo.retry

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.LoggingMeta
import no.nav.syfo.log
import no.nav.syfo.metrics.RETRY_OPPGAVE_COUNTER
import no.nav.syfo.model.OpprettOppgave
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaRetryPublisher(private val kafkaRetryProducer: KafkaProducer<String, OppgaveRetryKafkaMessage>, private val retryTopic: String) {
    fun publishOppgaveToRetryTopic(opprettOppgave: OpprettOppgave, messageId: String, loggingMeta: LoggingMeta) {
        val kafkaMessage = OppgaveRetryKafkaMessage(loggingMeta, opprettOppgave)
        kafkaRetryProducer.send(ProducerRecord(retryTopic, messageId, kafkaMessage))
        log.info("publish to retrytopic for opprett oppgave {}", StructuredArguments.fields(loggingMeta))
        RETRY_OPPGAVE_COUNTER.inc()
    }
}
