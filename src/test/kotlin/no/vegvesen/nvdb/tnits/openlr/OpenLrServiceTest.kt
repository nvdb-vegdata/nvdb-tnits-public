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
                val veglenkerStore =
                    VeglenkerRocksDbStore(
                        config.getDatabase(),
                        config.getDefaultColumnFamily(),
                    )
                val fartsgrense = objectMapper.readJson<Vegobjekt>("speed-limit-85283803-v2.json")
                val veglenkesekvens =
                    objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvens-41423.json").veglenkesekvenser.single()
                val veglenker = convertToDomainVeglenker(veglenkesekvens)
                val stedfestinger =
                    fartsgrense.getStedfestingLinjer().map { stedfesting ->
                        StedfestingUtstrekning(
                            veglenkesekvensId = stedfesting.veglenkesekvensId,
                            startposisjon = stedfesting.startposisjon,
                            sluttposisjon = stedfesting.sluttposisjon,
                            retning = stedfesting.retning ?: Retning.MED,
                        )
                    }
                veglenkerStore.upsert(veglenkesekvens.id, veglenker)
                val openLrService = OpenLrService(CachedVegnett(veglenkerStore))

                // Act
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Assert
                openLrReferences shouldHaveSize 2
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.45458 to 63.43004,
                        10.45995 to 63.42732,
                    )
                openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.46049 to 63.42708,
                        10.46598 to 63.42458,
                    )
            }
        }

        "should convert speed limit stedfesting to OpenLR with multi-sequence NVDB data" {
            withTempDb { config ->
                val veglenkerStore =
                    VeglenkerRocksDbStore(
                        config.getDatabase(),
                        config.getDefaultColumnFamily(),
                    )

                // Load test data from JSON files
                val fartsgrense = objectMapper.readJson<Vegobjekt>("speed-limit-85283410-v1.json")
                val veglenkesekvenser =
                    objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvenser-41658-2553792.json").veglenkesekvenser

                // Parse and convert data for both veglenkesekvenser
                val stedfestinger =
                    fartsgrense.getStedfestingLinjer().map { stedfesting ->
                        StedfestingUtstrekning(
                            veglenkesekvensId = stedfesting.veglenkesekvensId,
                            startposisjon = stedfesting.startposisjon,
                            sluttposisjon = stedfesting.sluttposisjon,
                            retning = stedfesting.retning ?: Retning.MED,
                        )
                    }

                // Populate RocksDB store with both veglenkesekvenser
                veglenkesekvenser.forEach { veglenkesekvens ->
                    val veglenker = convertToDomainVeglenker(veglenkesekvens)
                    veglenkerStore.upsert(veglenkesekvens.id, veglenker)
                }

                // Create OpenLR service and test
                val openLrService = OpenLrService(CachedVegnett(veglenkerStore))

                // Test the conversion
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Verify results
                openLrReferences shouldHaveSize 1
                openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                    listOf(
                        10.50101 to 63.4266,
                        10.50769 to 63.42537,
                    )
            }
        }
    })

inline fun <reified T> ObjectMapper.readJson(name: String): T =
    streamFile(name).use { inputStream ->
        readValue(inputStream)
    }

fun streamFile(name: String): InputStream {
    val function = { }
    return function.javaClass.getResourceAsStream(name.let { if (it.startsWith("/")) it else "/$it" })
        ?: error("Could not find resource $name")
}
