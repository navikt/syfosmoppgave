package no.nav.syfo.retry

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockkClass
import io.mockk.mockkStatic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.OppgaveResultat
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.utility.DockerImageName
import java.util.Properties

class OpprettOppgaveRetryServiceTest : Spek({

    val kafka = KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_NAME).withTag(KAFKA_IMAGE_VERSION)).withNetwork(Network.newNetwork())
    kafka.start()
    fun setupKafkaConfig(): Properties {
        val kafkaConfig = Properties()
        kafkaConfig.let {
            it["bootstrap.servers"] = kafka.bootstrapServers
            it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
            it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
            it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = OppgaveKafkaDeserializer::class.java
            it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = OppgaveKafkaSerializer::class.java
            it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
            it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        }
        return kafkaConfig
    }

    val applicationState = ApplicationState(true, true)
    val oppgaveClient = mockkClass(OppgaveClient::class)
    beforeEachTest {
        mockkStatic("kotlinx.coroutines.DelayKt")
        coEvery { delay(any<Long>()) } returns Unit
        applicationState.alive = true
        applicationState.ready = true
    }

    afterEachTest {
        clearAllMocks()
    }
    val kafkaConfig = setupKafkaConfig()

    val kafkaProducerProperties = kafkaConfig.toProducerConfig(
        "test-producer", OppgaveKafkaSerializer::class
    )
    val kafkaProducer = KafkaProducer<String, OppgaveRetryKafkaMessage>(kafkaProducerProperties)

    val consumerProperties = kafkaConfig.toConsumerConfig("test-consumer", OppgaveKafkaDeserializer::class)
    consumerProperties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }
    val kafkaConsumer = KafkaConsumer<String, OppgaveRetryKafkaMessage>(consumerProperties, StringDeserializer(), OppgaveKafkaDeserializer())
    val service = OpprettOppgaveRetryService(kafkaConsumer, applicationState, oppgaveClient, "topic", "onprem")

    describe("Test retry") {
        it("Should read message from kafka and retry opprett oppgave") {
            coEvery { oppgaveClient.opprettOppgave(any(), any(), any()) } answers {
                applicationState.ready = false
                applicationState.alive = false
                OppgaveResultat(1, false)
            }

            kafkaProducer.send(ProducerRecord("topic", "messageId", getKafkaRetryMessage()))

            runBlocking {
                service.start()
            }

            coVerify(exactly = 1) { oppgaveClient.opprettOppgave(any(), any(), any()) }
        }
        it("Should retry failed") {
            coEvery { oppgaveClient.opprettOppgave(any(), any(), any()) } throws RuntimeException("error") andThenAnswer {
                applicationState.ready = false
                applicationState.alive = false
                OppgaveResultat(1, false)
            }
            kafkaProducer.send(ProducerRecord("topic", "messageId", getKafkaRetryMessage()))
            runBlocking {
                service.start()
            }
            coVerify(exactly = 2) { oppgaveClient.opprettOppgave(any(), any(), any()) }
        }
    }
})
