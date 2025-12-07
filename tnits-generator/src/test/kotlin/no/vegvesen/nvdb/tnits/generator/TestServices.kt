package no.vegvesen.nvdb.tnits.generator

import io.minio.MinioClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import no.vegvesen.nvdb.tnits.common.infrastructure.MinioGateway
import no.vegvesen.nvdb.tnits.common.infrastructure.S3KeyValueStore
import no.vegvesen.nvdb.tnits.common.model.S3Config
import no.vegvesen.nvdb.tnits.common.model.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.common.model.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.config.BackupConfig
import no.vegvesen.nvdb.tnits.generator.config.ExporterConfig
import no.vegvesen.nvdb.tnits.generator.core.api.DatakatalogApi
import no.vegvesen.nvdb.tnits.generator.core.api.UberiketApi
import no.vegvesen.nvdb.tnits.generator.core.api.VeglenkerRepository
import no.vegvesen.nvdb.tnits.generator.core.model.EgenskapsTyper.hardcodedFartsgrenseTillatteVerdier
import no.vegvesen.nvdb.tnits.generator.core.model.EgenskapsTyper.hardcodedFaseTillatteVerdier
import no.vegvesen.nvdb.tnits.generator.core.model.EgenskapsTyper.hardcodedVegkategoriTillatteVerdier
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.FeatureExportWriter
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.FeatureTransformer
import no.vegvesen.nvdb.tnits.generator.core.services.tnits.TnitsExportService
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.OpenLrService
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsAutomaticCycle
import no.vegvesen.nvdb.tnits.generator.infrastructure.ingest.NvdbBackfillOrchestrator
import no.vegvesen.nvdb.tnits.generator.infrastructure.ingest.NvdbUpdateOrchestrator
import no.vegvesen.nvdb.tnits.generator.infrastructure.ingest.VegnettLoader
import no.vegvesen.nvdb.tnits.generator.infrastructure.ingest.VegobjektLoader
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.*
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.S3TimestampService
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.TnitsFeatureS3Exporter
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig
import kotlin.time.Clock

class TestServices(minioClient: MinioClient) : AutoCloseable {
    val testBucket = "nvdb-tnits-e2e-test"

    val dbContext = TempRocksDbConfig()

    val uberiketApi = mockk<UberiketApi>()

    val clock = mockk<Clock> {
        every { now() } answers { Clock.System.now() }
    }

    val keyValueStore = KeyValueRocksDbStore(dbContext)
    val vegobjekterRepository = VegobjekterRocksDbStore(dbContext, clock)
    val vegobjektLoader: VegobjektLoader = VegobjektLoader(
        keyValueStore = keyValueStore,
        uberiketApi = uberiketApi,
        vegobjekterRepository = vegobjekterRepository,
        rocksDbContext = dbContext,
        clock = clock,
    )
    val veglenkerRepository: VeglenkerRepository = VeglenkerRocksDbStore(dbContext, clock)
    val vegnettLoader: VegnettLoader = VegnettLoader(
        keyValueStore = keyValueStore,
        uberiketApi = uberiketApi,
        veglenkerRepository = veglenkerRepository,
        rocksDbContext = dbContext,
        clock = clock,
    )
    val cachedVegnett = CachedVegnett(veglenkerRepository, vegobjekterRepository, clock)

    val datakatalogApi = mockk<DatakatalogApi> {
        coEvery { getKmhByEgenskapVerdi() } returns hardcodedFartsgrenseTillatteVerdier
        coEvery { getVegkategoriByEgenskapVerdi() } returns hardcodedVegkategoriTillatteVerdier
        coEvery { getVegfaseByEgenskapVerdi() } returns hardcodedFaseTillatteVerdier
    }

    val exportedFeatureStore = ExportedFeatureRocksDbStore(dbContext)

    val exporterConfig = ExporterConfig(gzip = false)

    val s3Config = S3Config(
        endpoint = "N/A",
        accessKey = "N/A",
        secretKey = "N/A",
        bucket = testBucket,
    )

    val tnitsFeatureExporter = TnitsFeatureS3Exporter(
        exporterConfig = exporterConfig,
        minioClient = minioClient,
        s3Config = s3Config,
    )

    val exportWriter = FeatureExportWriter(
        featureExporter = tnitsFeatureExporter,
        exportedFeatureRepository = exportedFeatureStore,
    )

    val dirtyCheckingRepository = DirtyCheckingRocksDbStore(dbContext)

    val featureExportCoordinator = TnitsExportService(
        featureTransformer = FeatureTransformer(
            cachedVegnett = cachedVegnett,
            datakatalogApi = datakatalogApi,
            openLrService = OpenLrService(cachedVegnett),
            vegobjekterRepository = vegobjekterRepository,
            exportedFeatureStore = exportedFeatureStore,
            clock = clock,
        ),
        exportWriter,
        dirtyCheckingRepository = dirtyCheckingRepository,
        vegobjekterRepository = vegobjekterRepository,
        keyValueStore = keyValueStore,
    )

    val backfillOrchestrator = NvdbBackfillOrchestrator(vegnettLoader, vegobjektLoader)
    val updateOrchestrator = NvdbUpdateOrchestrator(vegnettLoader, vegobjektLoader)

    val adminFlags = S3KeyValueStore(MinioGateway(minioClient, s3Config))

    val rocksDbBackupService = RocksDbS3BackupService(
        dbContext,
        minioClient,
        BackupConfig(
            enabled = true,
        ),
        s3Config = s3Config,
        adminFlags = adminFlags,
        keyValueStore = keyValueStore,
        vegobjekterRepository = vegobjekterRepository,
    )

    val featureTransformer = FeatureTransformer(
        cachedVegnett = cachedVegnett,
        datakatalogApi = datakatalogApi,
        openLrService = OpenLrService(cachedVegnett),
        vegobjekterRepository = vegobjekterRepository,
        exportedFeatureStore = exportedFeatureStore,
        clock = clock,
    )

    val tnitsExportService = TnitsExportService(
        featureTransformer = featureTransformer,
        exportWriter = exportWriter,
        dirtyCheckingRepository = dirtyCheckingRepository,
        vegobjekterRepository = vegobjekterRepository,
        keyValueStore = keyValueStore,
    )

    val timestampService = S3TimestampService(
        minioClient = minioClient,
        exporterConfig = exporterConfig,
        s3Config = s3Config,
    )

    val automaticCycle = TnitsAutomaticCycle(
        rocksDbBackupService = rocksDbBackupService,
        timestampService = timestampService,
        keyValueStore = keyValueStore,
        backfillOrchestrator = backfillOrchestrator,
        updateOrchestrator = updateOrchestrator,
        cachedVegnett = cachedVegnett,
        tnitsExportService = tnitsExportService,
        clock = clock,
    )

    suspend fun setupBackfill(vararg paths: String) {
        val paths = paths.toList().ifEmpty { readJsonTestResources() }
        val (veglenkesekvenser, vegobjekter) = readTestData(*paths.toTypedArray())
        // Initial stream will return all veglenkesekvenser
        coEvery { uberiketApi.streamVeglenkesekvenser() } returns veglenkesekvenser.asFlow()
        // Next page will return no veglenkesekvenser
        coEvery { uberiketApi.streamVeglenkesekvenser(isNull(true)) } returns emptyFlow()
        for (typeId in mainVegobjektTyper + supportingVegobjektTyper) {
            // Same pattern; initial stream returns all vegobjekter of a given type
            coEvery { uberiketApi.streamVegobjekter(typeId) } returns
                vegobjekter.filter { it.typeId == typeId }.asFlow()
            // Next page returns no vegobjekter of that type
            coEvery { uberiketApi.streamVegobjekter(any(), start = isNull(true)) } returns emptyFlow()
            // When startdato of first version is needed, auto-paginated endpoint is used instead of stream
            coEvery { uberiketApi.getVegobjekterPaginated(any(), any(), any()) } answers {
                val typeId = firstArg<Int>()
                val ids = secondArg<Set<Long>>()
                vegobjekter.filter { it.typeId == typeId && it.id in ids }.asFlow()
            }
        }
        backfillOrchestrator.performBackfill()
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
