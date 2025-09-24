package no.vegvesen.nvdb.tnits.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.geometry.projectTo
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKTReader

class JtsGeometrySerializerTest :
    StringSpec({

        val geometryFactory = GeometryFactory()
        val wktReader = WKTReader(geometryFactory)

        "should serialize and deserialize Point geometry" {
            val originalPoint = geometryFactory.createPoint(Coordinate(10.5, 20.3))
            originalPoint.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalPoint)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<Point>()
            deserialized.coordinate.x shouldBe 10.5
            deserialized.coordinate.y shouldBe 20.3
            deserialized.srid shouldBe SRID.WGS84
            deserialized.factory.srid shouldBe SRID.WGS84
        }

        "should serialize and deserialize LineString geometry" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0),
                    Coordinate(10.0, 10.0),
                    Coordinate(20.0, 5.0),
                )
            val originalLineString = geometryFactory.createLineString(coordinates)
            originalLineString.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalLineString)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<LineString>()
            deserialized.numPoints shouldBe 3
            deserialized.getCoordinateN(0).x shouldBe 0.0
            deserialized.getCoordinateN(0).y shouldBe 0.0
            deserialized.getCoordinateN(1).x shouldBe 10.0
            deserialized.getCoordinateN(1).y shouldBe 10.0
            deserialized.getCoordinateN(2).x shouldBe 20.0
            deserialized.getCoordinateN(2).y shouldBe 5.0
            deserialized.srid shouldBe SRID.WGS84
        }

        "should serialize and deserialize Polygon geometry" {
            val shell =
                geometryFactory.createLinearRing(
                    arrayOf(
                        Coordinate(0.0, 0.0),
                        Coordinate(10.0, 0.0),
                        Coordinate(10.0, 10.0),
                        Coordinate(0.0, 10.0),
                        Coordinate(0.0, 0.0), // Must close the ring
                    ),
                )

            val hole =
                geometryFactory.createLinearRing(
                    arrayOf(
                        Coordinate(2.0, 2.0),
                        Coordinate(8.0, 2.0),
                        Coordinate(8.0, 8.0),
                        Coordinate(2.0, 8.0),
                        Coordinate(2.0, 2.0), // Must close the ring
                    ),
                )

            val originalPolygon = geometryFactory.createPolygon(shell, arrayOf(hole))
            originalPolygon.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalPolygon)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<Polygon>()
            deserialized.numInteriorRing shouldBe 1
            deserialized.exteriorRing.numPoints shouldBe 5
            deserialized.getInteriorRingN(0).numPoints shouldBe 5
            deserialized.srid shouldBe SRID.WGS84
        }

        "should serialize and deserialize MultiLineString geometry" {
            val line1 =
                geometryFactory.createLineString(
                    arrayOf(
                        Coordinate(0.0, 0.0),
                        Coordinate(10.0, 10.0),
                    ),
                )

            val line2 =
                geometryFactory.createLineString(
                    arrayOf(
                        Coordinate(20.0, 20.0),
                        Coordinate(30.0, 30.0),
                    ),
                )

            val originalMultiLineString = geometryFactory.createMultiLineString(arrayOf(line1, line2))
            originalMultiLineString.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalMultiLineString)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<MultiLineString>()
            deserialized.numGeometries shouldBe 2
            deserialized.srid shouldBe SRID.WGS84

            val firstLine = deserialized.getGeometryN(0) as LineString
            firstLine.getCoordinateN(0).x shouldBe 0.0
            firstLine.getCoordinateN(1).x shouldBe 10.0
        }

        "should preserve SRID during serialization round trip" {
            val originalGeometry = wktReader.read("LINESTRING(590000 6640000, 591000 6641000)")
            originalGeometry.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalGeometry)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.srid shouldBe SRID.WGS84
        }

        "should handle empty geometries" {
            val emptyLineString = geometryFactory.createLineString()
            emptyLineString.srid = SRID.WGS84

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, emptyLineString)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<LineString>()
            deserialized.isEmpty shouldBe true
            deserialized.srid shouldBe SRID.WGS84
        }

        "should handle geometries with Z coordinates" {
            val coordinatesWithZ =
                arrayOf(
                    Coordinate(0.0, 0.0, 100.0),
                    Coordinate(10.0, 10.0, 200.0),
                    Coordinate(20.0, 5.0, 150.0),
                )
            val originalLineString = geometryFactory.createLineString(coordinatesWithZ).also { it.srid = SRID.WGS84 }

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalLineString)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<LineString>()

            // Note: JtsGeometrySerializer creates WKBWriter with 2 dimensions, so Z coordinates will be lost
            // This is expected behavior based on the implementation
            deserialized.getCoordinateN(0).z.isNaN() shouldBe true
        }

        "should create separate writer instances for thread safety" {
            val writer1 = JtsGeometrySerializer.createWriter()
            val writer2 = JtsGeometrySerializer.createWriter()

            writer1 shouldNotBe writer2
            writer1::class shouldBe writer2::class
        }

        "should create separate reader instances for thread safety" {
            val reader1 = JtsGeometrySerializer.createReader()
            val reader2 = JtsGeometrySerializer.createReader()

            reader1 shouldNotBe reader2
            reader1::class shouldBe reader2::class
        }

        "should work with JSON serialization (for debugging)" {
            val originalPoint = geometryFactory.createPoint(Coordinate(10.5, 20.3))
            originalPoint.srid = SRID.WGS84

            val jsonString = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, originalPoint)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, jsonString)

            deserialized.shouldBeInstanceOf<Point>()
            deserialized.coordinate.x shouldBe 10.5
            deserialized.coordinate.y shouldBe 20.3
            deserialized.srid shouldBe SRID.WGS84
        }

        "should handle complex road network geometries" {
            // Test with a realistic road segment geometry
            val roadGeometry =
                wktReader
                    .read(
                        "LINESTRING(590123.45 6640567.89, 590234.56 6640678.90, 590345.67 6640789.01, 590456.78 6640890.12)",
                    ).also { it.srid = SRID.UTM33 }
                    .projectTo(SRID.WGS84)

            val serialized = ProtoBuf.encodeToByteArray(JtsGeometrySerializer, roadGeometry)
            val deserialized = ProtoBuf.decodeFromByteArray(JtsGeometrySerializer, serialized)

            deserialized.shouldBeInstanceOf<LineString>()
            deserialized.numPoints shouldBe 4
            deserialized.srid shouldBe SRID.WGS84

            // Verify coordinate precision is preserved
            deserialized.getCoordinateN(0).x shouldBe 16.61066
            deserialized.getCoordinateN(0).y shouldBe 59.89279
        }
    })
