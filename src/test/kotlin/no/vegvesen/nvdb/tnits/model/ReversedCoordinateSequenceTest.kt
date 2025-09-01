package no.vegvesen.nvdb.tnits.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.locationtech.jts.geom.*

class ReversedCoordinateSequenceTest :
    StringSpec({

        val geometryFactory = GeometryFactory()

        "should reverse coordinate order correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0),
                    Coordinate(10.0, 10.0),
                    Coordinate(20.0, 5.0),
                    Coordinate(30.0, 15.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.size() shouldBe 4
            reversedSeq.getCoordinate(0) shouldBe Coordinate(30.0, 15.0)
            reversedSeq.getCoordinate(1) shouldBe Coordinate(20.0, 5.0)
            reversedSeq.getCoordinate(2) shouldBe Coordinate(10.0, 10.0)
            reversedSeq.getCoordinate(3) shouldBe Coordinate(0.0, 0.0)
        }

        "should maintain original dimension" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0, 100.0),
                    Coordinate(10.0, 10.0, 200.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.dimension shouldBe originalSeq.dimension
        }

        "should reverse X and Y coordinates correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                    Coordinate(5.0, 6.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.getX(0) shouldBe 5.0
            reversedSeq.getY(0) shouldBe 6.0
            reversedSeq.getX(1) shouldBe 3.0
            reversedSeq.getY(1) shouldBe 4.0
            reversedSeq.getX(2) shouldBe 1.0
            reversedSeq.getY(2) shouldBe 2.0
        }

        "should reverse ordinate values correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0, 100.0),
                    Coordinate(3.0, 4.0, 200.0),
                    Coordinate(5.0, 6.0, 300.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.getOrdinate(0, 0) shouldBe 5.0 // X
            reversedSeq.getOrdinate(0, 1) shouldBe 6.0 // Y
            reversedSeq.getOrdinate(0, 2) shouldBe 300.0 // Z

            reversedSeq.getOrdinate(2, 0) shouldBe 1.0 // X
            reversedSeq.getOrdinate(2, 1) shouldBe 2.0 // Y
            reversedSeq.getOrdinate(2, 2) shouldBe 100.0 // Z
        }

        "should throw exception when attempting to modify" {
            val coordinates = arrayOf(Coordinate(1.0, 2.0))
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            shouldThrow<UnsupportedOperationException> {
                reversedSeq.setOrdinate(0, 0, 10.0)
            }
        }

        "should return correct coordinate copies" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val copy1 = reversedSeq.getCoordinateCopy(0)
            val copy2 = reversedSeq.getCoordinateCopy(0)

            copy1 shouldBe Coordinate(3.0, 4.0)
            copy2 shouldBe Coordinate(3.0, 4.0)
            // Both getCoordinate() and getCoordinateCopy() return new instances
            copy1 shouldBe copy2 // Same values
            (copy1 !== copy2) shouldBe true // Different instances
        }

        "should fill coordinate object correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val coord = Coordinate()
            reversedSeq.getCoordinate(1, coord)
            coord shouldBe Coordinate(1.0, 2.0)
        }

        "should convert to coordinate array correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                    Coordinate(5.0, 6.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val array = reversedSeq.toCoordinateArray()
            array.size shouldBe 3
            array[0] shouldBe Coordinate(5.0, 6.0)
            array[1] shouldBe Coordinate(3.0, 4.0)
            array[2] shouldBe Coordinate(1.0, 2.0)
        }

        "should expand envelope correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(5.0, 8.0),
                    Coordinate(3.0, 4.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val envelope = reversedSeq.expandEnvelope(null)
            envelope.minX shouldBe 1.0
            envelope.maxX shouldBe 5.0
            envelope.minY shouldBe 2.0
            envelope.maxY shouldBe 8.0
        }

        "should expand existing envelope correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(2.0, 3.0),
                    Coordinate(4.0, 5.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val existingEnvelope = Envelope(0.0, 1.0, 0.0, 1.0)
            val expandedEnvelope = reversedSeq.expandEnvelope(existingEnvelope)

            expandedEnvelope.minX shouldBe 0.0
            expandedEnvelope.maxX shouldBe 4.0
            expandedEnvelope.minY shouldBe 0.0
            expandedEnvelope.maxY shouldBe 5.0
        }

        "should clone correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val cloned = reversedSeq.clone()
            cloned.shouldBeInstanceOf<ReversedCoordinateSequence>()
            cloned shouldNotBe reversedSeq

            cloned.size() shouldBe reversedSeq.size()
            cloned.getCoordinate(0) shouldBe reversedSeq.getCoordinate(0)
        }

        "should copy correctly" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            val copied = reversedSeq.copy()
            copied.shouldBeInstanceOf<ReversedCoordinateSequence>()
            copied shouldNotBe reversedSeq

            copied.size() shouldBe reversedSeq.size()
            copied.getCoordinate(0) shouldBe reversedSeq.getCoordinate(0)
        }

        "should handle empty sequence" {
            val emptySeq = geometryFactory.coordinateSequenceFactory.create(arrayOf<Coordinate>())
            val reversedSeq = ReversedCoordinateSequence(emptySeq)

            reversedSeq.size() shouldBe 0
            reversedSeq.toCoordinateArray().size shouldBe 0
        }

        "should handle single coordinate" {
            val coordinates = arrayOf(Coordinate(5.0, 10.0))
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.size() shouldBe 1
            reversedSeq.getCoordinate(0) shouldBe Coordinate(5.0, 10.0)
            reversedSeq.getX(0) shouldBe 5.0
            reversedSeq.getY(0) shouldBe 10.0
        }

        "should maintain precision with double coordinates" {
            val coordinates =
                arrayOf(
                    Coordinate(123.456789, 987.654321),
                    Coordinate(111.111111, 222.222222),
                )
            val originalSeq = geometryFactory.coordinateSequenceFactory.create(coordinates)
            val reversedSeq = ReversedCoordinateSequence(originalSeq)

            reversedSeq.getX(0) shouldBe 111.111111
            reversedSeq.getY(0) shouldBe 222.222222
            reversedSeq.getX(1) shouldBe 123.456789
            reversedSeq.getY(1) shouldBe 987.654321
        }

        // Tests for reversedView function

        "reversedView should create LineString with reversed coordinate order" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0),
                    Coordinate(10.0, 10.0),
                    Coordinate(20.0, 5.0),
                    Coordinate(30.0, 15.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            reversedLine.shouldBeInstanceOf<LineString>()
            reversedLine.numPoints shouldBe 4
            reversedLine.getCoordinateN(0) shouldBe Coordinate(30.0, 15.0)
            reversedLine.getCoordinateN(1) shouldBe Coordinate(20.0, 5.0)
            reversedLine.getCoordinateN(2) shouldBe Coordinate(10.0, 10.0)
            reversedLine.getCoordinateN(3) shouldBe Coordinate(0.0, 0.0)
        }

        "reversedView should preserve original LineString geometry factory" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            reversedLine.factory shouldBe originalLine.factory
        }

        "reversedView should work with custom geometry factory" {
            val customFactory = GeometryFactory()
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine, customFactory)

            reversedLine.factory shouldBe customFactory
            reversedLine.factory shouldNotBe originalLine.factory
        }

        "reversedView should use same geometry factory and preserve factory SRID" {
            val coordinates =
                arrayOf(
                    Coordinate(590000.0, 6640000.0),
                    Coordinate(591000.0, 6641000.0),
                )
            val factoryWithSrid = GeometryFactory(PrecisionModel(), 25833)
            val originalLine = factoryWithSrid.createLineString(coordinates)

            val reversedLine = reversedView(originalLine)

            reversedLine.factory shouldBe originalLine.factory
            reversedLine.srid shouldBe 25833
        }

        "reversedView should handle simple two-point line" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0),
                    Coordinate(100.0, 100.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            reversedLine.numPoints shouldBe 2
            reversedLine.getCoordinateN(0) shouldBe Coordinate(100.0, 100.0)
            reversedLine.getCoordinateN(1) shouldBe Coordinate(0.0, 0.0)
        }

        "reversedView should handle minimal valid LineString (two points)" {
            val coordinates =
                arrayOf(
                    Coordinate(5.0, 10.0),
                    Coordinate(15.0, 20.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            reversedLine.numPoints shouldBe 2
            reversedLine.getCoordinateN(0) shouldBe Coordinate(15.0, 20.0)
            reversedLine.getCoordinateN(1) shouldBe Coordinate(5.0, 10.0)
        }

        "reversedView should handle empty LineString" {
            val emptyLine = geometryFactory.createLineString()
            val reversedLine = reversedView(emptyLine)

            reversedLine.isEmpty shouldBe true
            reversedLine.numPoints shouldBe 0
        }

        "reversedView should work with 3D coordinates" {
            val coordinates =
                arrayOf(
                    Coordinate(0.0, 0.0, 100.0),
                    Coordinate(10.0, 10.0, 200.0),
                    Coordinate(20.0, 5.0, 150.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            reversedLine.numPoints shouldBe 3
            reversedLine.getCoordinateN(0) shouldBe Coordinate(20.0, 5.0, 150.0)
            reversedLine.getCoordinateN(1) shouldBe Coordinate(10.0, 10.0, 200.0)
            reversedLine.getCoordinateN(2) shouldBe Coordinate(0.0, 0.0, 100.0)
        }

        "reversedView double reversal should equal original" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                    Coordinate(5.0, 6.0),
                    Coordinate(7.0, 8.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val doubleReversedLine = reversedView(reversedView(originalLine))

            doubleReversedLine.numPoints shouldBe originalLine.numPoints
            for (i in 0 until originalLine.numPoints) {
                doubleReversedLine.getCoordinateN(i) shouldBe originalLine.getCoordinateN(i)
            }
        }

        "reversedView should work with complex road geometry" {
            val coordinates =
                arrayOf(
                    Coordinate(590123.45, 6640567.89),
                    Coordinate(590234.56, 6640678.90),
                    Coordinate(590345.67, 6640789.01),
                    Coordinate(590456.78, 6640890.12),
                    Coordinate(590567.89, 6640991.23),
                )
            val factoryWithSrid = GeometryFactory(PrecisionModel(), 25833)
            val roadLine = factoryWithSrid.createLineString(coordinates)

            val reversedRoadLine = reversedView(roadLine)

            reversedRoadLine.numPoints shouldBe 5
            reversedRoadLine.srid shouldBe 25833
            reversedRoadLine.getCoordinateN(0) shouldBe Coordinate(590567.89, 6640991.23)
            reversedRoadLine.getCoordinateN(4) shouldBe Coordinate(590123.45, 6640567.89)
        }

        "reversedView should maintain immutability - changes to original don't affect reversed" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            // Modify original line's SRID
            originalLine.srid = 4326

            // The reversed line should maintain its own state
            reversedLine.getCoordinateN(0) shouldBe Coordinate(3.0, 4.0)
            reversedLine.getCoordinateN(1) shouldBe Coordinate(1.0, 2.0)
        }

        "reversedView coordinate sequence should be read-only" {
            val coordinates =
                arrayOf(
                    Coordinate(1.0, 2.0),
                    Coordinate(3.0, 4.0),
                )
            val originalLine = geometryFactory.createLineString(coordinates)
            val reversedLine = reversedView(originalLine)

            val coordinateSeq = reversedLine.coordinateSequence
            coordinateSeq.shouldBeInstanceOf<ReversedCoordinateSequence>()

            shouldThrow<UnsupportedOperationException> {
                coordinateSeq.setOrdinate(0, 0, 999.0)
            }
        }
    })
