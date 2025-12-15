package no.vegvesen.nvdb.tnits.generator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.minio.GetObjectArgs
import io.minio.GetObjectResponse
import io.minio.MinioClient
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import no.vegvesen.nvdb.tnits.common.MinioTestHelper
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektHendelse
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.TnitsExportType
import no.vegvesen.nvdb.tnits.generator.core.model.toDomain
import no.vegvesen.nvdb.tnits.generator.infrastructure.s3.TnitsFeatureS3Exporter.Companion.generateS3Key
import org.testcontainers.containers.MinIOContainer
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * End-to-end tests for TN-ITS export functionality.
 * Tests the complete workflow from data setup through XML generation and S3 export.
 */
class TnitsExportE2ETest : ShouldSpec() {

    private val minioContainer: MinIOContainer = MinioTestHelper.createMinioContainer()
    private lateinit var minioClient: MinioClient
    private val testBucket = "nvdb-tnits-e2e-test"

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioTestHelper.createMinioClient(minioContainer)
            MinioTestHelper.waitForMinioReady(minioClient)
            MinioTestHelper.ensureBucketExists(minioClient, testBucket)
        }

        afterSpec {
            minioClient.close()
            minioContainer.stop()
        }

        beforeEach {
            minioClient.clear(testBucket)
        }

        context("speed limits") {
            should("export snapshot") {
                withTestServices(minioClient) {
                    val timestamp = Instant.parse("2025-09-26T10:30:00Z")
                    val expectedXml = readFile("105-expected-snapshot.xml")
                    setupBackfill()

                    tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)

                    val xml = getExportedXml(timestamp, TnitsExportType.Snapshot)
                    xml shouldBe expectedXml
                }
            }

            should("export update with backup and restore, with closed and removed features") {
                val backfillTimestamp = Instant.parse("2025-09-26T10:30:00Z")
                val updateTimestamp = backfillTimestamp.plus(1.days)
                val expectedXml = readFile("105-expected-update.xml")

                withTestServices(minioClient) {
                    setupBackfill()
                    tnitsExportService.exportSnapshot(backfillTimestamp, ExportedFeatureType.SpeedLimit)
                    rocksDbBackupService.createBackup()
                }

                withTestServices(minioClient) {
                    dbContext.setPreserveOnClose(true)
                    rocksDbBackupService.restoreFromBackup()
                    dbContext.setPreserveOnClose(false)
                    coEvery { uberiketApi.getLatestVeglenkesekvensHendelseId(any()) } returns 1
                    coEvery { uberiketApi.streamVeglenkesekvensHendelser(any()) } returns emptyFlow()
                    coEvery { uberiketApi.getLatestVegobjektHendelseId(any(), any()) } returns 1
                    coEvery { uberiketApi.streamVegobjektHendelser(any(), any()) } answers {
                        val typeId = firstArg<Int>()
                        val start = secondArg<Long?>()
                        when (start) {
                            1L -> {
                                when (typeId) {
                                    105 -> flowOf(
                                        VegobjektHendelse(
                                            hendelseId = 2,
                                            vegobjektId = 78712521,
                                            vegobjektVersjon = 1,
                                        ),
                                        VegobjektHendelse(
                                            hendelseId = 3,
                                            vegobjektId = 83589630,
                                            vegobjektVersjon = 1,
                                        ),
                                    )

                                    else -> emptyFlow()
                                }
                            }

                            else -> emptyFlow()
                        }
                    }
                    // 78712521 er lukket, 83589630 er fjernet
                    coEvery { uberiketApi.getVegobjekterPaginated(105, setOf(78712521, 83589630)) } returns flowOf(
                        objectMapper.readApiVegobjekt("vegobjekt-105-78712521.json")
                            .toDomain()
                            .copy(sluttdato = kotlinx.datetime.LocalDate(2025, 9, 26)),
                    )
                    updateOrchestrator.performUpdate()

                    tnitsExportService.exportUpdate(updateTimestamp, ExportedFeatureType.SpeedLimit)

                    val xml = getExportedXml(updateTimestamp, TnitsExportType.Update)
                    xml shouldBe expectedXml
                }
            }
        }

        context("road names") {
            should("export snapshot") {
                withTestServices(minioClient) {
                    val timestamp = Instant.parse("2025-09-26T10:30:00Z")
                    val expectedXml = readFile("538-expected-snapshot.xml")
                    setupBackfill()

                    tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.RoadName)

                    val xml = getExportedXml(timestamp, TnitsExportType.Snapshot, ExportedFeatureType.RoadName)
                    xml shouldBe expectedXml
                }
            }
        }

        context("road numbers") {
            should("export snapshot") {
                withTestServices(minioClient) {
                    val timestamp = Instant.parse("2025-09-26T10:30:00Z")
                    val expectedXml = readFile("915-expected-snapshot.xml")
                    setupBackfill()

                    tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.RoadNumber)

                    val xml = getExportedXml(timestamp, TnitsExportType.Snapshot, ExportedFeatureType.RoadNumber)
                    xml shouldBe expectedXml
                }
            }
        }

        context("height restrictions") {
            should("export snapshot") {
                withTestServices(minioClient) {
                    val timestamp = Instant.parse("2025-09-26T10:30:00Z")
                    val expectedXml = readFile("591-expected-snapshot.xml")
                    setupBackfill()

                    tnitsExportService.exportSnapshot(timestamp, ExportedFeatureType.MaximumHeight)

                    val xml = getExportedXml(timestamp, TnitsExportType.Snapshot, ExportedFeatureType.MaximumHeight)
                    xml shouldBe expectedXml
                }
            }
        }

        context("automatic mode") {
            should("handle full cycle of snapshot and update exports") {
                withTestServices(minioClient) {
                    dbContext.setPreserveOnClose(true)
                    coEvery { uberiketApi.getLatestVeglenkesekvensHendelseId(any()) } returns 1
                    coEvery { uberiketApi.streamVeglenkesekvensHendelser(any()) } returns emptyFlow()
                    coEvery { uberiketApi.getLatestVegobjektHendelseId(any(), any()) } returns 1
                    coEvery { uberiketApi.streamVegobjektHendelser(any(), any()) } returns emptyFlow()
                    setupBackfill()

                    // Initial export
                    val firstDay = Instant.parse("2025-09-26T10:30:00Z")
                    every { clock.now() } returns firstDay
                    automaticCycle.execute()

                    timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit) shouldBe firstDay
                    timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit).shouldBeNull()

                    // Simulate some changes
                    coEvery { uberiketApi.streamVegobjektHendelser(any(), any()) } answers {
                        val typeId = firstArg<Int>()
                        val start = secondArg<Long?>()
                        when (start) {
                            1L -> {
                                when (typeId) {
                                    105 -> flowOf(
                                        VegobjektHendelse(
                                            hendelseId = 2,
                                            vegobjektId = 78712521,
                                            vegobjektVersjon = 1,
                                        ),
                                    )

                                    else -> emptyFlow()
                                }
                            }

                            else -> emptyFlow()
                        }
                    }
                    coEvery { uberiketApi.getVegobjekterPaginated(105, setOf(78712521)) } returns flowOf(
                        objectMapper.readApiVegobjekt("vegobjekt-105-78712521.json")
                            .toDomain()
                            .copy(sluttdato = kotlinx.datetime.LocalDate(2025, 9, 26)),
                    )

                    val secondDay = firstDay.plus(1.days)
                    every { clock.now() } returns secondDay
                    automaticCycle.execute()
                    timestampService.getLastSnapshotTimestamp(ExportedFeatureType.SpeedLimit) shouldBe firstDay
                    timestampService.getLastUpdateTimestamp(ExportedFeatureType.SpeedLimit) shouldBe secondDay

                    dbContext.setPreserveOnClose(false)
                }
            }
        }
    }

    private fun getExportedXml(timestamp: Instant, exportType: TnitsExportType, featureType: ExportedFeatureType = ExportedFeatureType.SpeedLimit): String {
        val key = generateS3Key(timestamp, exportType, false, featureType)
        return streamS3Object(key).use { stream ->
            stream.reader().readText()
        }
    }

    private fun streamS3Object(key: String): GetObjectResponse = minioClient.getObject(
        GetObjectArgs.builder()
            .bucket(testBucket)
            .`object`(key)
            .build(),
    )
}
