package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppConfiguration(
    val baseUrl: String,
)
