package no.nav.syfo

import io.ktor.util.KtorExperimentalAPI
import org.spekframework.spek2.Spek

@KtorExperimentalAPI
object CreateOppgaveITSpek : Spek({
    /*
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
                oppgavebehandlingUrl = "http://localhost/oppgave_mock",
                securityTokenServiceUrl = "http://localhost/sts_mock"
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

        beforeGroup {
            cleanupDir(kafkaStreamsStateDir, streamsApplicationName)
            clearAllMocks()

            coEvery { oppgaveClientMock.opprettOppgave(any(), any(), any()) } returns OppgaveResultat(21312313, false)

            embeddedEnvironment.start()

            stream.start()

            GlobalScope.launch {
                blockingApplicationLogic(applicationState, registrerOppgaveConsumer, oppgaveClientMock)
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

            coVerify(exactly = 1, timeout = 10000) {
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
     */
})
