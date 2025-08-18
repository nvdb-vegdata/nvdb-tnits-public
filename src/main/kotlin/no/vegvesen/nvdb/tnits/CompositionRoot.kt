package no.vegvesen.nvdb.tnits

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.vegvesen.nvdb.tnits.services.UberiketApi

private fun ObjectMapper.initialize(): ObjectMapper =
    apply {
        findAndRegisterModules()
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
    }

val objectMapper: ObjectMapper = ObjectMapper().initialize()

val httpClient =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                initialize()
            }
        }
        defaultRequest {
            url("https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/")
            headers.append("Accept", "application/json, application/x-ndjson")
            headers.append("X-Client", "nvdb-tnits-console")
        }
    }

val uberiketApi = UberiketApi(httpClient)
