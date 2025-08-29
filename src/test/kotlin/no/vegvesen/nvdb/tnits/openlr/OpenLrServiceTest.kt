package no.vegvesen.nvdb.tnits.openlr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.vegvesen.nvdb.apiles.uberiket.NoderSide
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.cachedVegnett
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.objectMapper
import no.vegvesen.nvdb.tnits.storage.NodePortCountRocksDbStore
import no.vegvesen.nvdb.tnits.storage.RocksDbConfiguration
import no.vegvesen.nvdb.tnits.storage.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class OpenLrServiceTest :
    StringSpec({

        "should convert speed limit stedfesting to OpenLR with real NVDB data" {
            // Setup temporary RocksDB store
            val tempDir = Files.createTempDirectory("openlr-test").toString()
            println("Using temp dir: $tempDir")
            val configuration = RocksDbConfiguration(tempDir, enableCompression = true)
            val veglenkerStore =
                VeglenkerRocksDbStore(
                    configuration.getDatabase(),
                    configuration.getDefaultColumnFamily(),
                )

            try {
                // Load test data from JSON files
                val fartsgrense = objectMapper.readJson<Vegobjekt>("speed-limit-85283803-v2.json")
                val veglenkesekvens =
                    objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvens-41423.json").veglenkesekvenser.single()

                // Parse and convert data
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

                // Populate RocksDB store
                val veglenkesekvensId = 41423L
                veglenkerStore.upsert(veglenkesekvensId, veglenker)

                // Verify data was stored correctly
                val storedVeglenker = veglenkerStore.get(veglenkesekvensId)
                storedVeglenker shouldNotBe null
                storedVeglenker!! shouldHaveSize 17

                // Create OpenLR service and test
                val openLrService = OpenLrService(CachedVegnett(veglenkerStore))

                // Test the conversion
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Verify results
                openLrReferences.shouldNotBeEmpty()
                stedfestinger shouldHaveSize 2
                stedfestinger[0].veglenkesekvensId shouldBe 41423L
                stedfestinger[0].startposisjon shouldBe 0.0
                stedfestinger[0].sluttposisjon shouldBe 0.4010989
                stedfestinger[1].startposisjon shouldBe 0.59010989
                stedfestinger[1].sluttposisjon shouldBe 0.95944735

                println("Successfully created ${openLrReferences.size} OpenLR location references")
                println("Stedfesting segments: ${stedfestinger.size}")
                println("Veglenker count: ${storedVeglenker.size}")
            } finally {
                configuration.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should convert speed limit stedfesting to OpenLR with multi-sequence NVDB data" {
            // Setup temporary RocksDB store
            val tempDir = Files.createTempDirectory("openlr-multi-test").toString()
            println("Using temp dir: $tempDir")
            val configuration = RocksDbConfiguration(tempDir, enableCompression = true)
            val veglenkerStore =
                VeglenkerRocksDbStore(
                    configuration.getDatabase(),
                    configuration.getDefaultColumnFamily(),
                )
            val nodeStore =
                NodePortCountRocksDbStore(
                    configuration.getDatabase(),
                    configuration.getNoderColumnFamily(),
                )

            try {
                // Load test data from JSON files
                val fartsgrense = objectMapper.readJson<Vegobjekt>("speed-limit-85283410-v1.json")
                val veglenkesekvenser =
                    objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvenser-41658-2553792.json").veglenkesekvenser
                val noder = objectMapper.readJson<NoderSide>("noder-from-veglenkesekvenser-41658-2553792.json").noder

                // Parse and convert data for both veglenkesekvenser
                val nodePortCounts = noder.associate { node -> node.id to node.porter.size }
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

                // Add node port counts
                nodePortCounts.forEach { (nodeId, portCount) ->
                    nodeStore.upsert(nodeId, portCount)
                }

                // Verify data was stored correctly
                val storedVeglenker41658 = veglenkerStore.get(41658L)
                val storedVeglenker2553792 = veglenkerStore.get(2553792L)
                storedVeglenker41658 shouldNotBe null
                storedVeglenker2553792 shouldNotBe null
                storedVeglenker41658!! shouldHaveSize 7
                storedVeglenker2553792!! shouldHaveSize 1

                // Create OpenLR service and test
                val openLrService = OpenLrService(cachedVegnett)

                // Test the conversion
                val openLrReferences = openLrService.toOpenLr(stedfestinger)

                // Verify results
                openLrReferences.shouldNotBeEmpty()
                stedfestinger shouldHaveSize 2
                stedfestinger[0].veglenkesekvensId shouldBe 41658L
                stedfestinger[0].startposisjon shouldBe 0.0
                stedfestinger[0].sluttposisjon shouldBe 1.0
                stedfestinger[1].veglenkesekvensId shouldBe 2553792L
                stedfestinger[1].startposisjon shouldBe 0.0
                stedfestinger[1].sluttposisjon shouldBe 1.0
                openLrReferences shouldHaveSize 1
                openLrReferences[0].locationReferencePoints.first().coordinate.should {
                    it.x shouldBe 275584
                    it.y shouldBe 7041006
                }
                openLrReferences[0].locationReferencePoints.last().coordinate.should {
                    it.x shouldBe 275908
                    it.y shouldBe 7040847
                }

                println("Successfully created ${openLrReferences.size} OpenLR location references")
                println("Stedfesting segments: ${stedfestinger.size}")
                println("Veglenker count: ${storedVeglenker41658.size + storedVeglenker2553792.size}")
                println("Node port counts: ${nodePortCounts.size}")
            } finally {
                configuration.close()
                File(tempDir).deleteRecursively()
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
