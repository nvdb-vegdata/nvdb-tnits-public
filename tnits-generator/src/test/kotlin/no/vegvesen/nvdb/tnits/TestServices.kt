package no.vegvesen.nvdb.tnits

import io.minio.MinioClient
import io.mockk.coEvery
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig
import no.vegvesen.nvdb.tnits.services.EgenskapService
import no.vegvesen.nvdb.tnits.storage.*
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjekterService

class TestServices(minioClient: MinioClient) : AutoCloseable {
    val testBucket = "nvdb-tnits-e2e-test"

    val dbContext = TempRocksDbConfig()

    val uberiketApi: UberiketApi = mockk()

    val keyValueStore = KeyValueRocksDbStore(dbContext)
    val vegobjekterRepository = VegobjekterRocksDbStore(dbContext)
    val vegobjekterService: VegobjekterService = VegobjekterService(
        keyValueStore = keyValueStore,
        uberiketApi = uberiketApi,
        vegobjekterRepository = vegobjekterRepository,
        rocksDbContext = dbContext,
    )
    val veglenkerRepository: VeglenkerRepository = VeglenkerRocksDbStore(dbContext)
    val veglenkesekvenserService: VeglenkesekvenserService = VeglenkesekvenserService(
        keyValueStore = keyValueStore,
        uberiketApi = uberiketApi,
        veglenkerRepository = veglenkerRepository,
        rocksDbContext = dbContext,
    )
    val cachedVegnett = CachedVegnett(veglenkerRepository, vegobjekterRepository)

    val hashStore = VegobjekterHashStore(dbContext)
    val egenskapService =
        mockk<EgenskapService> { coEvery { getKmhByEgenskapVerdi() } returns EgenskapService.hardcodedFartsgrenseTillatteVerdier }
    val tnitsFeatureExporter = TnitsFeatureExporter(
        tnitsFeatureGenerator = TnitsFeatureGenerator(
            cachedVegnett = cachedVegnett,
            egenskapService = egenskapService,
            openLrService = OpenLrService(cachedVegnett),
            vegobjekterRepository = vegobjekterRepository,
        ),
        exporterConfig = ExporterConfig(
            gzip = false,
            target = ExportTarget.S3,
            bucket = testBucket,
        ),
        minioClient = minioClient,
        hashStore = hashStore,
        rocksDbContext = dbContext,
    )

    suspend fun setupBackfill(paths: List<String> = readJsonTestResources()) {
        setupCachedVegnett(dbContext, *paths.toTypedArray())
        cachedVegnett.initialize()
    }

    override fun close() {
        dbContext.close()
    }

    companion object {
        inline fun withTestServices(minioClient: MinioClient, block: TestServices.() -> Unit) {
            TestServices(minioClient).use { services ->
                block(services)
            }
        }
    }
}
