package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/download")
@Tag(name = "Downloads")
class DownloadController(private val fileService: FileService) {

    @Operation(description = "Download a TN-ITS data file (snapshot or update).")
    @GetMapping
    fun download(
        @RequestParam @Parameter(description = "File path obtained from snapshot or update endpoints") path: String,
    ): ResponseEntity<InputStreamResource> {
        val fileDownload = fileService.downloadFile(path)

        val resource = InputStreamResource(fileDownload.inputStream)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${fileDownload.fileName}\"")
            .header(HttpHeaders.CONTENT_LENGTH, fileDownload.size.toString())
            .contentType(MediaType.parseMediaType(fileDownload.contentType))
            .body(resource)
    }

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 256 * 1024 // 256KB buffer for S3 streaming
    }
}
