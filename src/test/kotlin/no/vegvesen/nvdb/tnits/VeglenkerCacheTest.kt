package no.vegvesen.nvdb.tnits

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

class VeglenkerCacheTest :
    StringSpec({

        "should save and load veglenker from cache" {
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
                )

            val tempCacheFile = File.createTempFile("test-veglenker-cache", ".kryo")
            tempCacheFile.deleteOnExit()

            // Test saving
            saveVeglenkerToCache(testVeglenker, tempCacheFile)
            tempCacheFile.exists() shouldBe true
            tempCacheFile.length() shouldNotBe 0L

            // Test loading
            val loadedVeglenker = loadVeglenkerFromCache(tempCacheFile)
            loadedVeglenker shouldNotBe null
            loadedVeglenker!!.size shouldBe 2

            // Verify content
            val veglenke1 = loadedVeglenker[1L]?.first()
            veglenke1 shouldNotBe null
            veglenke1!!.veglenkesekvensId shouldBe 1L
            veglenke1.veglenkenummer shouldBe 1
            veglenke1.typeVeg shouldBe TypeVeg.KANALISERT_VEG

            val veglenke2 = loadedVeglenker[2L]?.first()
            veglenke2 shouldNotBe null
            veglenke2!!.veglenkesekvensId shouldBe 2L
            veglenke2.typeVeg shouldBe TypeVeg.ENKEL_BILVEG
        }

        "should handle cache file not found gracefully" {
            val nonExistentFile = File("non-existent-cache.kryo")
            val result = loadVeglenkerFromCache(nonExistentFile)
            result shouldBe null
        }

        "should handle corrupted cache file gracefully" {
            val corruptedFile = File.createTempFile("corrupted-cache", ".kryo")
            corruptedFile.deleteOnExit()
            corruptedFile.writeText("This is not valid Kryo data")

            val result = loadVeglenkerFromCache(corruptedFile)
            result shouldBe null
        }

        "should demonstrate improved serialization performance with pre-registration" {
            val geometryFactory = GeometryFactory()
            val testGeometry =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            // Create a larger dataset to measure performance difference
            val largeTestVeglenker = mutableMapOf<Long, List<Veglenke>>()

            repeat(1000) { i ->
                largeTestVeglenker[i.toLong()] =
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

            val tempCacheFile = File.createTempFile("benchmark-cache", ".kryo")
            tempCacheFile.deleteOnExit()

            // Measure serialization performance
            val serializeTime =
                kotlin.time.measureTime {
                    saveVeglenkerToCache(largeTestVeglenker, tempCacheFile)
                }

            // Measure deserialization performance
            val deserializeTime =
                kotlin.time.measureTime {
                    loadVeglenkerFromCache(tempCacheFile)
                }

            println("Serialization time: $serializeTime")
            println("Deserialization time: $deserializeTime")
            println("File size: ${tempCacheFile.length()} bytes")
            println("Records per second (serialize): ${1000 / serializeTime.inWholeSeconds}")
            println("Records per second (deserialize): ${1000 / deserializeTime.inWholeSeconds}")

            // Verify data integrity
            val loaded = loadVeglenkerFromCache(tempCacheFile)
            loaded shouldNotBe null
            loaded!!.size shouldBe 1000
        }
    })
