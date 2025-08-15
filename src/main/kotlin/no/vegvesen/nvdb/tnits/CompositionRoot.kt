package no.vegvesen.nvdb.tnits

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.vegvesen.nvdb.tnits.services.UberiketApi

val objectMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()

val httpClient =
    HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            jackson {
                findAndRegisterModules()
            }
        }
        defaultRequest {
            url("https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/")
            headers.append("Accept", "application/json, application/x-ndjson")
            headers.append("X-Client", "nvdb-tnits-console")
        }
    }

val uberiketApi = UberiketApi(httpClient)
