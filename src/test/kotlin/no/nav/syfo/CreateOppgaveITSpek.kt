package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.nhaarman.mockitokotlin2.timeout
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.common.KafkaEnvironment
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.model.OidcToken
import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.amshove.kluent.mock
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsConfig
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.net.ServerSocket
import java.util.Properties
import java.util.concurrent.TimeUnit

@KtorExperimentalAPI
object CreateOppgaveITSpek : Spek({
    describe("A full bootstrapped environment") {
        val streamsApplicationName = "spek.integration"
        val oppgaveMock = mock<() -> OpprettOppgaveResponse>()

        val mockPort = ServerSocket(0).use {
            it.localPort
        }

        val journalOpprettetTopic = "aapen-syfo-oppgave-journalOpprettet"
        val produserOppgaveTopic = "aapen-syfo-oppgave-produserOppgave"
        val registrerOppgaveTopic = "privat-syfo-oppgave-registrerOppgave"

        val mockWebServer = embeddedServer(Netty, mockPort) {
            install(ContentNegotiation) {
                jackson {
                    registerKotlinModule()
                    registerModule(JavaTimeModule())
                    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                }
            }
            routing {
                post("/oppgave_mock") {
                    call.respond(oppgaveMock())
                }

                get("/sts_mock") {
                    call.respond(OidcToken(
                            access_token = "abcdef",
                            token_type = "ghijk",
                            expires_in = 3600
                    ))
                }
            }
        }.start(wait = false)

        val embeddedEnvironment = KafkaEnvironment(
                topicNames = listOf(
                        journalOpprettetTopic,
                        produserOppgaveTopic,
                        registrerOppgaveTopic
                ),
                withSchemaRegistry = true
        )

        fun Properties.overrideForTest(): Properties = apply {
            remove("security.protocol")
            remove("sasl.mechanism")
            put("schema.registry.url", embeddedEnvironment.schemaRegistry!!.url)
            put(StreamsConfig.STATE_DIR_CONFIG, kafkaStreamsStateDir.toAbsolutePath().toString())
        }

        val applicationState = ApplicationState()

        val env = Environment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
                oppgavebehandlingUrl = "http://localhost:$mockPort/oppgave_mock",
                securityTokenServiceUrl = "http://localhost:$mockPort/sts_mock"
        )

        val credentials = Credentials("UNUSED", "UNUSED")

        val baseConfig = loadBaseConfig(env, credentials).overrideForTest()

        val consumerProperties = baseConfig
                .toConsumerConfig("spek.integration-consumer", valueDeserializer = KafkaAvroDeserializer::class)

        val producerProperties = baseConfig
                .toProducerConfig("spek.integration", valueSerializer = KafkaAvroSerializer::class)

        val streamProperties = baseConfig
                .toStreamsConfig(streamsApplicationName, valueSerde = GenericAvroSerde::class)

        val stream = createKafkaStream(streamProperties, env)

        val journalOpprettet = KafkaProducer<String, RegisterJournal>(producerProperties)
        val produserOppgave = KafkaProducer<String, ProduceTask>(producerProperties)
        val registrerOppgaveConsumer = KafkaConsumer<String, RegisterTask>(consumerProperties)
        registrerOppgaveConsumer.subscribe(listOf(registrerOppgaveTopic))

        val stsClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl)
        val oppgaveClient = OppgaveClient(env.oppgavebehandlingUrl, stsClient)

        beforeGroup {
            cleanupDir(kafkaStreamsStateDir, streamsApplicationName)
            embeddedEnvironment.start()

            stream.start()

            GlobalScope.launch {
                blockingApplicationLogic(applicationState, registrerOppgaveConsumer, oppgaveClient)
            }
        }

        afterGroup {
            applicationState.running = false
            stream.close()
            mockWebServer.stop(10, 10, TimeUnit.SECONDS)
            embeddedEnvironment.tearDown()
            deleteDir(kafkaStreamsStateDir)
        }

        it("Producing a RegisterJournal and ProduceTask message should result in a call to the oppgave rest endpoint") {
            val msgId = "id123"
            val registerJournal = createRegisterJournal(msgId)
            val produceTask = createProduceTask(msgId)

            whenever(oppgaveMock()).thenReturn(createOppgaveResponse())

            journalOpprettet.send(ProducerRecord(journalOpprettetTopic, msgId, registerJournal))
            produserOppgave.send(ProducerRecord(produserOppgaveTopic, msgId, produceTask))

            verify(oppgaveMock, timeout(10000).times(1))()
        }
    }
})
