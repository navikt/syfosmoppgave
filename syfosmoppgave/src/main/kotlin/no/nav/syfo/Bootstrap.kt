package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import io.prometheus.client.hotspot.DefaultExports
import kafka.server.KafkaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.RegistrerOppgaveKafkaMessage
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.retry.OppgaveKafkaDeserializer
import no.nav.syfo.retry.OppgaveKafkaSerializer
import no.nav.syfo.retry.OppgaveRetryKafkaMessage
import no.nav.syfo.retry.OpprettOppgaveRetryService
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import no.nav.syfo.service.handleRegisterOppgaveRequest
import no.nav.syfo.service.opprettOppgave
import no.nav.syfo.util.JacksonKafkaDeserializer
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smoppgave")
val objectMapper: ObjectMapper = ObjectMapper().registerModule(JavaTimeModule()).registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val credentials = Credentials()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }
    val oidcClient =
        StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
    kafkaBaseConfig["auto.offset.reset"] = "none"
    val consumerProperties = kafkaBaseConfig.toConsumerConfig(
        "${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class
    )

    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = GenericAvroSerde::class)

    val kafkaRetryProducer = KafkaProducer<String, OppgaveRetryKafkaMessage>(
        kafkaBaseConfig.toProducerConfig(
            "${env.applicationName}-retry-producer",
            keySerializer = StringSerializer::class,
            valueSerializer = OppgaveKafkaSerializer::class
        )
    )

    val kafkaRetryPublisher = KafkaRetryPublisher(kafkaRetryProducer, env.retryOppgaveTopic)

    val kafkaRetryConsumer = KafkaConsumer<String, OppgaveRetryKafkaMessage>(
        kafkaBaseConfig.toConsumerConfig(
            "${env.applicationName}-retry-consumer",
            keyDeserializer = StringDeserializer::class,
            valueDeserializer = OppgaveKafkaDeserializer::class
        )
    )

    val oppgaveRetryService = OpprettOppgaveRetryService(
        kafkaRetryConsumer, applicationState, oppgaveClient, env.retryOppgaveTopic, "on-prem"
    )

    applicationState.ready = true

    launchListeners(
        consumerProperties,
        applicationState,
        oppgaveClient,
        streamProperties,
        env,
        kafkaRetryPublisher,
        oppgaveRetryService
    )

    setupAndRunAiven(env, applicationState, oppgaveClient)
}

@DelicateCoroutinesApi
fun setupAndRunAiven(env: Environment, applicationState: ApplicationState, oppgaveClient: OppgaveClient) {
    val aivenProperties = KafkaUtils.getAivenKafkaConfig()
    val aivenRetryConsumer = KafkaConsumer<String, OppgaveRetryKafkaMessage>(
        aivenProperties.toConsumerConfig(
            "${env.applicationName}-consumer",
            keyDeserializer = StringDeserializer::class,
            valueDeserializer = OppgaveKafkaDeserializer::class
        ).also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" }
    )
    val aivenRegistrerOppgaveConsumer = KafkaConsumer(
        aivenProperties.toConsumerConfig(
            "${env.applicationName}-consumer", valueDeserializer = JacksonKafkaDeserializer::class
        ).also {
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        },
        StringDeserializer(), JacksonKafkaDeserializer(RegistrerOppgaveKafkaMessage::class)
    )

    val aivenRetryProducer = KafkaProducer<String, OppgaveRetryKafkaMessage>(
        KafkaUtils.getAivenKafkaConfig().toProducerConfig(
            "${env.applicationName}-retry-producer",
            keySerializer = StringSerializer::class,
            valueSerializer = OppgaveKafkaSerializer::class
        )
    )
    val aivenRetryPublisher = KafkaRetryPublisher(aivenRetryProducer, env.retryOppgaveAivenTopic)

    createListener(applicationState) {
        aivenRegistrerOppgaveConsumer.subscribe(listOf(env.privatRegistrerOppgave))
        blockingApplicationLogicAiven(applicationState, aivenRegistrerOppgaveConsumer, oppgaveClient, aivenRetryPublisher)
    }
    createListener(applicationState) {
        OpprettOppgaveRetryService(
            aivenRetryConsumer, applicationState, oppgaveClient, env.retryOppgaveAivenTopic, "aiven"
        ).start()
    }
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val journalCreatedTaskStream = streamsBuilder.stream<String, RegisterJournal>(env.journalCreatedTopic)
    val createTaskStream = streamsBuilder.stream<String, ProduceTask>(env.oppgaveTopic)
    KafkaConfig.LogRetentionTimeMillisProp()

    val joinWindow = JoinWindows.of(Duration.ofDays(31))

    createTaskStream.join(journalCreatedTaskStream, { produceTask, registerJournal ->
        RegisterTask.newBuilder().apply {
            this.produceTask = produceTask
            this.registerJournal = registerJournal
        }.build()
    }, joinWindow).to("privat-syfo-oppgave-registrerOppgave")

        return KafkaStreams(streamsBuilder.build(), streamProperties)
    }

    @DelicateCoroutinesApi
    fun createListener(
        applicationState: ApplicationState,
        action: suspend CoroutineScope.() -> Unit
    ): Job = GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

    @DelicateCoroutinesApi
    fun launchListeners(
        consumerProperties: Properties,
        applicationState: ApplicationState,
        oppgaveClient: OppgaveClient,
        streamProperties: Properties,
        env: Environment,
        kafkaRetryPublisher: KafkaRetryPublisher,
        oppgaveRetryService: OpprettOppgaveRetryService,

    ) {
        val kafkaStream = createKafkaStream(streamProperties, env)

        kafkaStream.setUncaughtExceptionHandler { err ->
            log.error("Onprem: Caught exception in stream: ${err.message}", err)
            kafkaStream.close(Duration.ofSeconds(30))
            applicationState.ready = false
            applicationState.alive = false
            throw err
        }

        kafkaStream.setStateListener { newState, oldState ->
            log.info("Onprem: From state={} to state={}", oldState, newState)

            if (newState == KafkaStreams.State.ERROR) {
                // if the stream has died there is no reason to keep spinning
                log.error("Onprem: Closing stream because it went into error state")
                kafkaStream.close(Duration.ofSeconds(30))
                log.error("Onprem: Restarter applikasjon")
                applicationState.ready = false
                applicationState.alive = false
            }
        }

        kafkaStream.start()

        createListener(applicationState) {
            val kafkaconsumerOppgave = KafkaConsumer<String, RegisterTask>(consumerProperties)
            kafkaconsumerOppgave.subscribe(listOf("privat-syfo-oppgave-registrerOppgave"))
            blockingApplicationLogic(applicationState, kafkaconsumerOppgave, oppgaveClient, kafkaRetryPublisher)
        }
        createListener(applicationState) {
            oppgaveRetryService.start()
        }
    }

    suspend fun blockingApplicationLogicAiven(
        applicationState: ApplicationState,
        kafkaConsumer: KafkaConsumer<String, RegistrerOppgaveKafkaMessage>,
        oppgaveClient: OppgaveClient,
        kafkaRetryPublisher: KafkaRetryPublisher
    ) {
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(10)).forEach {
                val produserOppgave = it.value().produserOppgave
                val journalOpprettet = it.value().journalOpprettet

                val loggingMeta = LoggingMeta(
                    orgNr = produserOppgave.orgnr, msgId = journalOpprettet.messageId, sykmeldingId = it.key()
                )
                handleRegisterOppgaveRequest(
                    oppgaveClient,
                    opprettOppgave(produserOppgave, journalOpprettet),
                    journalOpprettet.messageId,
                    loggingMeta,
                    kafkaRetryPublisher
                )
            }
        }
    }

    suspend fun blockingApplicationLogic(
        applicationState: ApplicationState,
        kafkaConsumer: KafkaConsumer<String, RegisterTask>,
        oppgaveClient: OppgaveClient,
        kafkaRetryPublisher: KafkaRetryPublisher
    ) {
        while (applicationState.ready) {
            kafkaConsumer.poll(Duration.ofSeconds(10)).forEach {
                val produceTask = it.value().produceTask
                val registerJournal = it.value().registerJournal

                val loggingMeta = LoggingMeta(
                    orgNr = produceTask.orgnr, msgId = registerJournal.messageId, sykmeldingId = it.key()
                )
                handleRegisterOppgaveRequest(
                    oppgaveClient,
                    opprettOppgave(produceTask, registerJournal),
                    registerJournal.messageId,
                    loggingMeta,
                    kafkaRetryPublisher,
                    source = "on-prem"
                )
            }
        }
    }
