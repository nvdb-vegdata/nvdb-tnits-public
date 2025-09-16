package no.vegvesen.nvdb.tnits.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource

enum class ExportTarget {
    File,
    S3,
}

data class AppConfig(val uberiketApi: UberiketApiConfig, val datakatalogApi: DatakatalogApiConfig, val s3: S3Config, val exporter: ExporterConfig)

data class ExporterConfig(val gzip: Boolean, val target: ExportTarget, val bucket: String)

data class UberiketApiConfig(val baseUrl: String)

data class DatakatalogApiConfig(val baseUrl: String)

data class S3Config(val endpoint: String, val accessKey: String, val secretKey: String)

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
