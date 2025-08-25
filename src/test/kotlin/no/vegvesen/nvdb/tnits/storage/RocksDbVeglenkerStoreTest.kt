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

class RocksDbVeglenkerStoreTest :
    StringSpec({

        "should store and retrieve veglenker" {
            val tempDir = Files.createTempDirectory("rocksdb-test").toString()
            println("Using temp dir: $tempDir")
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                store.initialize()

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
                            veglenkesekvensId = 1L,
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
                store.upsert(1L, testVeglenker)

                // Test get
                val retrieved = store.get(1L)
                retrieved shouldNotBe null
                retrieved!!.size shouldBe 1
                retrieved[0].veglenkesekvensId shouldBe 1L
                retrieved[0].typeVeg shouldBe TypeVeg.KANALISERT_VEG

                // Test getAll
                val allData = store.getAll()
                allData.size shouldBe 1
                allData[1L] shouldNotBe null

                // Test size
                store.size() shouldBe 1L

                // Test delete
                store.delete(1L)
                store.get(1L) shouldBe null
                store.size() shouldBe 0L
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should handle batch updates" {
            val tempDir = Files.createTempDirectory("rocksdb-batch-test").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = false)

            try {
                store.initialize()

                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(10.0, 60.0),
                                Coordinate(10.1, 60.1),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                val updates =
                    mapOf(
                        1L to
                            listOf(
                                Veglenke(
                                    veglenkesekvensId = 1L,
                                    veglenkenummer = 1,
                                    startposisjon = 0.0,
                                    sluttposisjon = 100.0,
                                    geometri = testGeometry,
                                    typeVeg = TypeVeg.KANALISERT_VEG,
                                    detaljniva = Detaljniva.VEGTRASE,
                                    superstedfesting = null,
                                ),
                            ),
                        2L to
                            listOf(
                                Veglenke(
                                    veglenkesekvensId = 2L,
                                    veglenkenummer = 1,
                                    startposisjon = 0.0,
                                    sluttposisjon = 200.0,
                                    geometri = testGeometry,
                                    typeVeg = TypeVeg.ENKEL_BILVEG,
                                    detaljniva = Detaljniva.VEGTRASE,
                                    superstedfesting = null,
                                ),
                            ),
                        3L to null, // Delete operation
                    )

                // Add item first for delete test
                store.upsert(
                    3L,
                    listOf(
                        Veglenke(
                            veglenkesekvensId = 3L,
                            veglenkenummer = 1,
                            startposisjon = 0.0,
                            sluttposisjon = 300.0,
                            geometri = testGeometry,
                            typeVeg = TypeVeg.ENKEL_BILVEG,
                            detaljniva = Detaljniva.VEGTRASE,
                            superstedfesting = null,
                        ),
                    ),
                )

                // Perform batch update
                store.batchUpdate(updates)

                // Verify results
                store.size() shouldBe 2L
                store.get(1L) shouldNotBe null
                store.get(2L) shouldNotBe null
                store.get(3L) shouldBe null // Should be deleted
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should benchmark performance vs file cache" {
            val tempDir = Files.createTempDirectory("rocksdb-benchmark").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                store.initialize()

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
                repeat(1000) { i ->
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

                // Benchmark batch insert
                val insertTime =
                    kotlin.time.measureTime {
                        store.batchUpdate(testData)
                    }

                // Benchmark full read
                val readTime =
                    kotlin.time.measureTime {
                        store.getAll()
                    }

                // Benchmark random access
                val randomAccessTime =
                    kotlin.time.measureTime {
                        repeat(100) { i ->
                            store.get((i * 10L) % 1000L)
                        }
                    }

                println("RocksDB Insert time: $insertTime")
                println("RocksDB Read all time: $readTime")
                println("RocksDB Random access time: $randomAccessTime")
                println("RocksDB Size: ${store.size()}")

                store.size() shouldBe 1000L
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }
    })
