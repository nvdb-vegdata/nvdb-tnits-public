package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_DB
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_FEATURE_TYPES
import no.vegvesen.nvdb.tnits.common.api.AdminFlags.RESET_ROADNET
import no.vegvesen.nvdb.tnits.common.api.SharedKeyValueStore
import no.vegvesen.nvdb.tnits.common.api.putValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administrative endpoints (requires authentication)")
@SecurityRequirement(name = "oauth2")
class AdminController(private val adminFlags: SharedKeyValueStore) {

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
}
