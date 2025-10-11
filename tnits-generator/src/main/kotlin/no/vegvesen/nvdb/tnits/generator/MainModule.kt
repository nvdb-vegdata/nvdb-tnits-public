package no.vegvesen.nvdb.tnits.generator

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.minio.MinioClient
import jakarta.inject.Named
import jakarta.inject.Singleton
import no.vegvesen.nvdb.tnits.generator.config.AppConfig
import no.vegvesen.nvdb.tnits.generator.config.DatakatalogApiConfig
import no.vegvesen.nvdb.tnits.generator.config.UberiketApiConfig
import no.vegvesen.nvdb.tnits.generator.config.loadConfig
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

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
    @Named("uberiketHttpClient")
    fun uberiketHttpClient(config: UberiketApiConfig) = createUberiketHttpClient(config.baseUrl)

    @Singleton
    @Named("datakatalogHttpClient")
    fun datakatalogHttpClient(config: DatakatalogApiConfig) = createDatakatalogHttpClient(config.baseUrl)

    @Singleton
    fun minioClient(config: AppConfig): MinioClient = config.s3.let { s3Config ->
        MinioClient.builder()
            .endpoint(s3Config.endpoint)
            .credentials(s3Config.accessKey, s3Config.secretKey)
            .build()
            .also { log.info("MinIO client initialized for endpoint: ${s3Config.endpoint}") }
    }

    companion object {
        private fun createUberiketHttpClient(baseUrl: String): HttpClient = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                jackson {
                    initialize()
                }
            }
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 5)
                retryOnException(maxRetries = 5, retryOnTimeout = true)
                exponentialDelay(base = 2.0, maxDelayMs = 30_000)

                modifyRequest { request ->
                    log.warn("Retrying API request to ${request.url}")
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
            defaultRequest {
                url(baseUrl)
                headers.append("Accept", "application/json, application/x-ndjson")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }

        private fun createDatakatalogHttpClient(baseUrl: String): HttpClient = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                jackson {
                    initialize()
                }
            }
            defaultRequest {
                url(baseUrl)
                headers.append("Accept", "application/json")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }
    }
}
