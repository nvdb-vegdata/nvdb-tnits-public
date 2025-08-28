package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
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
                            startnode = 100,
                            sluttnode = 200,
                            startdato = LocalDate(2020, 1, 1),
                            geometri = testGeometry,
                            typeVeg = TypeVeg.KANALISERT_VEG,
                            detaljniva = Detaljniva.VEGTRASE,
                            superstedfesting = null,
                        ),
                    )

                // Test upsert
                store.upsertVeglenker(1L, testVeglenker)

                // Test get
                val retrieved = store.getVeglenker(1L)
                retrieved shouldNotBe null
                retrieved!!.size shouldBe 1
                retrieved[0].veglenkesekvensId shouldBe 1L
                retrieved[0].typeVeg shouldBe TypeVeg.KANALISERT_VEG

                // Test getAll
                val allData = store.getAllVeglenker()
                allData.size shouldBe 1
                allData[1L] shouldNotBe null

                // Test size
                store.size() shouldBe 1L

                // Test delete
                store.deleteVeglenker(1L)
                store.getVeglenker(1L) shouldBe null
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
                                    startnode = 100,
                                    sluttnode = 200,
                                    geometri = testGeometry,
                                    typeVeg = TypeVeg.KANALISERT_VEG,
                                    detaljniva = Detaljniva.VEGTRASE,
                                    superstedfesting = null,
                                    startdato = LocalDate(2020, 1, 1),
                                ),
                            ),
                        2L to
                            listOf(
                                Veglenke(
                                    veglenkesekvensId = 2L,
                                    veglenkenummer = 1,
                                    startposisjon = 0.0,
                                    sluttposisjon = 200.0,
                                    startnode = 100,
                                    sluttnode = 200,
                                    geometri = testGeometry,
                                    typeVeg = TypeVeg.ENKEL_BILVEG,
                                    detaljniva = Detaljniva.VEGTRASE,
                                    superstedfesting = null,
                                    startdato = LocalDate(2020, 1, 1),
                                ),
                            ),
                        3L to null, // Delete operation
                    )

                // Add item first for delete test
                store.upsertVeglenker(
                    3L,
                    listOf(
                        Veglenke(
                            veglenkesekvensId = 3L,
                            veglenkenummer = 1,
                            startposisjon = 0.0,
                            sluttposisjon = 300.0,
                            startnode = 100,
                            sluttnode = 200,
                            geometri = testGeometry,
                            typeVeg = TypeVeg.ENKEL_BILVEG,
                            detaljniva = Detaljniva.VEGTRASE,
                            superstedfesting = null,
                            startdato = LocalDate(2020, 1, 1),
                        ),
                    ),
                )

                // Perform batch update
                store.batchUpdateVeglenker(updates)

                // Verify results
                store.size() shouldBe 2L
                store.getVeglenker(1L) shouldNotBe null
                store.getVeglenker(2L) shouldNotBe null
                store.getVeglenker(3L) shouldBe null // Should be deleted
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should benchmark performance vs file cache" {
            val tempDir = Files.createTempDirectory("rocksdb-benchmark").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

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
                repeat(1000) { i ->
                    testData[i.toLong()] =
                        listOf(
                            Veglenke(
                                veglenkesekvensId = i.toLong(),
                                veglenkenummer = 1,
                                startposisjon = 0.0,
                                sluttposisjon = 100.0,
                                startnode = 100,
                                sluttnode = 200,
                                geometri = testGeometry,
                                typeVeg = if (i % 2 == 0) TypeVeg.KANALISERT_VEG else TypeVeg.ENKEL_BILVEG,
                                detaljniva = Detaljniva.VEGTRASE,
                                superstedfesting = null,
                                startdato = LocalDate(2020, 1, 1),
                            ),
                        )
                }

                // Benchmark batch insert
                val insertTime =
                    kotlin.time.measureTime {
                        store.batchUpdateVeglenker(testData)
                    }

                // Benchmark full read
                val readTime =
                    kotlin.time.measureTime {
                        store.getAllVeglenker()
                    }

                // Benchmark random access
                val randomAccessTime =
                    kotlin.time.measureTime {
                        repeat(100) { i ->
                            store.getVeglenker((i * 10L) % 1000L)
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

        "should handle concurrent reads from multiple coroutines" {
            val tempDir = Files.createTempDirectory("rocksdb-concurrent-test").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(590000.0, 6640000.0),
                                Coordinate(591000.0, 6641000.0),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                // Populate store with test data
                val testData = mutableMapOf<Long, List<Veglenke>>()
                repeat(100) { i ->
                    testData[i.toLong()] =
                        listOf(
                            Veglenke(
                                veglenkesekvensId = i.toLong(),
                                veglenkenummer = 1,
                                startposisjon = 0.0,
                                sluttposisjon = (i + 1) * 100.0,
                                startnode = 100,
                                sluttnode = 200,
                                geometri = testGeometry,
                                typeVeg = if (i % 2 == 0) TypeVeg.KANALISERT_VEG else TypeVeg.ENKEL_BILVEG,
                                detaljniva = Detaljniva.VEGTRASE,
                                startdato = LocalDate(2020, 1, 1),
                            ),
                        )
                }

                store.batchUpdateVeglenker(testData)
                store.size() shouldBe 100L

                // Test concurrent reads from multiple coroutines
                val concurrentReads = 20
                val readsPerCoroutine = 50

                val readTime =
                    kotlin.time.measureTime {
                        coroutineScope {
                            val results =
                                (1..concurrentReads)
                                    .map { coroutineId ->
                                        async {
                                            val readResults = mutableListOf<List<Veglenke>?>()
                                            repeat(readsPerCoroutine) { readIndex ->
                                                val veglenkesekvensId = (readIndex % 100).toLong()
                                                val result = store.getVeglenker(veglenkesekvensId)
                                                readResults.add(result)
                                            }
                                            Triple(coroutineId, readResults.size, readResults.count { it != null })
                                        }
                                    }.awaitAll()

                            println("Concurrent read results:")
                            results.forEach { (coroutineId, totalReads, successfulReads) ->
                                println("  Coroutine $coroutineId: $successfulReads/$totalReads successful reads")
                                successfulReads shouldBe totalReads // All reads should succeed
                            }
                        }
                    }

                println("Concurrent reads completed in: $readTime")

                // Verify data integrity after concurrent access
                val finalSize = store.size()
                finalSize shouldBe 100L

                // Spot check some data to ensure it wasn't corrupted
                val spotCheck = store.getVeglenker(50L)
                spotCheck shouldNotBe null
                spotCheck!!.size shouldBe 1
                spotCheck[0].veglenkesekvensId shouldBe 50L
                spotCheck[0].sluttposisjon shouldBe 5100.0
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should handle batch get operations efficiently" {
            val tempDir = Files.createTempDirectory("rocksdb-batch-get-test").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(590000.0, 6640000.0),
                                Coordinate(591000.0, 6641000.0),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                // Populate store with test data
                val testData = mutableMapOf<Long, List<Veglenke>>()
                repeat(50) { i ->
                    testData[i.toLong()] =
                        listOf(
                            Veglenke(
                                veglenkesekvensId = i.toLong(),
                                veglenkenummer = 1,
                                startposisjon = 0.0,
                                sluttposisjon = (i + 1) * 100.0,
                                startnode = 100,
                                sluttnode = 200,
                                geometri = testGeometry,
                                typeVeg = if (i % 2 == 0) TypeVeg.KANALISERT_VEG else TypeVeg.ENKEL_BILVEG,
                                detaljniva = Detaljniva.VEGTRASE,
                                startdato = LocalDate(2020, 1, 1),
                            ),
                        )
                }

                store.batchUpdateVeglenker(testData)

                // Test batch get with existing keys
                val requestedIds = setOf(5L, 10L, 15L, 20L, 25L)
                val batchResult = store.batchGetVeglenker(requestedIds)

                batchResult.size shouldBe 5
                batchResult[5L] shouldNotBe null
                batchResult[10L] shouldNotBe null
                batchResult[15L] shouldNotBe null
                batchResult[20L] shouldNotBe null
                batchResult[25L] shouldNotBe null

                // Verify data integrity
                batchResult[5L]!![0].veglenkesekvensId shouldBe 5L
                batchResult[5L]!![0].sluttposisjon shouldBe 600.0
                batchResult[10L]!![0].typeVeg shouldBe TypeVeg.KANALISERT_VEG
                batchResult[15L]!![0].typeVeg shouldBe TypeVeg.ENKEL_BILVEG

                // Test batch get with mix of existing and non-existing keys
                val mixedIds = setOf(1L, 999L, 2L, 888L, 3L)
                val mixedResult = store.batchGetVeglenker(mixedIds)

                mixedResult.size shouldBe 3 // Only existing keys should be returned
                mixedResult[1L] shouldNotBe null
                mixedResult[2L] shouldNotBe null
                mixedResult[3L] shouldNotBe null
                mixedResult[999L] shouldBe null
                mixedResult[888L] shouldBe null

                // Test empty batch get
                val emptyResult = store.batchGetVeglenker(emptyList())
                emptyResult.size shouldBe 0

                // Performance comparison: batch vs individual gets
                val performanceTestIds = (0L until 20L).toList()

                val batchTime =
                    kotlin.time.measureTime {
                        store.batchGetVeglenker(performanceTestIds)
                    }

                val individualTime =
                    kotlin.time.measureTime {
                        performanceTestIds.forEach { id ->
                            store.getVeglenker(id)
                        }
                    }

                println("Batch get time: $batchTime")
                println("Individual gets time: $individualTime")
                println("Performance improvement: ${individualTime / batchTime}x")

                // Batch should be faster (or at least not significantly slower)
                // Note: This is more of a benchmark than an assertion since timing can vary
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }

        "should handle concurrent read/write operations" {
            val tempDir = Files.createTempDirectory("rocksdb-readwrite-test").toString()
            val store = RocksDbVeglenkerStore(tempDir, enableCompression = true)

            try {
                val geometryFactory = GeometryFactory()
                val testGeometry =
                    geometryFactory
                        .createLineString(
                            arrayOf(
                                Coordinate(590000.0, 6640000.0),
                                Coordinate(591000.0, 6641000.0),
                            ),
                        ).also { it.srid = SRID.UTM33 }

                // Initial data setup
                val initialData = mutableMapOf<Long, List<Veglenke>>()
                repeat(50) { i ->
                    initialData[i.toLong()] =
                        listOf(
                            Veglenke(
                                veglenkesekvensId = i.toLong(),
                                veglenkenummer = 1,
                                startposisjon = 0.0,
                                sluttposisjon = i * 100.0,
                                startnode = 100,
                                sluttnode = 200,
                                geometri = testGeometry,
                                typeVeg = TypeVeg.KANALISERT_VEG,
                                detaljniva = Detaljniva.VEGTRASE,
                                startdato = LocalDate(2020, 1, 1),
                            ),
                        )
                }
                store.batchUpdateVeglenker(initialData)

                val operationTime =
                    kotlin.time.measureTime {
                        coroutineScope {
                            val operations =
                                listOf(
                                    // Reader coroutines
                                    async {
                                        var successfulReads = 0
                                        repeat(100) { readIndex ->
                                            val veglenkesekvensId = (readIndex % 50).toLong()
                                            val result = store.getVeglenker(veglenkesekvensId)
                                            if (result != null) successfulReads++
                                        }
                                        println("Reader 1: $successfulReads/100 successful reads")
                                        successfulReads
                                    },
                                    async {
                                        var successfulReads = 0
                                        repeat(100) { readIndex ->
                                            val veglenkesekvensId = (readIndex % 50).toLong()
                                            val result = store.getVeglenker(veglenkesekvensId)
                                            if (result != null) successfulReads++
                                        }
                                        println("Reader 2: $successfulReads/100 successful reads")
                                        successfulReads
                                    },
                                    // Writer coroutine
                                    async {
                                        var successfulWrites = 0
                                        repeat(25) { writeIndex ->
                                            try {
                                                val veglenkesekvensId = (50 + writeIndex).toLong()
                                                val newVeglenker =
                                                    listOf(
                                                        Veglenke(
                                                            veglenkesekvensId = veglenkesekvensId,
                                                            veglenkenummer = 1,
                                                            startposisjon = 0.0,
                                                            sluttposisjon = writeIndex * 200.0,
                                                            startnode = 100,
                                                            sluttnode = 200,
                                                            geometri = testGeometry,
                                                            typeVeg = TypeVeg.ENKEL_BILVEG,
                                                            detaljniva = Detaljniva.VEGTRASE,
                                                            startdato = LocalDate(2020, 1, 1),
                                                        ),
                                                    )
                                                store.upsertVeglenker(veglenkesekvensId, newVeglenker)
                                                successfulWrites++
                                            } catch (e: Exception) {
                                                println("Write error: ${e.message}")
                                            }
                                        }
                                        println("Writer: $successfulWrites/25 successful writes")
                                        successfulWrites
                                    },
                                )

                            val results = operations.awaitAll()
                            results[0] shouldBe 100 // Reader 1 success count
                            results[1] shouldBe 100 // Reader 2 success count
                            results[2] shouldBe 25 // Writer success count
                        }
                    }

                println("Concurrent read/write operations completed in: $operationTime")

                // Verify final state
                val finalSize = store.size()
                finalSize shouldBe 75L // 50 initial + 25 new
            } finally {
                store.close()
                File(tempDir).deleteRecursively()
            }
        }
    })
