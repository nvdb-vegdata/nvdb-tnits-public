package no.vegvesen.nvdb.tnits.generator

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.minio.MinioClient
import jakarta.inject.Named
import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.common.api.SharedKeyValueStore
import no.vegvesen.nvdb.tnits.common.infrastructure.MinioGateway
import no.vegvesen.nvdb.tnits.common.infrastructure.S3KeyValueStore
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.generator.config.*
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module
import kotlin.time.Clock

@Module
@Configuration
@ComponentScan
class MainModule {

    @Singleton
    fun appConfig() = loadConfig()

    @Singleton
    fun uberiketApiConfig(appConfig: AppConfig) = appConfig.uberiketApi

    @Singleton
    fun datakatalogApiConfig(appConfig: AppConfig) = appConfig.datakatalogApi

    @Singleton
    fun s3Config(appConfig: AppConfig) = appConfig.s3

    @Singleton
    fun exporterConfig(appConfig: AppConfig) = appConfig.exporter

    @Singleton
    fun backupConfig(appConfig: AppConfig) = appConfig.backup

    @Singleton
    fun rocksDbConfig(appConfig: AppConfig) = appConfig.rocksDb

    @Singleton
    fun retentionConfig(appConfig: AppConfig) = appConfig.retention

    @Singleton
    fun clock(): Clock = Clock.System

    @Singleton
    fun minioGateway(minioClient: MinioClient, s3Config: S3Config) = MinioGateway(minioClient, s3Config)

    @Singleton
    fun adminFlags(minioGateway: MinioGateway): SharedKeyValueStore = S3KeyValueStore(minioGateway)

    @Singleton
    @Named("uberiketHttpClient")
    fun uberiketHttpClient(config: UberiketApiConfig, appConfig: AppConfig) = createUberiketHttpClient(config.baseUrl, appConfig)

    @Singleton
    @Named("datakatalogHttpClient")
    fun datakatalogHttpClient(config: DatakatalogApiConfig, appConfig: AppConfig) = createDatakatalogHttpClient(config.baseUrl, appConfig)

    @Singleton
    fun minioClient(config: AppConfig): MinioClient = config.s3.let { s3Config ->
        MinioClient.builder()
            .endpoint(s3Config.endpoint)
            .credentials(s3Config.accessKey, s3Config.secretKey)
            .build()
            .also { log.info("MinIO client initialized for endpoint: ${s3Config.endpoint}") }
    }

    companion object {
        private fun createUberiketHttpClient(baseUrl: String, appConfig: AppConfig): HttpClient = HttpClient(CIO) {
            commonConfig(appConfig.http)
            defaultRequest {
                url(baseUrl)
                headers.append("Accept", "application/json, application/x-ndjson")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }

        private fun createDatakatalogHttpClient(baseUrl: String, appConfig: AppConfig): HttpClient = HttpClient(CIO) {
            commonConfig(appConfig.http)
            defaultRequest {
                url(baseUrl)
                headers.append("Accept", "application/json")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }

        private fun HttpClientConfig<*>.configureLogging(httpConfig: HttpConfig) {
            val logLevel = httpConfig.logLevel
            if (logLevel != LogLevel.NONE) {
                install(Logging) {
                    level = logLevel
                }
            }
        }

        internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.commonConfig(httpConfig: HttpConfig) {
            expectSuccess = true
            configureJackson()
            configureLogging(httpConfig)
            configureRetry()
            configureTimeouts()
        }

        private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureTimeouts() {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
        }

        private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureRetry() {
            install(HttpRequestRetry) {
                maxRetries = 5
                retryIf { _, response ->
                    response.status.value in 500..599 || // 5xx server errors
                        response.status == HttpStatusCode.TooManyRequests // 429 rate limiting
                }
                retryOnException(retryOnTimeout = true)
                exponentialDelay(base = 2.0, maxDelayMs = 30_000)

                modifyRequest { request ->
                    log.warn("Retrying API request to ${request.url}")
                }
            }
        }

        private fun <T : HttpClientEngineConfig> HttpClientConfig<T>.configureJackson() {
            install(ContentNegotiation) {
                jackson {
                    initialize()
                }
            }
        }
    }
}
