package no.nav.syfo.service

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.syfo.LoggingMeta
import no.nav.syfo.client.OidcToken
import no.nav.syfo.client.OppgaveClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.createProduceTask
import no.nav.syfo.createRegisterJournal
import no.nav.syfo.model.OppgaveResponse
import no.nav.syfo.model.OpprettOppgaveResponse
import no.nav.syfo.objectMapper
import no.nav.syfo.retry.KafkaRetryPublisher
import no.nav.syfo.util.HttpClientTest
import no.nav.syfo.util.ResponseData
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OppgaveserviceKtTest : Spek({

    val stsOidcClient = mockkClass(StsOidcClient::class)

    val httpClientTest = HttpClientTest()

    val oppgaveClient = OppgaveClient("url", stsOidcClient, httpClientTest.httpClient)
    val kafkaRetryPublisher = mockkClass(KafkaRetryPublisher::class)

    beforeEachTest {
        every { kafkaRetryPublisher.publishOppgaveToRetryTopic(any(), any(), any()) } returns Unit
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("token", "jwt", 10_000L)
    }

    afterEachTest {
        clearAllMocks()
    }
    describe("test OpprettOppgave") {
        it("Opprett oppgave OK") {
            httpClientTest.setResponseData(HttpMethod.Get, ResponseData(objectMapper.writeValueAsString(OppgaveResponse(0, emptyList())), HttpStatusCode.OK))
            httpClientTest.setResponseData(HttpMethod.Post, ResponseData(objectMapper.writeValueAsString(OpprettOppgaveResponse(0)), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            val registerJournal = createRegisterJournal("msgId")
            val produceTask = createProduceTask("msgId")
            runBlocking {
                handleRegisterOppgaveRequest(oppgaveClient, opprettOppgave(produceTask, registerJournal), registerJournal.messageId, LoggingMeta("", "", ""), kafkaRetryPublisher)
            }
            verify(exactly = 0) { kafkaRetryPublisher.publishOppgaveToRetryTopic(any(), any(), any()) }
        }
        it("Opprett oppgave feiler med status 500 -> publisert til retrytopic") {
            httpClientTest.setResponseData(HttpMethod.Get, ResponseData(objectMapper.writeValueAsString(OppgaveResponse(0, emptyList())), HttpStatusCode.OK, headersOf("Content-Type", "application/json")))
            httpClientTest.setResponseData(HttpMethod.Post, ResponseData("", HttpStatusCode.InternalServerError, headersOf()))
            val registerJournal = createRegisterJournal("msgId")
            val produceTask = createProduceTask("msgId")
            runBlocking {
                handleRegisterOppgaveRequest(oppgaveClient, opprettOppgave(produceTask, registerJournal), registerJournal.messageId, LoggingMeta("", "", ""), kafkaRetryPublisher)
            }
            verify(exactly = 1) { kafkaRetryPublisher.publishOppgaveToRetryTopic(any(), any(), any()) }
        }
    }
})
