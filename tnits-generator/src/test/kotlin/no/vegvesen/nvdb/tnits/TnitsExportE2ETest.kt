package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.minio.*
import io.mockk.coEvery
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import no.vegvesen.nvdb.apiles.uberiket.VegobjektNotifikasjon
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import org.testcontainers.containers.MinIOContainer
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.InputStream
import java.time.LocalDate
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * End-to-end tests for TN-ITS export functionality.
 * Tests the complete workflow from data setup through XML generation and S3 export.
 */
class TnitsExportE2ETest : StringSpec() {

    private val minioContainer: MinIOContainer = MinIOContainer("minio/minio:RELEASE.2025-09-07T16-13-09Z")
        .withUserName("testuser")
        .withPassword("testpassword")

    private lateinit var minioClient: MinioClient
    private val testBucket = "nvdb-tnits-e2e-test"

    init {
        beforeSpec {
            minioContainer.start()
            minioClient = MinioClient.builder()
                .endpoint(minioContainer.s3URL)
                .credentials(minioContainer.userName, minioContainer.password)
                .build()

            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(testBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(testBucket).build())
            }
        }

        afterSpec {
            minioClient.close()
            minioContainer.stop()
        }

        beforeEach {
            minioClient.clear(testBucket)
        }

        "export snapshot" {
            withTestServices(minioClient) {
                val timestamp = Instant.parse("2025-09-26T10:30:00Z")
                val expectedXml = readFile("expected-snapshot.xml")
                setupBackfill()

                tnitsFeatureExporter.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)

                val xml = getExportedXml(timestamp, TnitsFeatureExporter.ExportType.Snapshot)
                xml shouldBe expectedXml
                shouldBeValidXsd(xml)
            }
        }

        "export update with backup and restore, with closed and removed vegobjekter" {
            val backfillTimestamp = Instant.parse("2025-09-26T10:30:00Z")
            val updateTimestamp = backfillTimestamp.plus(1.days)
            val expectedXml = readFile("expected-update.xml")

            withTestServices(minioClient) {
                setupBackfill()
                tnitsFeatureExporter.exportSnapshot(backfillTimestamp, ExportedFeatureType.SpeedLimit)
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
                                    VegobjektNotifikasjon().apply {
                                        hendelseId = 2
                                        vegobjektTypeId = 105
                                        vegobjektId = 78712521
                                        vegobjektVersjon = 1
                                        hendelseType = "VegobjektVersjonEndret"
                                    },
                                    VegobjektNotifikasjon().apply {
                                        hendelseId = 3
                                        vegobjektTypeId = 105
                                        vegobjektId = 83589630
                                        vegobjektVersjon = 1
                                        hendelseType = "VegobjektVersjonFjernet"
                                    },
                                )

                                else -> emptyFlow()
                            }
                        }

                        else -> emptyFlow()
                    }
                }
                // 78712521 er lukket, 83589630 er fjernet
                coEvery { uberiketApi.getVegobjekterPaginated(105, setOf(78712521, 83589630)) } returns flowOf(
                    objectMapper.readApiVegobjekt("vegobjekt-105-78712521.json").apply {
                        gyldighetsperiode!!.sluttdato = LocalDate.parse("2025-09-26")
                    },
                )
                performUpdateHandler.performUpdate()

                exportUpdateHandler.exportUpdate(updateTimestamp, ExportedFeatureType.SpeedLimit)

                val xml = getExportedXml(updateTimestamp, TnitsFeatureExporter.ExportType.Update)
                xml shouldBe expectedXml
                shouldBeValidXsd(xml)
            }
        }
    }

    private fun TestServices.getExportedXml(
        timestamp: Instant,
        exportType: TnitsFeatureExporter.ExportType,
        featureType: ExportedFeatureType = ExportedFeatureType.SpeedLimit,
    ): String {
        val key = tnitsFeatureExporter.generateS3Key(timestamp, exportType, featureType)
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

private fun shouldBeValidXsd(xml: String) {
    val (warnings, errors) = validateXmlWithSchemaHints(xml.byteInputStream())
    warnings.shouldBeEmpty()
    errors.shouldBeEmpty()
}

data class XsdValidationResult(val warnings: List<SAXParseException>, val errors: List<SAXParseException>)

fun validateXmlWithSchemaHints(xmlStream: InputStream): XsdValidationResult {
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    // Empty schema: relies on xsi:schemaLocation or xsi:noNamespaceSchemaLocation
    val schema = schemaFactory.newSchema()
    val warnings = mutableListOf<SAXParseException>()
    val errors = mutableListOf<SAXParseException>()
    val validator = schema.newValidator().apply {
        errorHandler = object : ErrorHandler {
            override fun warning(e: SAXParseException) {
                println("Warning: ${e.message} at ${e.lineNumber}:${e.columnNumber}")
                warnings.add(e)
            }

            override fun error(e: SAXParseException) {
                println("Error: ${e.message} at ${e.lineNumber}:${e.columnNumber}")
                errors.add(e)
            }

            override fun fatalError(e: SAXParseException): Unit = throw e
        }
    }

    xmlStream.use {
        validator.validate(StreamSource(it))
    }
    return XsdValidationResult(warnings, errors)
}
