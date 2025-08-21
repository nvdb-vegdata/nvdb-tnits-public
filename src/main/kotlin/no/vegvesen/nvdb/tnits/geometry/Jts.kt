package no.vegvesen.nvdb.tnits.geometry

import org.geotools.api.referencing.FactoryException
import org.geotools.api.referencing.NoSuchAuthorityCodeException
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.geotools.geometry.jts.JTS
import org.geotools.referencing.CRS
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.io.WKTReader
import javax.xml.crypto.dsig.TransformException


object SRID {
    const val UTM33 = 25833
    const val WGS84 = 4326
}

val geometryFactories =
    mapOf(
        SRID.UTM33 to GeometryFactory(PrecisionModel(1.0), SRID.UTM33),
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
