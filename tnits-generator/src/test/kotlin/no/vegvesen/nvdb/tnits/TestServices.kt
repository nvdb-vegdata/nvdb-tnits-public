package no.vegvesen.nvdb.tnits

import io.minio.MinioClient
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import no.vegvesen.nvdb.tnits.config.BackupConfig
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.handlers.ExportUpdateHandler
import no.vegvesen.nvdb.tnits.handlers.PerformBackfillHandler
import no.vegvesen.nvdb.tnits.handlers.PerformUpdateHandler
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

    val uberiketApi = mockk<UberiketApi>()

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

    val exportedFeatureStore = ExportedFeatureStore(dbContext)

    val tnitsFeatureExporter = TnitsFeatureExporter(
        tnitsFeatureGenerator = TnitsFeatureGenerator(
            cachedVegnett = cachedVegnett,
            egenskapService = egenskapService,
            openLrService = OpenLrService(cachedVegnett),
            vegobjekterRepository = vegobjekterRepository,
            exportedFeatureStore = exportedFeatureStore,
        ),
        exporterConfig = ExporterConfig(
            gzip = false,
            target = ExportTarget.S3,
            bucket = testBucket,
        ),
        minioClient = minioClient,
        hashStore = hashStore,
        rocksDbContext = dbContext,
        exportedFeatureStore = exportedFeatureStore,
    )

    val performBackfillHandler = PerformBackfillHandler(veglenkesekvenserService, vegobjekterService)
    val performUpdateHandler = PerformUpdateHandler(veglenkesekvenserService, vegobjekterService)

    val dirtyCheckingRepository = DirtyCheckingRocksDbStore(dbContext)

    val rocksDbBackupService = RocksDbBackupService(
        dbContext,
        minioClient,
        BackupConfig(
            enabled = true,
            bucket = testBucket,
        ),
    )

    val exportUpdateHandler = ExportUpdateHandler(
        tnitsFeatureExporter = tnitsFeatureExporter,
        dirtyCheckingRepository = dirtyCheckingRepository,
        vegobjekterRepository = vegobjekterRepository,
        keyValueStore = keyValueStore,
    )

    suspend fun setupBackfill(paths: List<String> = readJsonTestResources()) {
        val (veglenkesekvenser, vegobjekter) = readTestData(*paths.toTypedArray())
        coEvery { uberiketApi.streamVeglenkesekvenser() } returns veglenkesekvenser.asFlow()
        coEvery { uberiketApi.streamVeglenkesekvenser(isNull(true)) } returns emptyFlow()
        for (typeId in mainVegobjektTyper + supportingVegobjektTyper) {
            coEvery { uberiketApi.streamVegobjekter(typeId) } returns
                vegobjekter.filter { it.typeId == typeId }.asFlow()
            coEvery { uberiketApi.streamVegobjekter(any(), start = isNull(true)) } returns emptyFlow()
            coEvery { uberiketApi.getVegobjekterPaginated(any(), any(), any()) } answers {
                val typeId = firstArg<Int>()
                val ids = secondArg<Set<Long>>()
                vegobjekter.filter { it.typeId == typeId && it.id in ids }.asFlow()
            }
        }
        performBackfillHandler.performBackfill()
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
