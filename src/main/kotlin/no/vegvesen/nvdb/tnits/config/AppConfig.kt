package no.vegvesen.nvdb.tnits.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource

data class AppConfig(val database: DatabaseConfig, val uberiketApi: UberiketApiConfig, val datakatalogApi: DatakatalogApiConfig)

data class DatabaseConfig(val url: String = "jdbc:h2:file:./data/nvdb;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1", val user: String = "sa", val password: String = "")

data class UberiketApiConfig(val baseUrl: String)

data class DatakatalogApiConfig(val baseUrl: String)

/**
 * Loads configuration from application.conf and environment variables.
 * Environment variables take precedence over config file values.
 */
@OptIn(ExperimentalHoplite::class)
fun loadConfig(): AppConfig = ConfigLoaderBuilder
    .default()
    .withExplicitSealedTypes()
    .addResourceOrFileSource("/application.conf", optional = true)
    .addResourceOrFileSource("application.conf", optional = true)
    .build()
    .loadConfigOrThrow<AppConfig>()
