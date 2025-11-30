package no.vegvesen.nvdb.tnits.generator

import io.minio.MinioClient
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsAutomaticCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsSnapshotCycle
import no.vegvesen.nvdb.tnits.generator.core.useCases.TnitsUpdateCycle
import org.koin.core.annotation.KoinApplication
import org.koin.ksp.generated.startKoin
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.vegvesen.nvdb.tnits.generator.Application")

suspend fun main(args: Array<String>) {
    log.info("Starting NVDB TN-ITS application on process ${ProcessHandle.current().pid()}")

    val app = ProductionApp.startKoin()
    try {
        when (args.firstOrNull()) {
            "snapshot" -> app.koin.get<TnitsSnapshotCycle>().execute()
            "update" -> app.koin.get<TnitsUpdateCycle>().execute()
            "auto", null -> app.koin.get<TnitsAutomaticCycle>().execute()
            else -> {
                log.error("Unknown command '${args.first()}', use one of 'snapshot', 'update', or 'auto'")
            }
        }
        log.info("NVDB TN-ITS application finished")
    } finally {
        // To keep the underlying OkHttp client from hanging for a minute
        app.koin.get<MinioClient>().close()
    }
}

@KoinApplication
object ProductionApp
