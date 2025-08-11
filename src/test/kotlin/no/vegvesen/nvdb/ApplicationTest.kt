package no.vegvesen.nvdb

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ApplicationTest : StringSpec({
    
    "health endpoint should return OK" {
        testApplication {
            application {
                module()
            }
            
            val response = client.get("/health")
            response.status shouldBe HttpStatusCode.OK
            
            val responseBody = response.bodyAsText()
            val json = Json.parseToJsonElement(responseBody).jsonObject
            json["status"]?.jsonPrimitive?.content shouldBe "OK"
        }
    }
    
    "initial load endpoint should respond" {
        testApplication {
            application {
                module()
            }
            
            val response = client.post("/api/initial-load")
            response.status shouldBe HttpStatusCode.OK
            
            val responseBody = response.bodyAsText()
            responseBody shouldContain "Initial load completed successfully"
        }
    }
    
    "full snapshot endpoint should respond" {
        testApplication {
            application {
                module()
            }
            
            val response = client.get("/api/snapshot/full")
            response.status shouldBe HttpStatusCode.OK
            
            val responseBody = response.bodyAsText()
            responseBody shouldContain "FULL"
        }
    }
    
    "daily snapshot endpoint should require date parameter" {
        testApplication {
            application {
                module()
            }
            
            val response = client.get("/api/snapshot/daily/2024-01-01")
            response.status shouldBe HttpStatusCode.OK
            
            val responseBody = response.bodyAsText()
            responseBody shouldContain "INCREMENTAL"
        }
    }
})