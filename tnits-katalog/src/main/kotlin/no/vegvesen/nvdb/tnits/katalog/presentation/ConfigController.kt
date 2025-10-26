package no.vegvesen.nvdb.tnits.katalog.presentation

import io.swagger.v3.oas.annotations.Hidden
import no.vegvesen.nvdb.tnits.katalog.config.AppConfiguration
import no.vegvesen.nvdb.tnits.katalog.presentation.model.ConfigResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/config")
@Hidden
class ConfigController(
    private val appConfiguration: AppConfiguration,
) {

    @GetMapping
    fun getConfig(): ConfigResponse = ConfigResponse(
        vegkartBaseUrl = appConfiguration.vegkartBaseUrl,
        nvdbBaseUrl = appConfiguration.nvdbBaseUrl,
    )
}
