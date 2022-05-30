package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson

data class ResponseData(val content: String, val httpStatusCode: HttpStatusCode, val headers: Headers = headersOf("Content-Type", listOf("application/json")))

class HttpClientTest {

    val responseHandlers = HashMap<HttpMethod, ResponseData>()

    fun setResponseData(httpMethod: HttpMethod, responseData: ResponseData) {
        responseHandlers[httpMethod] = responseData
    }

    val httpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            addHandler { request ->
                if (responseHandlers.containsKey(request.method)) {
                    val responseData = responseHandlers[request.method]!!
                    respond(responseData.content, responseData.httpStatusCode, responseData.headers)
                } else {
                    respondError(HttpStatusCode.NotFound)
                }
            }
        }
        expectSuccess = true
    }
}
