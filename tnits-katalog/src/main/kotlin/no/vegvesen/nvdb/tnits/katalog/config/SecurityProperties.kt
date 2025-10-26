package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security")
data class SecurityProperties(
    val enabled: Boolean = true,
    val adminRole: String = "nvdbapi=admin",
    val roleClaimPath: String = "svvroles",
)
