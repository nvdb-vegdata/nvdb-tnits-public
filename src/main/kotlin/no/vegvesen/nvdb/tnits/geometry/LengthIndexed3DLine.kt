package no.vegvesen.nvdb.tnits.geometry

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.linearref.LengthIndexedLine
import org.locationtech.jts.linearref.LinearGeometryBuilder
import org.locationtech.jts.linearref.LinearIterator
import org.locationtech.jts.linearref.LinearLocation

const val INVALID_Z_VALUE = -999999.0

/**
 * Calculates the 3D length of the geometry. For 2D geometries, the default [Geometry.getLength] is used.
 *
 * Based on the formula for the 3D distance between two points:
 *
 * sqrt((x2 - x1)^2 + (y2 - y1)^2 + (z2 - z1)^2)
 *
 * The sum of distances between all neighboring points is the total length of the geometry.
 *
 */
fun Geometry.get3dLength(): Double =
    if (coordinates.any { it.z.isNaN() }) {
        length
    } else {
        val interpolatedCoordinates = interpolateInvalidZValues(coordinates.toList())
        interpolatedCoordinates
            .zipWithNext()
            .sumOf { (start, end) ->
                start.distance3D(end)
            }
    }

/**
 * Interpolates invalid Z coordinates equal to -999999 by taking the mean of neighboring valid Z coordinates.
 */
fun interpolateInvalidZValues(coordinates: List<Coordinate>): List<Coordinate> =
    coordinates.mapIndexed { index, coord ->
        if (coord.z == INVALID_Z_VALUE) {
            val prevValidZ = coordinates.subList(0, index).lastOrNull { it.z != INVALID_Z_VALUE }?.z
            val nextValidZ =
                coordinates.subList(index + 1, coordinates.size).firstOrNull { it.z != INVALID_Z_VALUE }?.z

            when {
                prevValidZ != null && nextValidZ != null -> Coordinate(coord.x, coord.y, (prevValidZ + nextValidZ) / 2)
                prevValidZ != null -> Coordinate(coord.x, coord.y, prevValidZ)
                nextValidZ != null -> Coordinate(coord.x, coord.y, nextValidZ)
                else -> coord
            }
        } else {
            coord
        }
    }

data class LengthIndexed3DLine(
    private val geometry: Geometry,
) : LengthIndexedLine(geometry) {
    override fun getEndIndex(): Double = geometry.get3dLength()

    // Code below is copied from LengthIndexedLine source code and modified to support 3D geometries

    override fun clampIndex(index: Double): Double {
        val posIndex = if (index >= 0.0) index else geometry.get3dLength() + index
        return if (posIndex < startIndex) {
            startIndex
        } else if (posIndex > endIndex) {
            endIndex
        } else {
            posIndex
        }
    }

    override fun extractLine(
        startIndex: Double,
        endIndex: Double,
    ): Geometry {
        if (geometry.coordinates.any { it.z.isNaN() || it.z == INVALID_Z_VALUE }) {
            return super.extractLine(startIndex, endIndex)
        }
        val start = clampIndex(startIndex)
        val end = clampIndex(endIndex)
        val startLocation = getLocation(start, start == end)
        val endLocation = getLocation(end, true)
        return extract(startLocation, endLocation)
    }

    private fun getLocation(
        length: Double,
        resolveLower: Boolean,
    ): LinearLocation {
        var forwardLength = length

        // negative values are measured from end of geometry
        if (length < 0.0) {
            val lineLen: Double = geometry.get3dLength()
            forwardLength = lineLen + length
        }
        val loc: LinearLocation = getLocationForward(forwardLength)
        if (resolveLower) {
            return loc
        }
        return resolveHigher(loc)
    }

    private fun getLocationForward(length: Double): LinearLocation {
        if (length <= 0.0) return LinearLocation()

        var totalLength = 0.0

        val it = LinearIterator(geometry)
        while (it.hasNext()) {
            /*
             * Special handling is required for the situation when the
             * length references exactly to a component endpoint.
             * In this case, the endpoint location of the current component
             * is returned,
             * rather than the startpoint location of the next component.
             * This produces consistent behaviour with the project method.
             */

            if (it.isEndOfLine) {
                if (totalLength == length) {
                    val compIndex = it.componentIndex
                    val segIndex = it.vertexIndex
                    return LinearLocation(compIndex, segIndex, 0.0)
                }
            } else {
                val p0 = it.segmentStart
                val p1 = it.segmentEnd
                val segLen = p1.distance3D(p0)
                // length falls in this segment
                if (totalLength + segLen > length) {
                    val frac = (length - totalLength) / segLen
                    val compIndex = it.componentIndex
                    val segIndex = it.vertexIndex
                    return LinearLocation(compIndex, segIndex, frac)
                }
                totalLength += segLen
            }

            it.next()
        }
        // length is longer than line - return end location
        return LinearLocation.getEndLocation(geometry)
    }

    private fun resolveHigher(loc: LinearLocation): LinearLocation {
        if (!loc.isEndpoint(geometry)) return loc
        var compIndex = loc.componentIndex
        // if last component can't resolve any higher
        if (compIndex >= geometry.numGeometries - 1) return loc

        do {
            compIndex++
        } while (compIndex < geometry.numGeometries - 1 && geometry.getGeometryN(compIndex).get3dLength() == 0.0)
        // resolve to next higher location
        return LinearLocation(compIndex, 0, 0.0)
    }

    private fun extract(
        start: LinearLocation,
        end: LinearLocation,
    ): Geometry =
        if (end < start) {
            computeLinear(end, start).reverse()
        } else {
            computeLinear(start, end)
        }

    private fun computeLinear(
        start: LinearLocation,
        end: LinearLocation,
    ): Geometry {
        val builder = LinearGeometryBuilder(geometry.factory)
        builder.setFixInvalidLines(true)

        if (!start.isVertex) builder.add(start.getCoordinate(geometry))

        val it = LinearIterator(geometry, start)
        while (it.hasNext()) {
            if (end.compareLocationValues(it.componentIndex, it.vertexIndex, 0.0) < 0) {
                break
            }

            val pt = it.segmentStart
            builder.add(pt)
            if (it.isEndOfLine) builder.endLine()
            it.next()
        }
        if (!end.isVertex) builder.add(end.getCoordinate(geometry))

        return builder.geometry
    }
}
