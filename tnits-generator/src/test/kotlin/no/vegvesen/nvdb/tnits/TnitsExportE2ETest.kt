package no.vegvesen.nvdb.tnits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.minio.*
import io.mockk.coEvery
import io.mockk.mockk
import no.vegvesen.nvdb.tnits.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.config.ExportTarget
import no.vegvesen.nvdb.tnits.config.ExporterConfig
import no.vegvesen.nvdb.tnits.gateways.UberiketApi
import no.vegvesen.nvdb.tnits.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.openlr.OpenLrService
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig
import no.vegvesen.nvdb.tnits.services.EgenskapService
import no.vegvesen.nvdb.tnits.services.EgenskapService.Companion.hardcodedFartsgrenseTillatteVerdier
import no.vegvesen.nvdb.tnits.storage.*
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjekterService
import org.testcontainers.containers.MinIOContainer
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
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
                val expected = readFile("expected-snapshot.xml")
                setupBackfill()

                tnitsFeatureExporter.exportSnapshot(timestamp, ExportedFeatureType.SpeedLimit)

                val key = tnitsFeatureExporter.generateS3Key(timestamp, TnitsFeatureExporter.ExportType.Snapshot, ExportedFeatureType.SpeedLimit)
                streamS3Object(key).use { stream ->
                    val xml = stream.reader().readText()

                    xml shouldBe expected
                    val (warnings, errors) = validateXmlWithSchemaHints(xml.byteInputStream())
                    warnings.shouldBeEmpty()
                    errors.shouldBeEmpty()
                }
            }
        }
    }

    private fun streamS3Object(key: String): GetObjectResponse = minioClient.getObject(
        GetObjectArgs.builder()
            .bucket(testBucket)
            .`object`(key)
            .build(),
    )

    private suspend fun setupFeatureExporter(dbContext: TempRocksDbConfig, files: List<String> = readJsonTestResources()): TnitsFeatureExporter {
        val (veglenkesekvenser, vegobjekter) = readTestData(*files.toTypedArray())
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
//        val cachedVegnett = setupCachedVegnett(
//            dbContext,
//            *files.toTypedArray(),
//        )
        cachedVegnett.initialize()
        val hashStore = VegobjekterHashStore(dbContext)
        val egenskapService = mockk<EgenskapService> { coEvery { getKmhByEgenskapVerdi() } returns hardcodedFartsgrenseTillatteVerdier }
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
        return tnitsFeatureExporter
    }
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
