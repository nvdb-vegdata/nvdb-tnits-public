package no.vegvesen.nvdb.tnits.openlr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.storage.FeltstrekningRepository
import no.vegvesen.nvdb.tnits.storage.FunksjonellVegklasseRepository
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService.Companion.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import org.openlr.binary.BinaryMarshallerFactory
import org.openlr.map.FunctionalRoadClass
import java.io.InputStream

private val marshaller = BinaryMarshallerFactory().create()

class OpenLrServiceTest :
    StringSpec({

        "should convert speed limit stedfesting to OpenLR with real NVDB data" {
            withTempDb { config ->
                // Arrange
                val openLrService =
                    setupOpenLrService(
                        config,
                        "veglenkesekvens-41423.json",
                        "veglenkesekvenser-41437-41438.json",
                    )
                val stedfestinger = loadStedfestinger("speed-limit-85283803-v2.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)
                val binary = openLrReferences.map(marshaller::marshallToBase64String)

                // Assert
                openLrReferences shouldHaveSize 4

                // Første stedfesting, med lenkeretning
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.45458 to 63.43004,
                        10.45995 to 63.42732,
                    )
                // Siste stedfesting, med lenkeretning
                openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.46041 to 63.42712,
                        10.46647 to 63.42433,
                    )
                // Siste stedfesting, mot lenkeretning
                openLrReferences[2].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.46647 to 63.42433,
                        10.46041 to 63.42712,
                    )
                // Første stedfesting, mot lenkeretning
                openLrReferences[3].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.45995 to 63.42732,
                        10.45458 to 63.43004,
                    )

                binary shouldBe
                    listOf(
                        "CwdvMy0bFwIQBwIZ/vADOy0=",
                        "CwdwQy0ajwMMBwJe/ukCfCQV",
                        "CwdxXS0aDQIcB/2iARcDbBUk",
                        "CwdwLi0amAMbB/3nARACUC0=",
                    )
            }
        }

        "should convert speed limit stedfesting to OpenLR with multi-sequence NVDB data" {
            withTempDb { config ->
                // Arrange
                val openLrService =
                    setupOpenLrService(
                        config,
                        "veglenkesekvenser-41658-2553792.json",
                        "veglenkesekvenser-42241-48174-41659.json",
                    )
                val stedfestinger = loadStedfestinger("speed-limit-85283410-v1.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)
                val binary = openLrReferences.map(marshaller::marshallToBase64String)

                // Assert
                openLrReferences shouldHaveSize 2
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.50101 to 63.4266,
                        10.50769 to 63.42537,
                    )
                openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.50769 to 63.42537,
                        10.50101 to 63.4266,
                    )

                binary shouldBe
                    listOf(
                        "Cwd3py0adgMIBgKc/4UDGg==",
                        "Cwd43i0aPQMaBv1kAHsDCA==",
                    )
            }
        }

        "should handle speed limits with gaps in veglenkesekvens" {
            withTempDb { config ->
                // Arrange
                val openLrService =
                    setupOpenLrService(
                        config,
                        "veglenkesekvens-365652.json",
                    )
                val stedfestinger = loadStedfestinger("speedlimit-78712521-v1.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)
                val binary = openLrReferences.map(marshaller::marshallToBase64String)

                // Assert
                openLrReferences shouldHaveSize 3
                openLrReferences.shouldForAll {
                    it.relativeNegativeOffset shouldBe 0
                    it.relativePositiveOffset shouldBe 0
                }
                binary shouldBe
                    listOf(
                        "CwOZ4iuf5gMeAP/dACwDDg==",
                        "CwOZwiugDQMCBgI7AKEDFw==",
                        "CwOazCugWAMXCP4K/wsDHg==",
                    )
            }
        }

        "should handle speed limit stedfestet MOT lenkeretning" {
            withTempDb { config ->
                // Arrange
                val openLrService =
                    setupOpenLrService(
                        config,
                        "veglenkesekvenser_2518522_413032_2518519.json",
                        feltstrekningRepository =
                        mockk {
                            every { findFeltoversiktFromFeltstrekning(any()) }
                                .returns(listOf("2"))
                        },
                    )
                val stedfestinger = loadStedfestinger("speed_limit_589421130_v2.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)
                val binary = openLrReferences.map(marshaller::marshallToBase64String)

                // Assert
                openLrReferences shouldHaveSize 1
                openLrReferences[0].should {
                    it.relativeNegativeOffset shouldBe 0
                    it.relativePositiveOffset shouldBe 0
                }

                binary shouldBe listOf("Cweq+ip17AMSAwB6/1kDGQ==")
            }
        }
    })

private fun setupOpenLrService(
    config: RocksDbContext,
    vararg paths: String,
    feltstrekningRepository: FeltstrekningRepository = mockk<FeltstrekningRepository>(),
    funksjonellVegklasseRepository: FunksjonellVegklasseRepository =
        mockk {
            every { findFunksjonellVegklasse(any()) } returns FunctionalRoadClass.FRC_0
        },
): OpenLrService {
    val veglenkerStore = VeglenkerRocksDbStore(config)
    val veglenkesekvenser =
        paths.flatMap { path ->
            objectMapper.readJson<VeglenkesekvenserSide>(path).veglenkesekvenser
        }

    for (veglenkesekvens in veglenkesekvenser) {
        val veglenker = convertToDomainVeglenker(veglenkesekvens)
        veglenkerStore.upsert(veglenkesekvens.id, veglenker)
    }

    val openLrService =
        OpenLrService(CachedVegnett(veglenkerStore, feltstrekningRepository, funksjonellVegklasseRepository))
    return openLrService
}

private fun loadStedfestinger(path: String): List<StedfestingUtstrekning> {
    val fartsgrense = objectMapper.readJson<Vegobjekt>(path)
    val stedfestinger =
        fartsgrense.getStedfestingLinjer().map { stedfesting ->
            StedfestingUtstrekning(
                veglenkesekvensId = stedfesting.veglenkesekvensId,
                startposisjon = stedfesting.startposisjon,
                sluttposisjon = stedfesting.sluttposisjon,
                retning = stedfesting.retning ?: Retning.MED,
            )
        }
    return stedfestinger
}

inline fun <reified T> ObjectMapper.readJson(name: String): T = streamFile(name).use { inputStream ->
    readValue(inputStream)
}

fun streamFile(name: String): InputStream {
    val function = { }
    return function.javaClass.getResourceAsStream(name.let { if (it.startsWith("/")) it else "/$it" })
        ?: error("Could not find resource $name")
}
