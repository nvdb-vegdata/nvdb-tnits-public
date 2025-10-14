package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/api/v1/download")
@Tag(name = "Downloads")
class DownloadController(private val fileService: FileService) {

    @GetMapping
    fun download(@RequestParam path: String, response: HttpServletResponse): ResponseEntity<StreamingResponseBody> {
        val fileDownload = fileService.downloadFile(path)

        response.setHeader("Content-Disposition", "attachment; filename=\"${fileDownload.fileName}\"")

        val streamingBody = StreamingResponseBody { outputStream ->
            fileDownload.inputStream.use { inputStream ->
                inputStream.copyTo(outputStream, bufferSize = DOWNLOAD_BUFFER_SIZE)
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(fileDownload.contentType))
            .body(streamingBody)
    }

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024 // 256KB buffer for S3 streaming
    }
}
