package no.nav.syfo.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

class HttpClientTest {

    val responseHandlers = HashMap<HttpMethod, HttpResponseData>()

    fun setResponseData(httpMethod: HttpMethod, responseData: HttpResponseData) {
        responseHandlers[httpMethod] = responseData
    }

    val httpClient = HttpClient(MockEngine) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        engine {
            addHandler { request ->
                if (responseHandlers.containsKey(request.method)) {
                    responseHandlers[request.method]!!
                } else {
                    respondError(HttpStatusCode.NotFound)
                }
            }
        }
    }
}
