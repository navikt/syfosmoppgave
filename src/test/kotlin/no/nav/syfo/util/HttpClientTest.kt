package no.nav.syfo.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson3.jackson

data class ResponseData(
    val content: String,
    val httpStatusCode: HttpStatusCode,
    val headers: Headers = headersOf("Content-Type", listOf("application/json")),
)

class HttpClientTest {

    val responseHandlers = HashMap<HttpMethod, ResponseData>()

    fun setResponseData(httpMethod: HttpMethod, responseData: ResponseData) {
        responseHandlers[httpMethod] = responseData
    }

    val httpClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) { jackson {} }
            engine {
                addHandler { request ->
                    if (responseHandlers.containsKey(request.method)) {
                        val responseData = responseHandlers[request.method]!!
                        respond(
                            responseData.content,
                            responseData.httpStatusCode,
                            responseData.headers,
                        )
                    } else {
                        respondError(HttpStatusCode.NotFound)
                    }
                }
            }
            expectSuccess = true
        }
}
