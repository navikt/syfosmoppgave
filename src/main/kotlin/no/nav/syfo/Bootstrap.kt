package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kafka.server.KafkaConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.metrics.OPPRETT_OPPGAVE_COUNTER
import no.nav.syfo.model.Ident
import no.nav.syfo.model.IdentType
import no.nav.syfo.model.Oppgavestatus
import no.nav.syfo.model.Oppgavestatuskategori
import no.nav.syfo.model.OpprettOppgave
import no.nav.syfo.model.Prioritet
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.oppgave")
val objectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()

@KtorExperimentalAPI
fun main() = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val env = Environment()
    val credentials = objectMapper.readValue<Credentials>(Files.newInputStream(Paths.get("/var/run/secrets/nais.io/vault/credentials.json")))
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    val kafkaBaseConfig = loadBaseConfig(env, credentials)
    val consumerProperties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)
    val streamProperties = kafkaBaseConfig.toStreamsConfig(env.applicationName, valueSerde = GenericAvroSerde::class)
    val kafkaStream = createKafkaStream(streamProperties, env)

    kafkaStream.start()

    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, oidcClient)

    launchListeners(env, consumerProperties, applicationState, oppgaveClient)

    Runtime.getRuntime().addShutdownHook(Thread {
        kafkaStream.close()
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
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

@KtorExperimentalAPI
fun CoroutineScope.launchListeners(
    env: Environment,
    consumerProperties: Properties,
    applicationState: ApplicationState,
    oppgaveClient: OppgaveClient
) {
    try {
        val listeners = (1..env.applicationThreads).map {
            launch {

                val kafkaConsumer = KafkaConsumer<String, RegisterTask>(consumerProperties)

                kafkaConsumer.subscribe(listOf("privat-syfo-oppgave-registrerOppgave"))

                blockingApplicationLogic(applicationState, kafkaConsumer, oppgaveClient)
            }
        }.toList()

        applicationState.initialized = true
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, RegisterTask>,
    oppgaveClient: OppgaveClient
) {
    while (applicationState.running) {
        var logValues = arrayOf(
                keyValue("orgNr", "missing"),
                keyValue("msgId", "missing"),
                keyValue("sykmeldingId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            try {
                val produceTask = it.value().produceTask
                val registerJournal = it.value().registerJournal
                logValues = arrayOf(
                        keyValue("sykmeldingId", it.key()),
                        keyValue("orgNr", produceTask.orgnr),
                        keyValue("msgId", registerJournal.messageId)
                )
                log.info("Received a SM2013, going to create task, $logKeys", *logValues)
                log.info("Creating task, $logKeys", *logValues)
                val opprettOppgave = OpprettOppgave(
                        tildeltEnhetsnr = produceTask.tildeltEnhetsnr,
                        ident = Ident(IdentType.AKTOERID, produceTask.aktoerId),
                        opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
                        journalpostId = registerJournal.journalpostId,
                        journalpostkilde = registerJournal.journalpostKilde,
                        behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
                        saksreferanse = registerJournal.sakId,
                        beskrivelse = produceTask.beskrivelse,
                        temagruppe = produceTask.temagruppe,
                        tema = produceTask.tema,
                        behandlingstema = produceTask.behandlingstema,
                        oppgavetype = produceTask.oppgavetype,
                        behandlingstype = produceTask.behandlingstype,
                        versjon = 1,
                        mappeId = produceTask.mappeId.toLong(),
                        aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
                        opprettetTidspunkt = LocalDateTime.now(),
                        endretTidspunkt = LocalDateTime.now(),
                        fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
                        opprettetAv = "srvsyfosmoppgave",
                        status = Oppgavestatus.OPPRETTET,
                        statuskategori = Oppgavestatuskategori.AAPEN,
                        ferdigstiltTidspunkt = LocalDateTime.now().plusDays(14),
                        prioritet = Prioritet.NORM,
                        metadata = mapOf()
                )
                log.info("opprettOppgave request: ${objectMapper.writeValueAsString(opprettOppgave)}")

                val response = oppgaveClient.createOppgave(opprettOppgave).await()
                OPPRETT_OPPGAVE_COUNTER.inc()
                log.info("Task created with {} $logKeys", keyValue("oppgaveId", response.id), *logValues)
            } catch (e: Exception) {
                log.error("Caught exception $logKeys", *logValues, e)
            }
        }
        delay(100)
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = { applicationState.initialized },
                livenessCheck = { applicationState.running }
        )
    }
}
