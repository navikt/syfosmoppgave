package no.nav.syfo.retry

import io.ktor.util.KtorExperimentalAPI
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
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory

@KtorExperimentalAPI
class OpprettOppgaveRetryService(
    private val kafkaConsumer: KafkaConsumer<String, OppgaveRetryKafkaMessage>,
    private val applicationState: ApplicationState,
    private val oppgaveClient: OppgaveClient,
    private val topic: String
) {

    companion object {
        private val log = LoggerFactory.getLogger(OpprettOppgaveRetryService::class.java)

        private val rerunTimes = listOf<OffsetTime>(
                OffsetTime.of(3, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(6, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(9, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(12, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(15, 0, 0, 0, ZoneOffset.UTC),
                OffsetTime.of(21, 0, 0, 0, ZoneOffset.UTC))
    }

    private val runtimeMinutes: Long = 5

    @KtorExperimentalAPI
    suspend fun runConsumer() {
        var endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(runtimeMinutes)
        while (applicationState.ready && OffsetDateTime.now(ZoneOffset.UTC).isBefore(endTime)) {
            val records = kafkaConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                val kafkaMessage = it.value()
                val messageId = it.key()
                log.info("Running retry for opprett oppgave {}", fields(kafkaMessage.loggingMeta))
                opprettOppgave(oppgaveClient, kafkaMessage.opprettOppgave, kafkaMessage.loggingMeta, messageId = messageId)
            }
            if (!records.isEmpty) {
                endTime = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)
            }
            delay(100)
        }
    }

    suspend fun start() {
        while (applicationState.ready) {
            val nextRunTime = getNextRunTime(OffsetDateTime.now(ZoneOffset.UTC), rerunTimes)
            log.info("Delaying for $nextRunTime")
            delay(nextRunTime)
            try {
                log.info("Starting opprett oppgave consumer")
                kafkaConsumer.subscribe(listOf(topic))
                runConsumer()
            } catch (ex: Exception) {
                log.error("Error when rerunning opprett oppgave", ex)
            } finally {
                kafkaConsumer.unsubscribe()
            }
        }
    }
}
