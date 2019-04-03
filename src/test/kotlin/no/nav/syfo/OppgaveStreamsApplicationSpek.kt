package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.toStreamsConfig
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsConfig
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.Properties

object OppgaveStreamsApplicationSpek : Spek({
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
                withSchemaRegistry = true,
                noOfBrokers = 1
        )

        fun Properties.overrideForTest(): Properties = apply {
            remove("security.protocol")
            remove("sasl.mechanism")
            put("schema.registry.url", embeddedEnvironment.schemaRegistry!!.url)
            put(StreamsConfig.STATE_DIR_CONFIG, kafkaStreamsStateDir.toAbsolutePath().toString())
        }

        val env = Environment(
                kafkaBootstrapServers = embeddedEnvironment.brokersURL,
                oppgavebehandlingUrl = "UNUSED",
                securityTokenServiceUrl = "UNUSED"
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

        beforeGroup {
            cleanupDir(kafkaStreamsStateDir, streamsApplicationName)
            embeddedEnvironment.start()
            stream.start()
        }

        afterGroup {
            stream.close()
            embeddedEnvironment.tearDown()
            deleteDir(kafkaStreamsStateDir)
        }

        val journalOpprettet = KafkaProducer<String, RegisterJournal>(producerProperties)
        val produserOppgave = KafkaProducer<String, ProduceTask>(producerProperties)
        val registrerOppgaveConsumer = KafkaConsumer<String, RegisterTask>(consumerProperties)
        registrerOppgaveConsumer.subscribe(listOf(registrerOppgaveTopic))

        it("Producing a RegisterJournal and ProduceTask message should result in a RegisterTask") {
            val sykmeldingId = "id123"
            val registerJournal = createRegisterJournal(sykmeldingId)
            val produceTask = createProduceTask(sykmeldingId)

            journalOpprettet.send(ProducerRecord(journalOpprettetTopic, sykmeldingId, registerJournal))
            produserOppgave.send(ProducerRecord(produserOppgaveTopic, sykmeldingId, produceTask))
            val result = registrerOppgaveConsumer.poll(Duration.ofMillis(30000)).first().value()

            result.produceTask shouldEqual produceTask
            result.registerJournal shouldEqual registerJournal
        }
    }
})
