package no.vegvesen.nvdb.tnits.generator.core.extensions

import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.WGS84
import no.vegvesen.nvdb.tnits.generator.core.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.generator.core.model.intersect
import no.vegvesen.nvdb.tnits.generator.core.model.overlaps
import org.geotools.api.referencing.FactoryException
import org.geotools.api.referencing.NoSuchAuthorityCodeException
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.*
import org.locationtech.jts.io.WKTReader
import org.locationtech.jts.linearref.LengthIndexedLine
import org.locationtech.jts.operation.linemerge.LineMerger
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.simplify.TopologyPreservingSimplifier
import javax.xml.crypto.dsig.TransformException

internal object GeoToolsInit {
    init {
        // Set system properties BEFORE any GeoTools initialization
        System.setProperty("org.geotools.referencing.forceXY", "true")
        System.setProperty("org.geotools.referencing.lon.lat.order", "true")

        // Clear any existing hints to force re-initialization
        org.geotools.util.factory.Hints.scanSystemProperties()

        org.geotools.util.factory.Hints.putSystemDefault(
            org.geotools.util.factory.Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER,
            true,
        )
        org.geotools.util.factory.Hints.putSystemDefault(
            org.geotools.util.factory.Hints.FORCE_AXIS_ORDER_HONORING,
            "http",
        )

        // Force factory reset by clearing the cache
        try {
            org.geotools.referencing.CRS.reset("all")
        } catch (e: Exception) {
            // Reset might fail if factory hasn't been initialized yet, which is fine
        }
    }
}

// Force initialization by accessing the object
private val geoToolsInitialized = GeoToolsInit

object SRID {
    const val UTM33 = 25833
    const val WGS84 = 4326
    const val EPSG5973 = 5973
}

val geometryFactories =
    mapOf(
        SRID.UTM33 to GeometryFactory(PrecisionModel(10.0), SRID.UTM33),
        WGS84 to GeometryFactory(PrecisionModel(100_000.0), WGS84),
    )

val wktReaders =
    mapOf(
        SRID.UTM33 to WKTReader(geometryFactories[SRID.UTM33]),
        WGS84 to WKTReader(geometryFactories[WGS84]),
        SRID.EPSG5973 to WKTReader(geometryFactories[SRID.UTM33]),
    )

fun parseWkt(wkt: String, srid: Int): Geometry = wktReaders[srid]?.read(wkt) ?: error("Unsupported SRID: $srid")

// OpenLR library expects coordinates in longitude/latitude order for WGS84.
// Cache CRS instances to avoid repeated lookups
private val crsCache = mutableMapOf<Int, CoordinateReferenceSystem>()

fun getCrs(srid: Int): CoordinateReferenceSystem = crsCache.getOrPut(srid) {
    if (srid == WGS84) {
        // Force longitude-first by using the OGC WKT variant which has X,Y order
        // instead of the official EPSG definition which has Y,X order
        try {
            // Try using the OGC definition first
            CRS.decode("OGC:CRS84") // CRS84 is WGS84 with longitude-first
        } catch (e: Exception) {
            // Fallback to forced decode
            CRS.decode("EPSG:$srid", true)
        }
    } else {
        CRS.decode("EPSG:$srid")
    }
}

fun Geometry.projectTo(srid: Int): Geometry = if (this.srid == srid) {
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

fun Geometry.simplify(distanceTolerance: Double): Geometry = DouglasPeuckerSimplifier
    .simplify(this, distanceTolerance)
    .let {
        if (it.isEmpty) {
            TopologyPreservingSimplifier.simplify(this, distanceTolerance)
        } else {
            it
        }
    }.also { it.srid = this.srid }

data class UtstrekningGeometri(val utstrekning: StedfestingUtstrekning, val geometri: Geometry)

fun calculateIntersectingGeometry(utstrekningGeometri: UtstrekningGeometri, stedfestingUtstrekning: StedfestingUtstrekning): UtstrekningGeometri? {
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
): Geometry? = calculateIntersectingGeometry(
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
                    MultiLineString(
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
