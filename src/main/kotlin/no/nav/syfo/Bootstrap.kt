package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.azuread.AccessTokenClient
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.RegistrerOppgaveKafkaMessage
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.retry.OppgaveKafkaDeserializer
import no.nav.syfo.retry.OppgaveKafkaSerializer
import no.nav.syfo.retry.OppgaveRetryKafkaMessage
import no.nav.syfo.retry.OpprettOppgaveRetryService
import no.nav.syfo.service.handleRegisterOppgaveRequest
import no.nav.syfo.service.opprettOppgave
import no.nav.syfo.util.JacksonKafkaDeserializer
import no.nav.syfo.util.Unbounded
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.smoppgave")
val securelog: Logger = LoggerFactory.getLogger("securelog")
val objectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(env, applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    DefaultExports.initialize()

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn(
                        "Retrying for status code ${response.status.value}, for url ${request.url}",
                    )
                    true
                } else {
                    false
                }
            }
        }
        expectSuccess = true
    }
    val httpClient = HttpClient(Apache, config)

    val accessTokenClient =
        AccessTokenClient(env.aadAccessTokenUrl, env.clientId, env.clientSecret, httpClient)
    val oppgaveClient =
        OppgaveClient(env.oppgavebehandlingUrl, accessTokenClient, env.oppgaveScope, httpClient)

    setupAndRunAiven(env, applicationState, oppgaveClient)

    applicationServer.start()
}

@DelicateCoroutinesApi
fun setupAndRunAiven(
    env: Environment,
    applicationState: ApplicationState,
    oppgaveClient: OppgaveClient
) {
    val aivenProperties = KafkaUtils.getAivenKafkaConfig()
    val aivenRetryConsumer =
        KafkaConsumer<String, OppgaveRetryKafkaMessage>(
            aivenProperties
                .toConsumerConfig(
                    "${env.applicationName}-consumer",
                    keyDeserializer = StringDeserializer::class,
                    valueDeserializer = OppgaveKafkaDeserializer::class,
                )
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" },
        )
    val aivenRegistrerOppgaveConsumer =
        KafkaConsumer(
            aivenProperties
                .toConsumerConfig(
                    "${env.applicationName}-consumer",
                    valueDeserializer = JacksonKafkaDeserializer::class,
                )
                .also { it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none" },
            StringDeserializer(),
            JacksonKafkaDeserializer(RegistrerOppgaveKafkaMessage::class),
        )

    val aivenRetryProducer =
        KafkaProducer<String, OppgaveRetryKafkaMessage>(
            KafkaUtils.getAivenKafkaConfig()
                .toProducerConfig(
                    "${env.applicationName}-retry-producer",
                    keySerializer = StringSerializer::class,
                    valueSerializer = OppgaveKafkaSerializer::class,
                ),
        )
    val aivenRetryPublisher = KafkaRetryPublisher(aivenRetryProducer, env.retryOppgaveAivenTopic)

    createListener(applicationState) {
        aivenRegistrerOppgaveConsumer.subscribe(listOf(env.privatRegistrerOppgave))
        blockingApplicationLogicAiven(
            applicationState,
            aivenRegistrerOppgaveConsumer,
            oppgaveClient,
            aivenRetryPublisher,
        )
    }
    createListener(applicationState) {
        OpprettOppgaveRetryService(
                aivenRetryConsumer,
                applicationState,
                oppgaveClient,
                env.retryOppgaveAivenTopic,
                "aiven",
                env.cluster,
            )
            .start()
    }
}

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit,
): Job =
    GlobalScope.launch(Dispatchers.Unbounded) {
        try {
            action()
        } catch (e: TrackableException) {
            log.error(
                "En uhåndtert feil oppstod, applikasjonen restarter {}",
                fields(e.loggingMeta),
                e.cause,
            )
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }

suspend fun blockingApplicationLogicAiven(
    applicationState: ApplicationState,
    kafkaConsumer: KafkaConsumer<String, RegistrerOppgaveKafkaMessage>,
    oppgaveClient: OppgaveClient,
    kafkaRetryPublisher: KafkaRetryPublisher,
) {
    while (applicationState.ready) {
        kafkaConsumer.poll(Duration.ofSeconds(10)).forEach {
            val produserOppgave = it.value().produserOppgave
            val journalOpprettet = it.value().journalOpprettet

            val loggingMeta =
                LoggingMeta(
                    orgNr = produserOppgave.orgnr,
                    msgId = journalOpprettet.messageId,
                    sykmeldingId = it.key(),
                )
            handleRegisterOppgaveRequest(
                oppgaveClient,
                opprettOppgave(produserOppgave, journalOpprettet),
                journalOpprettet.messageId,
                loggingMeta,
                kafkaRetryPublisher,
            )
        }
    }
}
