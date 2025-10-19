package no.vegvesen.nvdb.tnits.generator

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.UTM33
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.WGS84
import no.vegvesen.nvdb.tnits.generator.core.extensions.getCrs
import no.vegvesen.nvdb.tnits.generator.core.extensions.parseWkt
import no.vegvesen.nvdb.tnits.generator.core.extensions.projectTo
import no.vegvesen.nvdb.tnits.generator.core.useCases.InspireRoadnetCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsAutomaticCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsSnapshotCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsUpdateCycle
import org.geotools.util.factory.Hints
import org.koin.core.annotation.KoinApplication
import org.koin.ksp.generated.startKoin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

val log: Logger = LoggerFactory.getLogger("no.vegvesen.nvdb.tnits.generator.Application")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")

    // GeoTools properties are primarily set in GeometryHelpers static initializer
    // to ensure they're applied before any CRS objects are created.
    // Setting them here again as a safety measure.
    System.setProperty("org.geotools.referencing.forceXY", "true")
    Hints.putSystemDefault(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true)

    verifyWgs84Mapping()

    val app = ProductionApp.startKoin()
    try {
        when (args.firstOrNull()) {
            "snapshot" -> app.koin.get<TnitsSnapshotCycle>().execute()
            "update" -> app.koin.get<TnitsUpdateCycle>().execute()
            "inspire-roadnet" -> app.koin.get<InspireRoadnetCycle>().execute()
            "auto", null -> app.koin.get<TnitsAutomaticCycle>().execute()
            else -> {
                log.error("Unknown command '${args.first()}', use one of 'snapshot', 'update', 'inspire-roadnet' or 'auto'")
            }
        }
        log.info("NVDB TN-ITS application finished")
    } finally {
        // To keep the underlying OkHttp client from hanging for a minute
        app.koin.get<MinioClient>().close()
    }
}

fun verifyWgs84Mapping() {
    log.info("Verifying WGS84 coordinate transformation...")

    val forceXY = System.getProperty("org.geotools.referencing.forceXY")
    val lonLatOrder = System.getProperty("org.geotools.referencing.lon.lat.order")
    log.info("GeoTools properties - forceXY: $forceXY, lon.lat.order: $lonLatOrder")

    val wgs84Crs = getCrs(WGS84)
    log.info("WGS84 CRS identifier: ${wgs84Crs.name}")

    val axis0 = wgs84Crs.coordinateSystem.getAxis(0)
    val axis1 = wgs84Crs.coordinateSystem.getAxis(1)

    log.info("WGS 84 CRS axis order: [0]=${axis0.name.code} (${axis0.direction}), [1]=${axis1.name.code} (${axis1.direction})")

    val isLatLonOrder = axis0.direction.name().contains("NORTH") || axis0.direction.name().contains("SOUTH")
    if (isLatLonOrder) {
        log.info("⚠ WGS84 CRS has latitude-first order - will swap coordinates after transformation")
    } else {
        log.info("✓ WGS84 CRS has longitude-first order - no coordinate swapping needed")
    }

    val utm33Point = parseWkt("POINT (246926 6995436)", UTM33)
    val wgs84point = utm33Point.projectTo(WGS84)
    val coordinate = wgs84point.coordinate
    val x = coordinate.x.roundToInt()
    val y = coordinate.y.roundToInt()

    log.info("Transformation test: UTM33 (246926, 6995436) -> WGS84 ($x, $y)")

    check(x == 10 && y == 63) {
        "WGS84 mapping is broken! Got ($x, $y) but expected (10, 63). " +
            "Axis order is: ${axis0.name.code}/${axis1.name.code}. " +
            "CRS: ${wgs84Crs.name}."
    }

    log.info("✓ WGS84 coordinate transformation verified successfully (longitude-first axis order)")
}

@KoinApplication
object ProductionApp
