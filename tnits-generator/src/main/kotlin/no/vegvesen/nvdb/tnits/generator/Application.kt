package no.vegvesen.nvdb.tnits.generator

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.core.useCases.PerformInspireRoadnetExport
import no.vegvesen.nvdb.tnits.generator.core.useCases.PerformSmartTnitsExport
import no.vegvesen.nvdb.tnits.generator.core.useCases.PerformTnitsSnapshotExport
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsUpdateCycle
import org.koin.core.annotation.KoinApplication
import org.koin.ksp.generated.startKoin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.vegvesen.nvdb.tnits.generator.Application")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")
    val app = ProductionApp.startKoin()
    when (args.firstOrNull()) {
        "snapshot" -> app.koin.get<PerformTnitsSnapshotExport>().execute()
        "update" -> app.koin.get<TnitsUpdateCycle>().execute()
        "inspire-roadnet" -> app.koin.get<PerformInspireRoadnetExport>().execute()
        "auto", null -> app.koin.get<PerformSmartTnitsExport>().execute()
        else -> {
            log.error("Unknown command '${args.first()}', use one of 'snapshot', 'update', 'inspire-roadnet' or 'auto'")
        }
    }
    log.info("NVDB TN-ITS application finished")
    // To keep the underlying OkHttp client from hanging for a minute
    app.koin.get<MinioClient>().close()
}

@KoinApplication
object ProductionApp
