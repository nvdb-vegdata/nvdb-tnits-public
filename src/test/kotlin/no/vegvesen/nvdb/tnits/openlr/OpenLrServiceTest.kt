package no.vegvesen.nvdb.tnits.openlr

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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
import java.io.InputStream
import java.nio.file.Files

class OpenLrServiceTest :
    StringSpec({

        "should convert speed limit stedfesting to OpenLR with real NVDB data" {
            // Setup temporary RocksDB store
            val tempDir = Files.createTempDirectory("openlr-test").toString()
            println("Using temp dir: $tempDir")
            val veglenkerStore = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                // Load test data from JSON files
                val fartsgrense = objectMapper.readJson<Vegobjekt>("speed-limit-85283803-v2.json")
                val veglenkesekvens =
                    objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvens-41423.json").veglenkesekvenser.single()
                val noder = objectMapper.readJson<NoderSide>("noder-from-veglenkesekvens-41423.json").noder

                // Parse and convert data
                val veglenker = convertToDomainVeglenker(veglenkesekvens)
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
                storedVeglenker!! shouldHaveSize 17

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
