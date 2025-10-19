package no.vegvesen.nvdb.tnits.generator

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.UTM33
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID.WGS84
import no.vegvesen.nvdb.tnits.generator.core.extensions.parseWkt
import no.vegvesen.nvdb.tnits.generator.core.extensions.projectTo
import no.vegvesen.nvdb.tnits.generator.core.useCases.InspireRoadnetCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsAutomaticCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsSnapshotCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsUpdateCycle
import org.koin.core.annotation.KoinApplication
import org.koin.ksp.generated.startKoin
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

val log: Logger = LoggerFactory.getLogger("no.vegvesen.nvdb.tnits.generator.Application")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")

    System.setProperty("org.geotools.referencing.forceXY", "true")

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
    val utm33Point = parseWkt("POINT (246926 6995436)", UTM33)
    val wgs84point = utm33Point.projectTo(WGS84)
    val coordinate = wgs84point.coordinate
    val x = coordinate.x.roundToInt()
    val y = coordinate.y.roundToInt()
    check(x == 10 && y == 63) {
        "WGS84 mapping is broken, got ($x, $y) expected (10, 63). ${utm33Point.coordinate.x} should be 246926 and ${utm33Point.coordinate.y} should be 6995436"
    }
}

@KoinApplication
object ProductionApp
