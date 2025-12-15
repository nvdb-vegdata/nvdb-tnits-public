package no.vegvesen.nvdb.tnits.generator.infrastructure.http

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.vegvesen.nvdb.tnits.generator.MainModule.Companion.commonConfig
import no.vegvesen.nvdb.tnits.generator.config.HttpConfig

class HttpClientRetryTest : ShouldSpec({

    val testHttpConfig = HttpConfig(logLevel = LogLevel.NONE)

    should("retry on 429 Too Many Requests with exponential backoff") {
        // Arrange
        var requestCount = 0
        val mockClient = HttpClient(MockEngine) {
            commonConfig(testHttpConfig)
            expectSuccess = false
            engine {
                addHandler { request ->
                    requestCount++
                    if (requestCount <= 2) {
                        respond(
                            content = """{"status": 429, "title": "Too Many Requests"}""",
                            status = HttpStatusCode.TooManyRequests,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """{"data": "success"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
        }

        // Act
        val response = mockClient.get("https://example.com/api/test")

        // Assert
        response.status shouldBe HttpStatusCode.OK
        requestCount shouldBe 3
    }

    should("retry on 500 Internal Server Error") {
        // Arrange
        var requestCount = 0
        val mockClient = HttpClient(MockEngine) {
            commonConfig(testHttpConfig)
            expectSuccess = false
            engine {
                addHandler { request ->
                    requestCount++
                    if (requestCount <= 2) {
                        respond(
                            content = """{"status": 500}""",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            content = """{"data": "success"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
        }

        // Act
        val response = mockClient.get("https://example.com/api/test")

        // Assert
        response.status shouldBe HttpStatusCode.OK
        requestCount shouldBe 3
    }
})
