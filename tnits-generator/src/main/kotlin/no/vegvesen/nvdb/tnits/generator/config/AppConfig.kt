package no.vegvesen.nvdb.tnits.generator.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource
import io.ktor.client.plugins.logging.LogLevel
import no.vegvesen.nvdb.tnits.common.model.S3Config

data class AppConfig(
    val uberiketApi: UberiketApiConfig,
    val datakatalogApi: DatakatalogApiConfig,
    val s3: S3Config,
    val exporter: ExporterConfig,
    val backup: BackupConfig,
    val rocksDb: RocksDbConfig,
    val httpClient: HttpClientConfig = HttpClientConfig(),
)

data class RocksDbConfig(val path: String)

data class ExporterConfig(val gzip: Boolean)

data class BackupConfig(val enabled: Boolean, val path: String = "rocksdb-backup")

data class UberiketApiConfig(val baseUrl: String)

data class DatakatalogApiConfig(val baseUrl: String)

data class HttpClientConfig(val logLevel: LogLevel = LogLevel.NONE)

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
