package no.vegvesen.nvdb.tnits.openlr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.objectMapper
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.storage.RocksDbConfiguration
import no.vegvesen.nvdb.tnits.storage.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import java.io.InputStream

class OpenLrServiceTest :
    StringSpec({

        "should convert speed limit stedfesting to OpenLR with real NVDB data" {
            withTempDb { config ->
                // Arrange
                val openLrService = setupOpenLrService(config, "veglenkesekvens-41423.json")
                val stedfestinger = loadStedfestinger("speed-limit-85283803-v2.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Assert
                openLrReferences shouldHaveSize 2
                // Første stedfesting, med lenkeretning
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.45458 to 63.43004,
                        10.45995 to 63.42732,
                    )
                // Siste stedfesting, med lenkeretning
                openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.46049 to 63.42708,
                        10.46598 to 63.42458,
                    )

                // TODO én referanse hver vei
//                // Assert
//                openLrReferences shouldHaveSize 4
//                // Første stedfesting, med lenkeretning
//                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
//                    listOf(
//                        10.45458 to 63.43004,
//                        10.45995 to 63.42732,
//                    )
//                // Første stedfesting, mot lenkeretning
//                openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
//                    listOf(
//                        10.45995 to 63.42732,
//                        10.45458 to 63.43004,
//                    )
//                // Siste stedfesting, med lenkeretning
//                openLrReferences[2].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
//                    listOf(
//                        10.46049 to 63.42708,
//                        10.46598 to 63.42458,
//                    )
//                // Siste stedfesting, mot lenkeretning
//                openLrReferences[3].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
//                    listOf(
//                        10.46049 to 63.42708,
//                        10.46598 to 63.42458,
//                    )
            }
        }

        "should convert speed limit stedfesting to OpenLR with multi-sequence NVDB data" {
            withTempDb { config ->
                // Arrange
                val openLrService = setupOpenLrService(config, "veglenkesekvenser-41658-2553792.json")
                val stedfestinger = loadStedfestinger("speed-limit-85283410-v1.json")

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Assert
                openLrReferences shouldHaveSize 1
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.50101 to 63.4266,
                        10.50769 to 63.42537,
                    )
            }
        }
    })

private fun setupOpenLrService(
    config: RocksDbConfiguration,
    path: String,
): OpenLrService {
    val veglenkerStore =
        VeglenkerRocksDbStore(
            config.getDatabase(),
            config.getDefaultColumnFamily(),
        )
    val veglenkesekvenser =
        objectMapper.readJson<VeglenkesekvenserSide>(path).veglenkesekvenser

    for (veglenkesekven in veglenkesekvenser) {
        val veglenker = convertToDomainVeglenker(veglenkesekven)
        veglenkerStore.upsert(veglenkesekven.id, veglenker)
    }
    val openLrService = OpenLrService(CachedVegnett(veglenkerStore))
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

inline fun <reified T> ObjectMapper.readJson(name: String): T =
    streamFile(name).use { inputStream ->
        readValue(inputStream)
    }

fun streamFile(name: String): InputStream {
    val function = { }
    return function.javaClass.getResourceAsStream(name.let { if (it.startsWith("/")) it else "/$it" })
        ?: error("Could not find resource $name")
}
