package no.vegvesen.nvdb.tnits.katalog.presentation

import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.core.model.FileDownload
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.ByteArrayInputStream

class DownloadControllerTest : ShouldSpec({

    fun setupController(path: String, fileName: String, contentType: String, fileSize: Long): MockMvc {
        val fileDownload = FileDownload(
            inputStream = ByteArrayInputStream(ByteArray(fileSize.toInt())),
            fileName = fileName,
            contentType = contentType,
            size = fileSize,
        )

        val fileService = mockk<FileService>()
        every { fileService.downloadFile(path) } returns fileDownload

        val controller = DownloadController(fileService)
        return MockMvcBuilders.standaloneSetup(controller).build()
    }

    should("return file with content length header") {
        val fileName = "test_file.xml.gz"
        val contentType = "application/gzip"
        val fileSize = 17L
        val path = "test/path.xml.gz"

        val mockMvc = setupController(path, fileName, contentType, fileSize)

        mockMvc.get("/api/v1/download?path=$path")
            .andExpect {
                status { isOk() }
                header { exists("Content-Length") }
                header { string("Content-Length", fileSize.toString()) }
                header { exists("Content-Disposition") }
                content { contentType(contentType) }
            }
    }

    should("return gzip file with correct content type") {
        val fileName = "snapshot.xml.gz"
        val contentType = "application/gzip"
        val path = "data/snapshot.xml.gz"

        val mockMvc = setupController(path, fileName, contentType, 1024)

        mockMvc.get("/api/v1/download?path=$path")
            .andExpect {
                status { isOk() }
                content { contentType(contentType) }
            }
    }

    should("return xml file with correct content type") {
        val fileName = "data.xml"
        val contentType = "application/xml"
        val path = "data/file.xml"

        val mockMvc = setupController(path, fileName, contentType, 6)

        mockMvc.get("/api/v1/download?path=$path")
            .andExpect {
                status { isOk() }
                content { contentType(contentType) }
            }
    }

    should("include filename in content disposition header") {
        val fileName = "export_2025-01-15.xml.gz"
        val path = "path/to/file"

        val mockMvc = setupController(path, fileName, "application/gzip", 512)

        mockMvc.get("/api/v1/download?path=$path")
            .andExpect {
                status { isOk() }
                header { string("Content-Disposition", "attachment; filename=\"$fileName\"") }
            }
    }
})
