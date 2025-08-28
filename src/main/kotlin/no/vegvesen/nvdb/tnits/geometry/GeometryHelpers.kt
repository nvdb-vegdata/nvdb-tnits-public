package no.vegvesen.nvdb.tnits.geometry

import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.intersect
import no.vegvesen.nvdb.tnits.model.overlaps
import org.geotools.api.referencing.FactoryException
import org.geotools.api.referencing.NoSuchAuthorityCodeException
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.linearref.LengthIndexedLine
import org.locationtech.jts.operation.linemerge.LineMerger
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import javax.xml.crypto.dsig.TransformException

object SRID {
    const val UTM33 = 25833
    const val WGS84 = 4326
}

val geometryFactories =
    mapOf(
        SRID.UTM33 to GeometryFactory(PrecisionModel(10.0), SRID.UTM33),
        SRID.WGS84 to GeometryFactory(PrecisionModel(100_000.0), SRID.WGS84),
    )

val wktReaders =
    mapOf(
        SRID.UTM33 to WKTReader(geometryFactories[SRID.UTM33]),
        SRID.WGS84 to WKTReader(geometryFactories[SRID.WGS84]),
    )

fun parseWkt(
    wkt: String,
    srid: Int,
): Geometry = wktReaders[srid]?.read(wkt) ?: error("Unsupported SRID: $srid")

fun getCrs(srid: Int): CoordinateReferenceSystem = CRS.decode("EPSG:$srid")

fun Geometry.projectTo(srid: Int): Geometry =
    if (this.srid == srid) {
        this
    } else {
        try {
            val sourceCrs = getCrs(this.srid)
            val targetCrs = getCrs(srid)
            val transform = CRS.findMathTransform(sourceCrs, targetCrs, true)
            val transformedGeometry = JTS.transform(this, transform)

            val factory = geometryFactories[srid] ?: error("Unsupported SRID: $srid")
            factory.createGeometry(transformedGeometry)
        } catch (e: NoSuchAuthorityCodeException) {
            error("Invalid SRID: ${e.message}")
        } catch (e: FactoryException) {
            error("Error creating CRS: ${e.message}")
        } catch (e: TransformException) {
            error("Error transforming coordinates: ${e.message}")
        }
    }

fun Geometry.simplify(distanceTolerance: Double): Geometry =
    DouglasPeuckerSimplifier
        .simplify(this, distanceTolerance)
        .let {
            if (it.isEmpty) {
                TopologyPreservingSimplifier.simplify(this, distanceTolerance)
            } else {
                it
            }
        }.also { it.srid = this.srid }

data class UtstrekningGeometri(
    val utstrekning: StedfestingUtstrekning,
    val geometri: Geometry,
)

fun calculateIntersectingGeometry(
    utstrekningGeometri: UtstrekningGeometri,
    stedfestingUtstrekning: StedfestingUtstrekning,
): UtstrekningGeometri? {
    val veglenkeUtstrekning = utstrekningGeometri.utstrekning
    val veglenkeGeometri = utstrekningGeometri.geometri

    if (!veglenkeUtstrekning.overlaps(stedfestingUtstrekning)) {
        return null
    }
    if (veglenkeUtstrekning.startposisjon == stedfestingUtstrekning.startposisjon &&
        veglenkeUtstrekning.sluttposisjon == stedfestingUtstrekning.sluttposisjon
    ) {
        return utstrekningGeometri
    }
    val line = LengthIndexedLine(veglenkeGeometri)
    val intersection =
        veglenkeUtstrekning.intersect(stedfestingUtstrekning)
            ?: return null

    // Calculate the relative positions within the veglenke geometry
    val totalLength = veglenkeUtstrekning.relativeLength
    val startOffset = intersection.startposisjon - veglenkeUtstrekning.startposisjon
    val endOffset = intersection.sluttposisjon - veglenkeUtstrekning.startposisjon

    // Convert relative positions to actual distances along the geometry
    val geometryLength = line.endIndex
    val startDistance = (startOffset / totalLength) * geometryLength
    val endDistance = (endOffset / totalLength) * geometryLength

    // Extract the geometry segment between the calculated distances
    val intersectingGeometry = line.extractLine(startDistance, endDistance).also { it.srid = veglenkeGeometri.srid }

    return UtstrekningGeometri(intersection, intersectingGeometry)
}

fun calculateIntersectingGeometry(
    veglenkeGeometri: Geometry,
    veglenkeUtstrekning: StedfestingUtstrekning,
    stedfestingUtstrekning: StedfestingUtstrekning,
): Geometry? =
    calculateIntersectingGeometry(
        UtstrekningGeometri(veglenkeUtstrekning, veglenkeGeometri),
        stedfestingUtstrekning,
    )?.geometri

fun mergeGeometries(geometries: List<Geometry>): Geometry? {
    if (geometries.isEmpty()) {
        return null
    }
    val srid = geometries.map { it.srid }.toSet().singleOrNull()
    require(srid != null) {
        "All geometries must have the same SRID to be merged"
    }
    require(srid != 0) {
        "Geometries must have a valid SRID to be merged"
    }

    if (geometries.all { it.length == 0.0 } || geometries.distinct().size == 1) {
        return geometries.first().let {
            if (it is LineString && it.length == 0.0) {
                it.startPoint
            } else {
                it
            }
        }
    }

    val lineMerger = LineMerger()
    lineMerger.add(geometries)

    val merged: Geometry? =
        lineMerger.mergedLineStrings.filterIsInstance<LineString>().let { lineStrings ->
            when (lineStrings.size) {
                0 -> null
                1 -> lineStrings.first()
                else ->
                    org.locationtech.jts.geom.MultiLineString(
                        lineStrings.toTypedArray(),
                        lineStrings.first().factory,
                    )
            }
        }

    check(merged?.srid != 0) {
        "Merged geometry must have a valid SRID"
    }

    return merged
}
