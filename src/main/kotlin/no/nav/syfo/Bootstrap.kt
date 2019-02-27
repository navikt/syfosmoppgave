package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.model.OpprettOppgave
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
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

private val log = LoggerFactory.getLogger("nav.syfo.oppgave")
val objectMapper: ObjectMapper = ObjectMapper()

fun main() = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val env = Environment()
    val credentials = objectMapper.readValue<Credentials>(Files.newInputStream(Paths.get("/")))
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val consumerProperties = readConsumerConfig(env, credentials, valueDeserializer = KafkaAvroDeserializer::class)

                val kafkaConsumer = KafkaConsumer<String, RegisterTask>(consumerProperties)

                val streamsBuilder = StreamsBuilder()

                val journalCreatedTaskStream = streamsBuilder.stream<String, RegisterJournal>("aapen-syfo-oppgave-journalOpprettet")
                val createTaskStream = streamsBuilder.stream<String, ProduceTask>("aapen-syfo-oppgave-produserOppgave")

                createTaskStream
                        .leftJoin(journalCreatedTaskStream, { produceTask, registerJournal ->
                            RegisterTask.newBuilder().apply {
                                this.produceTask = produceTask
                                this.registerJournal = registerJournal
                            }.build()
                        }, JoinWindows.of(TimeUnit.DAYS.convert(30, TimeUnit.MILLISECONDS)))
                        .to("privat-syfo-oppgave-registrerOppgave")

                val streams = KafkaStreams(streamsBuilder.build(), consumerProperties)
                streams.start()

                kafkaConsumer.subscribe(listOf("privat-syfo-oppgave-registrerOppgave"))

                blockingApplicationLogic(applicationState, kafkaConsumer)
            }
        }.toList()

        applicationState.initialized = true

        Runtime.getRuntime().addShutdownHook(Thread {
            applicationServer.stop(10, 10, TimeUnit.SECONDS)
        })
        runBlocking { listeners.forEach { it.join() } }
    } finally {
        applicationState.running = false
    }
}

suspend fun blockingApplicationLogic(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, RegisterTask>
) {
    while (applicationState.running) {
        var logValues = arrayOf(
                keyValue("smId", "missing"),
                keyValue("orgNr", "missing"),
                keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
        }

        kafkaConsumer.poll(Duration.ofMillis(0)).forEach {
            log.info("Recived a kafka message:")
            val produceTask = it.value().produceTask
            val registerJournal = it.value().registerJournal
            logValues = arrayOf(
                    keyValue("smId", it.key()),
                    keyValue("msgId", it.key())
            )
            log.info("Received a SM2013, going to create task, $logKeys", *logValues)
            log.info("Creating task")
            val opprettOppgave = OpprettOppgave(
                    tildeltEnhetsnr = "9999", // TODO
                    aktoerId = produceTask.aktoerId,
                    opprettetAvEnhetsnr = produceTask.opprettetAvEnhetsnr,
                    journalpostId = registerJournal.journalpostId,
                    journalpostkilde = registerJournal.journalpostKilde,
                    behandlesAvApplikasjon = produceTask.behandlesAvApplikasjon,
                    saksreferanse = produceTask.behandlesAvApplikasjon,
                    orgnr = produceTask.orgnr,
                    beskrivelse = produceTask.beskrivelse,
                    temagruppe = produceTask.temagruppe,
                    tema = produceTask.tema,
                    behandlingstema = produceTask.behandlingstema,
                    oppgavetype = produceTask.oppgavetype,
                    behandlingstype = produceTask.behandlingstype,
                    mappeId = produceTask.mappeId,
                    aktivDato = LocalDate.parse(produceTask.aktivDato, DateTimeFormatter.ISO_DATE),
                    fristFerdigstillelse = LocalDate.parse(produceTask.fristFerdigstillelse, DateTimeFormatter.ISO_DATE),
                    prioritet = produceTask.prioritet.name,
                    metadata = produceTask.metadata
            )
            log.info(opprettOppgave.toString())
        }
        delay(100)
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(
                readynessCheck = {
                    applicationState.initialized
                },
                livenessCheck = {
                    applicationState.running
                }
        )
    }
}
