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
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.gateways.DatakatalogApi
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
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

class Services : WithLogger {

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

    val uberiketApi = UberiketApi(uberiketHttpClient)

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

    val rocksDbContext = RocksDbContext()

    val veglenkerRepository: VeglenkerRepository =
        VeglenkerRocksDbStore(rocksDbContext)

    val keyValueStore: KeyValueRocksDbStore = KeyValueRocksDbStore(rocksDbContext)

    val vegobjekterRepository: VegobjekterRepository = VegobjekterRocksDbStore(rocksDbContext)

    val cachedVegnett = CachedVegnett(veglenkerRepository, vegobjekterRepository)

    val openLrService: OpenLrService = OpenLrService(cachedVegnett)

    val vegobjekterService = VegobjekterService(keyValueStore, uberiketApi, vegobjekterRepository, rocksDbContext)

    val veglenkesekvenserService = VeglenkesekvenserService(keyValueStore, uberiketApi, veglenkerRepository, rocksDbContext)

    val speedLimitGenerator =
        SpeedLimitGenerator(
            veglenkerBatchLookup = { ids ->
                ids.associateWith {
                    cachedVegnett.getVeglenker(it)
                }
            },
            datakatalogApi = datakatalogApi,
            openLrService = openLrService,
            vegobjekterRepository = vegobjekterRepository,
        )

    val speedLimitExporter = SpeedLimitExporter(speedLimitGenerator, config)

    companion object {
        val marshaller: BinaryMarshaller = BinaryMarshallerFactory().create()
        val objectMapper = jacksonObjectMapper().initialize()
    }
}
