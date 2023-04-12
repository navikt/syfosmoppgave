package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.RegistrerOppgaveKafkaMessage
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmoppgave-stream")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

fun main() {
    val env = Environment()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState,
    )
    createAndStartKafkaStream(env, applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
}

fun createAndStartKafkaStream(env: Environment, applicationState: ApplicationState) {
    val streamBuilder = StreamsBuilder()
    val streamProperties = KafkaUtils.getAivenKafkaConfig()
        .toStreamsConfig(env.applicationName, Serdes.ByteArray()::class)
    streamProperties[StreamsConfig.APPLICATION_ID_CONFIG] = env.applicationId
    val journalOpprettetStream =
        streamBuilder.stream(env.oppgaveJournalOpprettet, Consumed.with(Serdes.String(), Serdes.ByteArray()))
    val produserOppgaveStream =
        streamBuilder.stream(env.oppgaveProduserOppgave, Consumed.with(Serdes.String(), Serdes.ByteArray()))

    val joinWindow = JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofDays(14))

    journalOpprettetStream.join(
        produserOppgaveStream,
        { journalOpprettet, produserOppgave ->
            objectMapper.writeValueAsBytes(
                RegistrerOppgaveKafkaMessage(
                    produserOppgave = objectMapper.readValue(produserOppgave),
                    journalOpprettet = objectMapper.readValue(journalOpprettet),
                ),
            )
        },
        joinWindow,
    ).to(env.privatRegistrerOppgave)

    val kafkaStream = KafkaStreams(streamBuilder.build(), streamProperties)

    kafkaStream.setUncaughtExceptionHandler { err ->
        log.error("Caught exception in stream: ${err.message}", err)
        closeStream(kafkaStream, applicationState)
        throw err
    }

    kafkaStream.setStateListener { newState, oldState ->
        log.info("From state={} to state={}", oldState, newState)
        if (newState == KafkaStreams.State.ERROR) {
            closeStream(kafkaStream, applicationState)
        }
    }

    kafkaStream.start()
}

private fun closeStream(
    kafkaStream: KafkaStreams,
    applicationState: ApplicationState,
) {
    kafkaStream.close(Duration.ofSeconds(30))
    log.error("Closing stream because it went into error state")
    applicationState.ready = false
    applicationState.alive = false
}
