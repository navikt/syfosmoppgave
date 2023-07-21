package no.nav.syfo.retry

import java.time.Duration
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.retry.util.getNextRunTime
import no.nav.syfo.service.opprettOppgave
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

class OpprettOppgaveRetryService(
    private val kafkaConsumer: KafkaConsumer<String, OppgaveRetryKafkaMessage>,
    private val applicationState: ApplicationState,
    private val oppgaveClient: OppgaveClient,
    private val topic: String,
    private val kafkaCluster: String,
    private val naisCluster: String,
) {

    companion object {
        private val log = LoggerFactory.getLogger(OpprettOppgaveRetryService::class.java)

        private val rerunTimes =
            listOf<OffsetTime>(
                OffsetTime.of(3, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(6, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(9, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(15, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(21, 0, 0, 0, ZoneOffset.UTC),
            )
    }

    private val runtimeMinutes: Long = 5

    suspend fun runConsumer() {
        var endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(runtimeMinutes)
        while (applicationState.ready && OffsetDateTime.now(ZoneOffset.UTC).isBefore(endTime)) {
            val records = kafkaConsumer.poll(Duration.ofMillis(1000))
            records.forEach { processRecord(it) }
            if (!records.isEmpty) {
                endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
            }
        }
    }

    private suspend fun processRecord(it: ConsumerRecord<String, OppgaveRetryKafkaMessage>) {
        try {
            val kafkaMessage = it.value()
            val messageId = it.key()
            log.info(
                "$kafkaCluster: Running retry for opprett oppgave {}",
                fields(kafkaMessage.loggingMeta),
            )
            opprettOppgave(
                oppgaveClient,
                kafkaMessage.opprettOppgave,
                kafkaMessage.loggingMeta,
                messageId = messageId,
                kafkaCluster,
            )
        } catch (e: Exception) {
            if (naisCluster != "dev-gcp") (throw e)
            else (log.error("Error running retry-service, skipping item in dev-gcp"))
        }
    }

    suspend fun start() {
        var firstRun = true
        while (applicationState.ready) {
            val nextRunTime =
                if (firstRun) {
                    firstRun = false
                    0
                } else {
                    getNextRunTime(OffsetDateTime.now(ZoneOffset.UTC), rerunTimes)
                }
            log.info("$kafkaCluster: Delaying for $nextRunTime")
            delay(nextRunTime)
            try {
                log.info("$kafkaCluster: Starting opprett oppgave consumer")
                kafkaConsumer.subscribe(listOf(topic))
                runConsumer()
            } catch (ex: Exception) {
                log.error("$kafkaCluster: Error when rerunning opprett oppgave", ex)
            } finally {
                kafkaConsumer.unsubscribe()
            }
        }
    }
}
