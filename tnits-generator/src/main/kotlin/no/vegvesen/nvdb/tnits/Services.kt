package no.vegvesen.nvdb.tnits

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.gateways.DatakatalogApi
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.gateways.UberiketApiGateway
import no.vegvesen.nvdb.tnits.handlers.ExportUpdateHandler
import no.vegvesen.nvdb.tnits.handlers.PerformBackfillHandler
import no.vegvesen.nvdb.tnits.handlers.PerformUpdateHandler
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.services.EgenskapService
import no.vegvesen.nvdb.tnits.services.S3TimestampService
import no.vegvesen.nvdb.tnits.storage.*
import no.vegvesen.nvdb.tnits.utilities.WithLogger
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjekterService
import org.openlr.binary.BinaryMarshaller
import org.openlr.binary.BinaryMarshallerFactory

fun ObjectMapper.initialize(): ObjectMapper = apply {
    findAndRegisterModules()
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
}

/**
 * Core composition root for the application.
 * Initializes and holds references to all services, repositories, and clients.
 */
class Services :
    WithLogger,
    AutoCloseable {

    val config = loadConfig()

    val uberiketHttpClient =
        HttpClient(CIO) {
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
                url(config.uberiketApi.baseUrl)
                headers.append("Accept", "application/json, application/x-ndjson")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }

    val uberiketApi: UberiketApi = UberiketApiGateway(uberiketHttpClient)

    val datakatalogHttpClient =
        HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                jackson {
                    initialize()
                }
            }
            defaultRequest {
                url(config.datakatalogApi.baseUrl)
                headers.append("Accept", "application/json")
                headers.append("X-Client", "nvdb-tnits-console")
            }
        }

    val datakatalogApi = DatakatalogApi(datakatalogHttpClient)

    val minioClient: MinioClient = config.s3.let { s3Config ->
        MinioClient.builder()
            .endpoint(s3Config.endpoint)
            .credentials(s3Config.accessKey, s3Config.secretKey)
            .build()
            .also { log.info("MinIO client initialized for endpoint: ${s3Config.endpoint}") }
    }

    val rocksDbContext = RocksDbContext()

    val veglenkerRepository: VeglenkerRepository =
        VeglenkerRocksDbStore(rocksDbContext)

    val keyValueStore: KeyValueRocksDbStore = KeyValueRocksDbStore(rocksDbContext)

    val vegobjekterRepository: VegobjekterRepository = VegobjekterRocksDbStore(rocksDbContext)

    val dirtyCheckingRepository: DirtyCheckingRepository = DirtyCheckingRocksDbStore(rocksDbContext)

    val cachedVegnett = CachedVegnett(veglenkerRepository, vegobjekterRepository)

    val openLrService: OpenLrService = OpenLrService(cachedVegnett)

    val vegobjekterHashStore = VegobjekterHashStore(rocksDbContext)

    val vegobjekterService = VegobjekterService(keyValueStore, uberiketApi, vegobjekterRepository, rocksDbContext)

    val veglenkesekvenserService = VeglenkesekvenserService(keyValueStore, uberiketApi, veglenkerRepository, rocksDbContext)

    val egenskapService = EgenskapService(datakatalogApi)

    val exportedFeatureStore = ExportedFeatureStore(rocksDbContext)

    val tnitsFeatureGenerator =
        TnitsFeatureGenerator(
            cachedVegnett,
            egenskapService,
            openLrService,
            vegobjekterRepository,
            exportedFeatureStore,
        )

    val tnitsFeatureExporter =
        TnitsFeatureExporter(tnitsFeatureGenerator, config.exporter, minioClient, vegobjekterHashStore, rocksDbContext, exportedFeatureStore)

    val s3TimestampService = S3TimestampService(minioClient, config.exporter.bucket)

    val rocksDbBackupService = RocksDbBackupService(rocksDbContext, minioClient, config.backup)

    val performBackfillHandler = PerformBackfillHandler(veglenkesekvenserService, vegobjekterService)

    val performUpdateHandler = PerformUpdateHandler(veglenkesekvenserService, vegobjekterService)

    val exportUpdateHandler = ExportUpdateHandler(tnitsFeatureExporter, dirtyCheckingRepository, vegobjekterRepository, keyValueStore)

    override fun close() {
        runCatching {
            uberiketHttpClient.close()
        }
        runCatching {
            datakatalogHttpClient.close()
        }
        runCatching {
            minioClient.close()
        }
        runCatching {
            rocksDbContext.close()
        }
    }

    companion object {
        val marshaller: BinaryMarshaller = BinaryMarshallerFactory().create()
        val objectMapper = jacksonObjectMapper().initialize()

        inline fun withServices(block: Services.() -> Unit) {
            Services().use { services ->
                services.block()
            }
        }
    }
}
