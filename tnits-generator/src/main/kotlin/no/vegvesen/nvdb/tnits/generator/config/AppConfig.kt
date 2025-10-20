package no.vegvesen.nvdb.tnits.generator.config

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addResourceOrFileSource

data class AppConfig(
    val uberiketApi: UberiketApiConfig,
    val datakatalogApi: DatakatalogApiConfig,
    val s3: S3Config,
    val exporter: ExporterConfig,
    val backup: BackupConfig,
    val rocksDb: RocksDbConfig,
)

data class RocksDbConfig(val path: String)

data class ExporterConfig(val gzip: Boolean, val bucket: String)

data class BackupConfig(val enabled: Boolean, val bucket: String, val path: String = "rocksdb-backup")

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
