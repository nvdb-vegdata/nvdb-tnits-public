package no.vegvesen.nvdb.tnits.katalog.presentation

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.katalog.config.AppConfiguration
import no.vegvesen.nvdb.tnits.katalog.config.InstantConverter
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.core.model.FileObject
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class TnitsControllerTest : ShouldSpec({
    val mapper = jacksonObjectMapper()

    fun setupMockMvc(snapshots: List<FileObject> = emptyList(), updates: List<FileObject> = emptyList()): MockMvc {
        val fileService = mockk<FileService>()
        every { fileService.getFileObjects(any(), any()) } answers {
            when (secondArg<String>()) {
                "/snapshot.xml.gz" -> snapshots
                "/update.xml.gz" -> updates
                else -> emptyList()
            }
        }

        val controller = TnitsController(
            appConfiguration = AppConfiguration(
                baseUrl = "https://example.test",
                vegkartBaseUrl = "https://example.test/vegkart",
                nvdbBaseUrl = "https://example.test/nvdb",
            ),
            fileService = fileService,
        )
        val conversionService = ApplicationConversionService().apply {
            addConverter(InstantConverter)
        }
        val objectMapper = jacksonObjectMapper().apply {
            findAndRegisterModules()
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
        return MockMvcBuilders.standaloneSetup(controller)
            .setConversionService(conversionService)
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }

    should("return snapshots in ascending timestamp order") {
        val mockMvc = setupMockMvc(
            snapshots = listOf(
                FileObject("0105-SpeedLimit/2025-01-03T00-00-00Z/snapshot.xml.gz", Instant.parse("2025-01-03T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-01T00-00-00Z/snapshot.xml.gz", Instant.parse("2025-01-01T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-02T00-00-00Z/snapshot.xml.gz", Instant.parse("2025-01-02T00:00:00Z"), 100),
            ),
        )

        mockMvc.get("/api/v1/tnits/SpeedLimit/snapshots")
            .andExpect {
                status { isOk() }
                jsonPath("$.snapshots[0].timestamp") { value("2025-01-01T00:00:00Z") }
                jsonPath("$.snapshots[1].timestamp") { value("2025-01-02T00:00:00Z") }
                jsonPath("$.snapshots[2].timestamp") { value("2025-01-03T00:00:00Z") }
            }
    }

    should("return all updates in ascending order when from parameter is omitted") {
        val mockMvc = setupMockMvc(
            updates = listOf(
                FileObject("0105-SpeedLimit/2025-01-03T00-00-00Z/update.xml.gz", Instant.parse("2025-01-03T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-01T00-00-00Z/update.xml.gz", Instant.parse("2025-01-01T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-02T00-00-00Z/update.xml.gz", Instant.parse("2025-01-02T00:00:00Z"), 100),
            ),
        )

        mockMvc.get("/api/v1/tnits/SpeedLimit/updates")
            .andExpect {
                status { isOk() }
                jsonPath("$.updates.length()") { value(3) }
                jsonPath("$.updates[0].timestamp") { value("2025-01-01T00:00:00Z") }
                jsonPath("$.updates[1].timestamp") { value("2025-01-02T00:00:00Z") }
                jsonPath("$.updates[2].timestamp") { value("2025-01-03T00:00:00Z") }
                jsonPath("$.newUpdates") {
                    value("https://example.test/api/v1/tnits/SpeedLimit/updates?from=2025-01-03T00:00:00Z")
                }
            }
    }

    should("filter updates by exclusive from timestamp and keep ascending order") {
        val mockMvc = setupMockMvc(
            updates = listOf(
                FileObject("0105-SpeedLimit/2025-01-03T00-00-00Z/update.xml.gz", Instant.parse("2025-01-03T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-01T00-00-00Z/update.xml.gz", Instant.parse("2025-01-01T00:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-02T00-00-00Z/update.xml.gz", Instant.parse("2025-01-02T00:00:00Z"), 100),
            ),
        )

        mockMvc.get("/api/v1/tnits/SpeedLimit/updates?from=2025-01-01T00:00:00Z")
            .andExpect {
                status { isOk() }
                jsonPath("$.updates.length()") { value(2) }
                jsonPath("$.updates[0].timestamp") { value("2025-01-02T00:00:00Z") }
                jsonPath("$.updates[1].timestamp") { value("2025-01-03T00:00:00Z") }
                jsonPath("$.newUpdates") {
                    value("https://example.test/api/v1/tnits/SpeedLimit/updates?from=2025-01-03T00:00:00Z")
                }
            }
    }

    should("set newUpdates from request time when from is omitted and no updates exist") {
        val mockMvc = setupMockMvc(updates = emptyList())
        val beforeRequest = Instant.now().minusSeconds(2)

        val result = mockMvc.get("/api/v1/tnits/SpeedLimit/updates")
            .andExpect {
                status { isOk() }
                jsonPath("$.updates.length()") { value(0) }
            }
            .andReturn()

        val afterRequest = Instant.now().plusSeconds(2)
        val body = mapper.readTree(result.response.contentAsString)
        val newUpdatesUrl = body["newUpdates"].asText()
        val fromParam = URI(newUpdatesUrl).query.substringAfter("from=", "")
        val decodedFrom = URLDecoder.decode(fromParam, StandardCharsets.UTF_8)
        val fromInstant = Instant.parse(decodedFrom)

        assert(fromInstant >= beforeRequest && fromInstant <= afterRequest)
    }

    should("handle exclusive from correctly around Oslo midnight") {
        val mockMvc = setupMockMvc(
            updates = listOf(
                FileObject("0105-SpeedLimit/2025-01-31T22-59-59Z/update.xml.gz", Instant.parse("2025-01-31T22:59:59Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-31T23-00-00Z/update.xml.gz", Instant.parse("2025-01-31T23:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-31T23-00-01Z/update.xml.gz", Instant.parse("2025-01-31T23:00:01Z"), 100),
            ),
        )

        mockMvc.get("/api/v1/tnits/SpeedLimit/updates") {
            param("from", "2025-02-01T00:00:00+01:00")
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.updates.length()") { value(1) }
                jsonPath("$.updates[0].timestamp") { value("2025-01-31T23:00:01Z") }
                jsonPath("$.newUpdates") {
                    value("https://example.test/api/v1/tnits/SpeedLimit/updates?from=2025-01-31T23:00:01Z")
                }
            }
    }

    should("interpret date-only from as Oslo midnight and filter exclusively") {
        val mockMvc = setupMockMvc(
            updates = listOf(
                FileObject("0105-SpeedLimit/2025-01-31T22-59-59Z/update.xml.gz", Instant.parse("2025-01-31T22:59:59Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-31T23-00-00Z/update.xml.gz", Instant.parse("2025-01-31T23:00:00Z"), 100),
                FileObject("0105-SpeedLimit/2025-01-31T23-00-01Z/update.xml.gz", Instant.parse("2025-01-31T23:00:01Z"), 100),
            ),
        )

        mockMvc.get("/api/v1/tnits/SpeedLimit/updates?from=2025-02-01")
            .andExpect {
                status { isOk() }
                jsonPath("$.updates.length()") { value(1) }
                jsonPath("$.updates[0].timestamp") { value("2025-01-31T23:00:01Z") }
                jsonPath("$.newUpdates") {
                    value("https://example.test/api/v1/tnits/SpeedLimit/updates?from=2025-01-31T23:00:01Z")
                }
            }
    }
})
