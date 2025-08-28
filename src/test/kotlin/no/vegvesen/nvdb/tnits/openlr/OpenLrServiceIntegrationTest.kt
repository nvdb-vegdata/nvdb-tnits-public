package no.vegvesen.nvdb.tnits.openlr

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.vegvesen.nvdb.apiles.uberiket.NoderSide
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.objectMapper
import no.vegvesen.nvdb.tnits.storage.RocksDbVeglenkerStore
import no.vegvesen.nvdb.tnits.vegnett.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import java.io.File
import java.nio.file.Files

class OpenLrServiceIntegrationTest :
    StringSpec({

        fun <T> loadTestDataFile(
            filename: String,
            clazz: Class<T>,
        ): T {
            val testDataPath = "src/test/resources/test-data/$filename"
            val file = File(testDataPath)
            return objectMapper.readValue(file, clazz)
        }

        fun extractNodePortCounts(noderSide: NoderSide): Map<Long, Int> =
            noderSide.noder.associate { node ->
                node.id to node.porter.size
            }

        fun createStedfestingFromSpeedLimit(speedLimitVegobjekt: Vegobjekt): List<StedfestingUtstrekning> {
            val stedfestingLinjer = speedLimitVegobjekt.getStedfestingLinjer()

            return stedfestingLinjer.map { stedfesting ->
                StedfestingUtstrekning(
                    veglenkesekvensId = stedfesting.veglenkesekvensId,
                    startposisjon = stedfesting.startposisjon,
                    sluttposisjon = stedfesting.sluttposisjon,
                    retning = stedfesting.retning ?: Retning.MED,
                )
            }
        }

        "should convert speed limit stedfesting to OpenLR with real NVDB data" {
            // Setup temporary RocksDB store
            val tempDir = Files.createTempDirectory("openlr-test").toString()
            println("Using temp dir: $tempDir")
            val veglenkerStore = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                // Load test data using proper API models
                val speedLimitVegobjekt = loadTestDataFile("speed-limit-85283803-v2.json", Vegobjekt::class.java)
                val veglenkesekvenser = loadTestDataFile("veglenkesekvens-41423.json", VeglenkesekvenserSide::class.java)
                val noderSide = loadTestDataFile("noder-from-veglenkesekvens-41423.json", NoderSide::class.java)

                // Convert and extract data
                val veglenkesekvens = veglenkesekvenser.veglenkesekvenser.first()
                val veglenker = convertToDomainVeglenker(veglenkesekvens)
                val nodePortCounts = extractNodePortCounts(noderSide)
                val stedfestinger = createStedfestingFromSpeedLimit(speedLimitVegobjekt)

                // Populate RocksDB store
                val veglenkesekvensId = 41423L
                veglenkerStore.upsertVeglenker(veglenkesekvensId, veglenker)

                // Add node port counts
                nodePortCounts.forEach { (nodeId, portCount) ->
                    veglenkerStore.upsertNodePortCount(nodeId, portCount)
                }

                // Verify data was stored correctly
                val storedVeglenker = veglenkerStore.getVeglenker(veglenkesekvensId)
                storedVeglenker shouldNotBe null
                storedVeglenker!! shouldHaveSize 18

                // Create OpenLR service and test
                val openLrService = OpenLrService(veglenkerStore)

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
                println("Node port counts: ${nodePortCounts.size}")
            } finally {
                veglenkerStore.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should create valid OpenLR lines from veglenker" {
            val tempDir = Files.createTempDirectory("openlr-lines-test").toString()
            val veglenkerStore = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                // Load and setup test data
                val veglenkesekvenser = loadTestDataFile("veglenkesekvens-41423.json", VeglenkesekvenserSide::class.java)
                val noderSide = loadTestDataFile("noder-from-veglenkesekvens-41423.json", NoderSide::class.java)

                val veglenkesekvens = veglenkesekvenser.veglenkesekvenser.first()
                val veglenker = convertToDomainVeglenker(veglenkesekvens)
                val nodePortCounts = extractNodePortCounts(noderSide)

                veglenkerStore.upsertVeglenker(41423L, veglenker)
                nodePortCounts.forEach { (nodeId, portCount) ->
                    veglenkerStore.upsertNodePortCount(nodeId, portCount)
                }

                // Test line creation
                val openLrService = OpenLrService(veglenkerStore)
                val lines = openLrService.getLines(veglenker)

                // Verify lines
                lines shouldHaveSize 18
                lines.forEach { line ->
                    line.id shouldNotBe null
                    line.startnode shouldNotBe null
                    line.sluttnode shouldNotBe null
                    line.geometri shouldNotBe null
                    line.frc shouldNotBe null
                    line.fow shouldNotBe null
                }

                // Test line locations
                val locations = openLrService.getLineLocations(lines)
                locations shouldHaveSize 1

                println("Created ${lines.size} OpenLR lines")
                println("Created ${locations.size} line locations")
            } finally {
                veglenkerStore.close()
                File(tempDir).deleteRecursively()
            }
        }
    })
