package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import kafka.server.KafkaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.TimeUnit

val log: Logger = LoggerFactory.getLogger("nav.syfo.oppgave")
val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = objectMapper.readValue<Credentials>(File("/var/run/secrets/nais.io/vault/credentials.json"))

    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)
    val applicationServer = ApplicationServer(applicationEngine)
    applicationServer.start()

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)
    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = GenericAvroSerde::class)
    val kafkaStream = createKafkaStream(streamProperties, env)

    kafkaStream.start()

    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }
    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient, httpClient)

    launchListeners(consumerProperties, applicationState, oppgaveClient)

    applicationState.ready = true
}

fun createKafkaStream(streamProperties: Properties, env: Environment): KafkaStreams {
    val streamsBuilder = StreamsBuilder()

    val journalCreatedTaskStream = streamsBuilder.stream<String, RegisterJournal>(env.journalCreatedTopic)
    val createTaskStream = streamsBuilder.stream<String, ProduceTask>(env.oppgaveTopic)
    KafkaConfig.LogRetentionTimeMillisProp()

    val joinWindow = JoinWindows.of(TimeUnit.DAYS.toMillis(14))
            .until(TimeUnit.DAYS.toMillis(31))

    createTaskStream.join(journalCreatedTaskStream, { produceTask, registerJournal ->
        RegisterTask.newBuilder().apply {
            this.produceTask = produceTask
            this.registerJournal = registerJournal
        }.build()
    }, joinWindow).to("privat-syfo-oppgave-registrerOppgave")

    return KafkaStreams(streamsBuilder.build(), streamProperties)
}

fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =  GlobalScope.launch {
    try {
        action()
    } catch (e: TrackableException) {
        log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", fields(e.loggingMeta), e.cause)
    } finally {
        applicationState.alive = false
    }
}

@KtorExperimentalAPI
fun launchListeners(
    consumerProperties: Properties,
    applicationState: ApplicationState,
    oppgaveClient: OppgaveClient
) {
        createListener(applicationState) {
            val kafkaconsumerOppgave = KafkaConsumer<String, RegisterTask>(consumerProperties)

            kafkaconsumerOppgave.subscribe(listOf("privat-syfo-oppgave-registrerOppgave"))
            blockingApplicationLogic(applicationState, kafkaconsumerOppgave, oppgaveClient)
        }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, RegisterTask>,
    oppgaveClient: OppgaveClient
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            val produceTask = it.value().produceTask
            val registerJournal = it.value().registerJournal

            val loggingMeta = LoggingMeta(
                    orgNr = produceTask.orgnr,
                    msgId = registerJournal.messageId,
                    sykmeldingId = it.key()
            )

            wrapExceptions(loggingMeta) {
                handleRegisterOppgaveRequest(oppgaveClient, produceTask, registerJournal, loggingMeta)
            }
        }
        delay(100)
    }
}

@KtorExperimentalAPI
suspend fun handleRegisterOppgaveRequest(
    oppgaveClient: OppgaveClient,
    produceTask: ProduceTask,
    registerJournal: RegisterJournal,
    loggingMeta: LoggingMeta
) {
    log.info("Received a SM2013, going to create oppgave, {}", fields(loggingMeta))
    val opprettOppgave = OpprettOppgave(
            tildeltEnhetsnr = produceTask.tildeltEnhetsnr,
            aktoerId = produceTask.aktoerId,
            opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
            journalpostId = registerJournal.journalpostId,
            behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
            saksreferanse = registerJournal.sakId,
            beskrivelse = produceTask.beskrivelse,
            tema = produceTask.tema,
            oppgavetype = produceTask.oppgavetype,
            aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
            fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
            prioritet = produceTask.prioritet.name
    )

    val oppgaveResultat = oppgaveClient.opprettOppgave(opprettOppgave, registerJournal.messageId, loggingMeta)
    if (!oppgaveResultat.duplikat) {
        OPPRETT_OPPGAVE_COUNTER.inc()
        log.info("Opprettet oppgave med {}, {}, {}, {} {}",
            keyValue("oppgaveId", oppgaveResultat.oppgaveId),
            keyValue("sakid", registerJournal.sakId),
            keyValue("journalpost", registerJournal.journalpostId),
            keyValue("tildeltEnhetsnr", produceTask.tildeltEnhetsnr),
            fields(loggingMeta))
    }
}
