package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.io.File
import java.nio.file.Files

class FileVeglenkerStoreTest :
    StringSpec({

        "should store and retrieve veglenker with file backend" {
            val tempDir = Files.createTempDirectory("file-veglenker-test").toString()
            val store = FileVeglenkerStore(tempDir)

            try {
                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(10.0, 60.0),
                                Coordinate(10.1, 60.1),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                val testVeglenker =
                    listOf(
                        Veglenke(
                            veglenkesekvensId = 100L,
                            veglenkenummer = 1,
                            startposisjon = 0.0,
                            sluttposisjon = 100.0,
                            geometri = testGeometry,
                            typeVeg = TypeVeg.KANALISERT_VEG,
                            detaljniva = Detaljniva.VEGTRASE,
                            superstedfesting = null,
                        ),
                    )

                // Test upsert
                store.upsert(100L, testVeglenker)

                // Test get
                val retrieved = store.get(100L)
                retrieved shouldNotBe null
                retrieved!!.size shouldBe 1
                retrieved[0].veglenkesekvensId shouldBe 100L
                retrieved[0].typeVeg shouldBe TypeVeg.KANALISERT_VEG

                // Test persistence - close and reopen
                store.close()
                val store2 = FileVeglenkerStore(tempDir)

                val retrieved2 = store2.get(100L)
                retrieved2 shouldNotBe null
                retrieved2!!.size shouldBe 1
                retrieved2[0].veglenkesekvensId shouldBe 100L

                // Test size
                store2.size() shouldBe 1L

                // Test delete
                store2.delete(100L)
                store2.get(100L) shouldBe null
                store2.size() shouldBe 0L

                store2.close()
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should handle batch operations efficiently" {
            val tempDir = Files.createTempDirectory("file-batch-test").toString()
            val store = FileVeglenkerStore(tempDir)

            try {
                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(10.0, 60.0),
                                Coordinate(10.1, 60.1),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                // Create test data
                val testData = mutableMapOf<Long, List<Veglenke>>()
                repeat(100) { i ->
                    testData[i.toLong()] =
                        listOf(
                            Veglenke(
                                veglenkesekvensId = i.toLong(),
                                veglenkenummer = 1,
                                startposisjon = 0.0,
                                sluttposisjon = 100.0,
                                geometri = testGeometry,
                                typeVeg = if (i % 2 == 0) TypeVeg.KANALISERT_VEG else TypeVeg.ENKEL_BILVEG,
                                detaljniva = Detaljniva.VEGTRASE,
                                superstedfesting = null,
                            ),
                        )
                }

                // Benchmark batch operations
                val batchTime =
                    kotlin.time.measureTime {
                        store.batchUpdate(testData)
                    }

                val getAllTime =
                    kotlin.time.measureTime {
                        store.getAll()
                    }

                println("File store batch insert time: $batchTime")
                println("File store get all time: $getAllTime")
                println("File store size: ${store.size()}")

                store.size() shouldBe 100L

                // Test individual access performance
                val randomAccessTime =
                    kotlin.time.measureTime {
                        repeat(50) { i ->
                            store.get((i * 2L) % 100L)
                        }
                    }

                println("File store random access time: $randomAccessTime")
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }
    })
