package no.vegvesen.nvdb.tnits.serialization

import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class KryoSerializationTest :
    StringSpec({

        "should serialize and deserialize single veglenke list to bytes" {
            val geometryFactory = GeometryFactory()
            val testGeometry =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            val originalVeglenker =
                listOf(
                    Veglenke(
                        veglenkesekvensId = 42L,
                        veglenkenummer = 1,
                        startposisjon = 0.0,
                        sluttposisjon = 100.0,
                        geometri = testGeometry,
                        typeVeg = TypeVeg.KANALISERT_VEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        superstedfesting = null,
                    ),
                )

            // Serialize to bytes
            val serializedBytes =
                ByteArrayOutputStream().use { baos ->
                    Output(baos).use { output ->
                        kryo.writeObject(output, originalVeglenker)
                    }
                    baos.toByteArray()
                }

            println("Serialized ${originalVeglenker.size} veglenker to ${serializedBytes.size} bytes")

            // Deserialize from bytes
            val deserializedVeglenker =
                ByteArrayInputStream(serializedBytes).use { bais ->
                    Input(bais).use { input ->
                        @Suppress("UNCHECKED_CAST")
                        kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
                    }
                }

            // Verify data integrity
            deserializedVeglenker shouldNotBe null
            deserializedVeglenker.size shouldBe 1

            val veglenke = deserializedVeglenker[0]
            veglenke.veglenkesekvensId shouldBe 42L
            veglenke.veglenkenummer shouldBe 1
            veglenke.startposisjon shouldBe 0.0
            veglenke.sluttposisjon shouldBe 100.0
            veglenke.typeVeg shouldBe TypeVeg.KANALISERT_VEG
            veglenke.detaljniva shouldBe Detaljniva.VEGTRASE
            veglenke.superstedfesting shouldBe null

            // Check geometry
            veglenke.geometri shouldNotBe null
            veglenke.geometri.srid shouldBe SRID.UTM33
            veglenke.geometri.coordinates.size shouldBe 2
        }

        "should serialize and deserialize multiple veglenker to bytes" {
            val geometryFactory = GeometryFactory()
            val testGeometry1 =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            val testGeometry2 =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(11.0, 61.0),
                            Coordinate(11.1, 61.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            val originalVeglenker =
                listOf(
                    Veglenke(
                        veglenkesekvensId = 100L,
                        veglenkenummer = 1,
                        startposisjon = 0.0,
                        sluttposisjon = 100.0,
                        geometri = testGeometry1,
                        typeVeg = TypeVeg.KANALISERT_VEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        superstedfesting = null,
                    ),
                    Veglenke(
                        veglenkesekvensId = 200L,
                        veglenkenummer = 2,
                        startposisjon = 0.5,
                        sluttposisjon = 99.5,
                        geometri = testGeometry2,
                        typeVeg = TypeVeg.ENKEL_BILVEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        superstedfesting = null,
                    ),
                )

            // Serialize to bytes
            val serializedBytes =
                ByteArrayOutputStream().use { baos ->
                    Output(baos).use { output ->
                        kryo.writeObject(output, originalVeglenker)
                    }
                    baos.toByteArray()
                }

            println("Serialized ${originalVeglenker.size} veglenker to ${serializedBytes.size} bytes")

            // Deserialize from bytes
            val deserializedVeglenker =
                ByteArrayInputStream(serializedBytes).use { bais ->
                    Input(bais).use { input ->
                        @Suppress("UNCHECKED_CAST")
                        kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
                    }
                }

            // Verify data integrity
            deserializedVeglenker.size shouldBe 2

            val veglenke1 = deserializedVeglenker[0]
            veglenke1.veglenkesekvensId shouldBe 100L
            veglenke1.typeVeg shouldBe TypeVeg.KANALISERT_VEG

            val veglenke2 = deserializedVeglenker[1]
            veglenke2.veglenkesekvensId shouldBe 200L
            veglenke2.typeVeg shouldBe TypeVeg.ENKEL_BILVEG
            veglenke2.startposisjon shouldBe 0.5
        }

        "should handle empty veglenker list" {
            val originalVeglenker = emptyList<Veglenke>()

            // Serialize to bytes
            val serializedBytes =
                ByteArrayOutputStream().use { baos ->
                    Output(baos).use { output ->
                        kryo.writeObject(output, originalVeglenker)
                    }
                    baos.toByteArray()
                }

            println("Serialized empty list to ${serializedBytes.size} bytes")

            // Deserialize from bytes
            val deserializedVeglenker =
                ByteArrayInputStream(serializedBytes).use { bais ->
                    Input(bais).use { input ->
                        @Suppress("UNCHECKED_CAST")
                        kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
                    }
                }

            deserializedVeglenker.size shouldBe 0
        }

        "should benchmark kryo serialization performance" {
            val geometryFactory = GeometryFactory()
            val testGeometry =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            // Create larger test dataset
            val originalVeglenker =
                (1..500).map { i ->
                    Veglenke(
                        veglenkesekvensId = i.toLong(),
                        veglenkenummer = 1,
                        startposisjon = 0.0,
                        sluttposisjon = 100.0,
                        geometri = testGeometry,
                        typeVeg = if (i % 2 == 0) TypeVeg.KANALISERT_VEG else TypeVeg.ENKEL_BILVEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        superstedfesting = null,
                    )
                }

            // Measure serialization time
            lateinit var serializedBytes: ByteArray
            val serializationTime =
                kotlin.time.measureTime {
                    serializedBytes =
                        ByteArrayOutputStream().use { baos ->
                            Output(baos).use { output ->
                                kryo.writeObject(output, originalVeglenker)
                            }
                            baos.toByteArray()
                        }
                }

            // Measure deserialization time
            lateinit var deserializedVeglenker: List<Veglenke>
            val deserializationTime =
                kotlin.time.measureTime {
                    deserializedVeglenker =
                        ByteArrayInputStream(serializedBytes).use { bais ->
                            Input(bais).use { input ->
                                @Suppress("UNCHECKED_CAST")
                                kryo.readObject(input, ArrayList::class.java) as List<Veglenke>
                            }
                        }
                }

            println("Kryo serialization benchmark:")
            println("  Records: ${originalVeglenker.size}")
            println("  Serialized size: ${serializedBytes.size} bytes")
            println("  Serialization time: $serializationTime")
            println("  Deserialization time: $deserializationTime")
            println("  Total time: ${serializationTime + deserializationTime}")
            println("  Records/sec (serialize): ${originalVeglenker.size * 1000 / serializationTime.inWholeMilliseconds}")
            println("  Records/sec (deserialize): ${originalVeglenker.size * 1000 / deserializationTime.inWholeMilliseconds}")

            // Verify integrity
            deserializedVeglenker.size shouldBe originalVeglenker.size
            deserializedVeglenker.first().veglenkesekvensId shouldBe 1L
            deserializedVeglenker.last().veglenkesekvensId shouldBe 500L
        }
    })
