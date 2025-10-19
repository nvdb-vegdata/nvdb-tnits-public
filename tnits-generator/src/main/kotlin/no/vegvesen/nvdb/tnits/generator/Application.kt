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
    val coordinate = parseWkt("POINT (246926 6995436)", UTM33).projectTo(WGS84).coordinate
    val x = coordinate.x.roundToInt()
    val y = coordinate.y.roundToInt()
    check(x == 10 && y == 63) {
        "WGS84 mapping is broken, got ${coordinate.x}, ${coordinate.y}"
    }
}

@KoinApplication
object ProductionApp
