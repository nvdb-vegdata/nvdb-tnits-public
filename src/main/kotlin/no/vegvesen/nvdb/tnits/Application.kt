package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.config.configureDatabase
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.vegnett.backfillVeglenker
import no.vegvesen.nvdb.tnits.vegnett.updateVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.backfillVegobjekter
import no.vegvesen.nvdb.tnits.vegobjekter.updateVegobjekter
import kotlin.time.Instant

suspend fun main() {
    println("Starter NVDB TN-ITS konsollapplikasjon...")
    val config = loadConfig()
    configureDatabase(config)
    println("Applikasjon startet, databasen er konfigurert!")

    val vegobjektTyper = listOf(105, 821)

    coroutineScope {
        launch {
            println("Oppdaterer veglenker...")
            val veglenkerBackfillCompleted = KeyValue.get<Instant>("veglenker_backfill_completed")
            if (veglenkerBackfillCompleted == null) {
                backfillVeglenker()
            }
            updateVeglenker()
        }

        vegobjektTyper.forEach { typeId ->
            println("Oppdaterer vegobjekter for type $typeId...")
            launch {
                val backfillCompleted = KeyValue.get<Instant>("vegobjekter_${typeId}_backfill_completed")
                if (backfillCompleted == null) {
                    backfillVegobjekter(typeId)
                }
                updateVegobjekter(typeId)
            }
        }
    }

    println("Oppdateringer fullført!")

    println("Trykk:")
    println(" 1 for å generere fullt snapshot av TN-ITS fartsgrenser")
    println(" 2 for å generere delta snapshot")
    println(" 3 for å avslutte")

    var input: String
    do {
        input = readln().trim()
        when (input) {
            "1" -> {
                println("Genererer fullt snapshot av TN-ITS fartsgrenser...")
                generateSpeedLimitsFullSnapshot()
            }

            "2" -> {
                println("Genererer delta snapshot av TN-ITS fartsgrenser...")
                TODO()
            }

            "3" -> {
                println("Avslutter applikasjonen...")
                return
            }

            else -> println("Ugyldig valg, vennligst prøv igjen.")
        }
    } while (true)
}
