package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_DB
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_FEATURE_TYPES
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_ROADNET
import no.vegvesen.nvdb.tnits.common.api.SharedKeyValueStore
import no.vegvesen.nvdb.tnits.common.api.putValue
import no.vegvesen.nvdb.tnits.katalog.config.AppConfiguration
import no.vegvesen.nvdb.tnits.katalog.core.api.FileService
import no.vegvesen.nvdb.tnits.katalog.presentation.model.FileItem
import no.vegvesen.nvdb.tnits.katalog.presentation.model.FileItemType
import no.vegvesen.nvdb.tnits.katalog.presentation.model.ListResponse
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative endpoints (requires authentication)")
@SecurityRequirement(name = "oauth2")
class AdminController(
    private val adminFlags: SharedKeyValueStore,
    private val fileService: FileService,
    private val appConfiguration: AppConfiguration,
) {

    @PostMapping("flags")
    fun setFlags(@RequestParam resetDb: Boolean? = null, @RequestParam resetRoadnet: Boolean? = null, @RequestParam resetFeatureTypeIds: Set<Int>? = null) {
        if (resetDb == true) {
            adminFlags.putValue(RESET_DB, resetDb)
            return
        } else if (resetDb == false) {
            adminFlags.delete(RESET_DB)
        }

        if (resetRoadnet != null) {
            if (resetRoadnet) {
                adminFlags.putValue(RESET_ROADNET, resetRoadnet)
            } else {
                adminFlags.delete(RESET_ROADNET)
            }
        }

        if (resetFeatureTypeIds != null) {
            if (resetFeatureTypeIds.any()) {
                adminFlags.putValue(RESET_FEATURE_TYPES, resetFeatureTypeIds)
            } else {
                adminFlags.delete(RESET_FEATURE_TYPES)
            }
        }
    }

    @GetMapping("files")
    fun listPath(@RequestParam path: String = "", @RequestParam recursive: Boolean = false): ListResponse {
        val objects = fileService.list(path, recursive)

        val items = objects.map { objectName ->
            val isDirectory = objectName.endsWith("/")
            val type = if (isDirectory) FileItemType.DIRECTORY else FileItemType.FILE

            val href = if (isDirectory) {
                val dirPath = objectName.trimEnd('/')
                "${appConfiguration.baseUrl}/api/v1/admin/files?path=$dirPath"
            } else {
                "${appConfiguration.baseUrl}/api/v1/download?path=$objectName"
            }

            FileItem(
                path = objectName,
                type = type,
                href = href,
                size = null,
            )
        }

        return ListResponse(
            path = path,
            recursive = recursive,
            items = items,
        )
    }

    @DeleteMapping("files")
    fun deletePath(@RequestParam path: String, @RequestParam recursive: Boolean = false): List<String> = fileService.delete(path, recursive)
}
