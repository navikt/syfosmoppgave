package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import java.util.Properties
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.common.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.model.OppgaveResultat
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsConfig
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object CreateOppgaveITSpek : Spek({
    describe("A full bootstrapped environment") {
        val streamsApplicationName = "spek.integration"

        val journalOpprettetTopic = "aapen-syfo-oppgave-journalOpprettet"
        val produserOppgaveTopic = "aapen-syfo-oppgave-produserOppgave"
        val registrerOppgaveTopic = "privat-syfo-oppgave-registrerOppgave"

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
        applicationState.ready = true
        applicationState.alive = true

        val env = Environment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
                securityTokenServiceUrl = "http://localhost/sts_mock",
                oppgavebehandlingUrl = "http://localhost/oppgave_mock"
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

        val oppgaveClientMock = mockk<OppgaveClient>()
        val kafkaRetryPublisher = mockkClass(KafkaRetryPublisher::class)

        beforeGroup {
            cleanupDir(kafkaStreamsStateDir, streamsApplicationName)
            clearAllMocks()
            every { kafkaRetryPublisher.publishOppgaveToRetryTopic(any(), any(), any()) } returns Unit
            coEvery { oppgaveClientMock.opprettOppgave(any(), any(), any()) } returns OppgaveResultat(21312313, false)

            embeddedEnvironment.start()

            stream.start()

            GlobalScope.launch {
                blockingApplicationLogic(applicationState, registrerOppgaveConsumer, oppgaveClientMock, kafkaRetryPublisher)
            }
        }

        afterGroup {
            applicationState.alive = false
            stream.close()
            embeddedEnvironment.tearDown()
            deleteDir(kafkaStreamsStateDir)
        }

        it("Producing a RegisterJournal and ProduceTask message should result in a call to the oppgave rest endpoint") {
            val msgId = "id123"
            val registerJournal = createRegisterJournal(msgId)
            val produceTask = createProduceTask(msgId)

            journalOpprettet.send(ProducerRecord(journalOpprettetTopic, msgId, registerJournal))
            produserOppgave.send(ProducerRecord(produserOppgaveTopic, msgId, produceTask))

            coVerify(exactly = 1, timeout = 10_000) {
                oppgaveClientMock.opprettOppgave(coMatch {
                    it.journalpostId == registerJournal.journalpostId &&
                            it.aktoerId == produceTask.aktoerId &&
                            it.oppgavetype == produceTask.oppgavetype &&
                            it.tema == produceTask.tema &&
                            it.saksreferanse == registerJournal.sakId
                }, msgId, any())
            }
        }
    }
})
