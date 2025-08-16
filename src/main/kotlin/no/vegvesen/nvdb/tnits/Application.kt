package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.tnits.config.configureDatabase
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.vegnett.backfillVeglenker
import no.vegvesen.nvdb.tnits.vegnett.updateVeglenker
import kotlin.time.Instant

suspend fun main() {
    println("Starter NVDB TN-ITS konsollapplikasjon...")
    val config = loadConfig()
    configureDatabase(config)
    println("Applikasjon startet, databasen er konfigurert!")

    val veglenkerBackfillCompleted = KeyValue.get<Instant>("veglenker_backfill_completed")

    if (veglenkerBackfillCompleted == null) {
        backfillVeglenker()
    }

    updateVeglenker()

    val vegobjektTyper = listOf(105, 821)

    // TODO: Implement vegobjekter backfill and update logic
}
