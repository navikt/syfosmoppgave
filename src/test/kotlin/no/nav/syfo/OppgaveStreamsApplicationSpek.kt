package no.nav.syfo

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import no.nav.common.KafkaEnvironment
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sak.avro.RegisterJournal
import no.nav.syfo.sak.avro.RegisterTask
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Duration
import java.util.Properties

object OppgaveStreamsApplicationSpek : Spek({
    describe("A full bootstrapped environment") {
        val journalOpprettetTopic = "aapen-syfo-oppgave-journalOpprettet"
        val produserOppgaveTopic = "aapen-syfo-oppgave-produserOppgave"
        val registrerOppgaveTopic = "privat-syfo-oppgave-registrerOppgave"

        val embeddedEnvironment = KafkaEnvironment(
                topics = listOf(
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
                .toStreamsConfig("spek.integration", valueSerde = GenericAvroSerde::class)

        val stream = createKafkaStream(streamProperties)

        beforeGroup {
            embeddedEnvironment.start()

            stream.start()
        }

        afterGroup {
            stream.close()
            embeddedEnvironment.tearDown()
        }

        val journalOpprettet = KafkaProducer<String, RegisterJournal>(producerProperties)
        val produserOppgave = KafkaProducer<String, ProduceTask>(producerProperties)
        val registrerOppgaveConsumer = KafkaConsumer<String, RegisterTask>(consumerProperties)
        registrerOppgaveConsumer.subscribe(listOf(registrerOppgaveTopic))

        it("Producing a RegisterJournal and ProduceTask message should result in a RegisterTask") {
            val msgId = "id123"
            val registerJournal = createRegisterJournal(msgId)
            val produceTask = createProduceTask(msgId)

            journalOpprettet.send(ProducerRecord(journalOpprettetTopic, msgId, registerJournal))
            produserOppgave.send(ProducerRecord(produserOppgaveTopic, msgId, produceTask))
            val result = registrerOppgaveConsumer.poll(Duration.ofMillis(10000)).first().value()

            result.produceTask shouldEqual produceTask
            result.registerJournal shouldEqual registerJournal
        }
    }
})
