package no.vegvesen.nvdb.tnits

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import no.vegvesen.nvdb.tnits.services.DatakatalogApi
import no.vegvesen.nvdb.tnits.services.UberiketApi
import no.vegvesen.nvdb.tnits.storage.RocksDbVeglenkerStore
import org.openlr.encoder.EncoderFactory
import org.openlr.location.LocationFactory

private fun ObjectMapper.initialize(): ObjectMapper =
    apply {
        findAndRegisterModules()
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
    }

val objectMapper: ObjectMapper = ObjectMapper().initialize()

val uberiketHttpClient =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                initialize()
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000 // 60 seconds
            connectTimeoutMillis = 10_000 // 10 seconds
            socketTimeoutMillis = 60_000 // 60 seconds
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 5)
            retryOnException(maxRetries = 5, retryOnTimeout = true)
            exponentialDelay(base = 2.0, maxDelayMs = 30_000) // Max 30 second delay

            // Enhanced exception handling - this should catch ClosedByteChannelException
            retryOnExceptionIf(maxRetries = 5) { _, cause ->
                val shouldRetry =
                    when {
                        cause.message?.contains("Invalid chunk", ignoreCase = true) == true -> {
                            println("Retrying due to chunked transfer error: ${cause.message}")
                            true
                        }
                        cause.message?.contains("ended unexpectedly", ignoreCase = true) == true -> {
                            println("Retrying due to connection termination: ${cause.message}")
                            true
                        }
                        cause.message?.contains("ClosedByteChannel", ignoreCase = true) == true -> {
                            println("Retrying due to closed byte channel: ${cause.message}")
                            true
                        }
                        cause.javaClass.simpleName.contains("EOFException") -> {
                            println("Retrying due to EOF exception: ${cause.message}")
                            true
                        }
                        cause.javaClass.simpleName.contains("Timeout") -> {
                            println("Retrying due to timeout: ${cause.message}")
                            true
                        }
                        else -> false
                    }
                shouldRetry
            }

            modifyRequest { request ->
                println("Retrying API request to ${request.url}")
            }
        }
        defaultRequest {
            url("https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/")
            headers.append("Accept", "application/json, application/x-ndjson")
            headers.append("X-Client", "nvdb-tnits-console")
        }
    }

val uberiketApi = UberiketApi(uberiketHttpClient)

val datakatalogHttpClient =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                initialize()
            }
        }
        defaultRequest {
            url("https://nvdbapiles.atlas.vegvesen.no/datakatalog/api/v1/")
            headers.append("Accept", "application/json")
            headers.append("X-Client", "nvdb-tnits-console")
        }
    }

val datakatalogApi = DatakatalogApi(datakatalogHttpClient)

val veglenkerStore = RocksDbVeglenkerStore()

val openLrEncoder = EncoderFactory().create()

val locationFactory = LocationFactory()
