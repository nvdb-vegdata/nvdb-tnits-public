package no.vegvesen.nvdb.tnits.katalog.presentation

import io.minio.GetObjectArgs
import io.minio.MinioClient
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import no.vegvesen.nvdb.tnits.katalog.config.MinioProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestController
@RequestMapping("/api/v1/download")
@Tag(name = "Downloads")
class DownloadController(private val minioClient: MinioClient, private val minioProperties: MinioProperties) {

    @GetMapping
    fun download(@RequestParam path: String, response: HttpServletResponse): ResponseEntity<StreamingResponseBody> {
        val fileName = path.substringAfterLast('/')
        response.setHeader("Content-Disposition", "attachment; filename=\"$fileName\"")

        val contentType = when {
            path.endsWith(".xml.gz") -> "application/gzip"
            path.endsWith(".xml") -> "application/xml"
            else -> "application/octet-stream"
        }

        val streamingBody = StreamingResponseBody { outputStream ->
            try {
                minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(minioProperties.bucket)
                        .`object`(path)
                        .build(),
                ).use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = DOWNLOAD_BUFFER_SIZE)
                }
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "File not found: $path", e)
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .body(streamingBody)
    }

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024 // 256KB buffer for S3 streaming
    }
}
