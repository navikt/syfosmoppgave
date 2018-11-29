package no.nav.syfo

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
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import no.nav.syfo.ws.configureSTSFor
import no.nav.tjeneste.virksomhet.oppgavebehandling.v3.OppgavebehandlingV3
import no.nav.tjeneste.virksomhet.oppgavebehandling.v3.meldinger.WSOpprettOppgave
import no.nav.tjeneste.virksomhet.oppgavebehandling.v3.meldinger.WSOpprettOppgaveRequest
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.apache.cxf.ext.logging.LoggingFeature
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.JoinWindows
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

data class ApplicationState(var running: Boolean = true, var initialized: Boolean = false)

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

private val log = LoggerFactory.getLogger("nav.syfo.oppgave")

fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(2).asCoroutineDispatcher()) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    try {
        val listeners = (1..env.applicationThreads).map {
            launch {
                val oppgavebehandlingV3 = JaxWsProxyFactoryBean().apply {
                    address = env.virksomhetOppgavebehandlingV3Endpointurl
                    features.add(LoggingFeature())
                    features.add(WSAddressingFeature())
                    serviceClass = OppgavebehandlingV3::class.java
                }.create() as OppgavebehandlingV3
                configureSTSFor(oppgavebehandlingV3, env.srvsyfosmoppgaveUsername,
                        env.srvsyfosmoppgavePassword, env.securityTokenServiceUrl)

                val consumerProperties = readConsumerConfig(env, valueDeserializer = KafkaAvroDeserializer::class)

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

                blockingApplicationLogic(applicationState, oppgavebehandlingV3, kafkaConsumer)
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
    oppgavebehandlingV3: OppgavebehandlingV3,
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
            oppgavebehandlingV3.opprettOppgave(WSOpprettOppgaveRequest()
                    .withOpprettetAvEnhetId(9999)
                    .withOpprettOppgave(WSOpprettOppgave()
                            .withBrukerId(produceTask.getUserIdent())
                            .withBrukertypeKode(produceTask.getUserTypeCode())
                            .withOppgavetypeKode(produceTask.getTaskType())
                            .withFagomradeKode(produceTask.getFieldCode())
                            .withUnderkategoriKode(produceTask.getSubcategoryType())
                            .withPrioritetKode(produceTask.getPriorityCode())
                            .withBeskrivelse(produceTask.getDescription())
                            .withAktivFra(LocalDate.now().plusDays(produceTask.getStartsInDays().toLong()))
                            .withAktivTil(LocalDate.now().plusDays(produceTask.getEndsInDays().toLong()))
                            .withAnsvarligEnhetId(produceTask.getResponsibleUnit())
                            .withDokumentId(registerJournal.getDocumentId())
                            .withMottattDato(LocalDate.now())
                            .withSaksnummer(registerJournal.getCaseId())
                            // TODO: .withOppfolging()
                    ))
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

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "ENH"
        }
