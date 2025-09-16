package no.vegvesen.nvdb.tnits.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource

data class AppConfig(
    val database: DatabaseConfig,
    val uberiketApi: UberiketApiConfig,
    val datakatalogApi: DatakatalogApiConfig,
    val gzip: Boolean,
    val s3: S3Config? = null,
)

data class DatabaseConfig(val url: String = "jdbc:h2:file:./data/nvdb;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1", val user: String = "sa", val password: String = "")

data class UberiketApiConfig(val baseUrl: String)

data class DatakatalogApiConfig(val baseUrl: String)

data class S3Config(val endpoint: String, val bucket: String, val accessKey: String, val secretKey: String, val region: String = "us-east-1")

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
