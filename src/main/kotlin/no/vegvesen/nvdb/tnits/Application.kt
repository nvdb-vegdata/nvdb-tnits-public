package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.config.configureDatabase
import no.vegvesen.nvdb.tnits.config.loadConfig
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.extensions.clearVeglenkesekvensSettings
import no.vegvesen.nvdb.tnits.vegnett.backfillVeglenkesekvenser
import no.vegvesen.nvdb.tnits.vegnett.updateVeglenkesekvenser
import no.vegvesen.nvdb.tnits.vegobjekter.backfillVegobjekter
import no.vegvesen.nvdb.tnits.vegobjekter.updateVegobjekter

suspend fun main() {
    println("Starter NVDB TN-ITS konsollapplikasjon...")
    val config = loadConfig()
    configureDatabase(config)
    println("Applikasjon startet, databasen er konfigurert!")

    // Check if RocksDB exists and has data
    if (!rocksDbConfiguration.existsAndHasData()) {
        println("RocksDB-database eksisterer ikke eller er tom. Nullstiller veglenkesekvenser-innstillinger...")
        KeyValue.clearVeglenkesekvensSettings()
        println("Veglenkesekvenser-innstillinger nullstilt. Backfill vil starte på nytt.")
    }

    val vegobjektTyper = listOf(105, 821)

    coroutineScope {
        launch {
            println("Oppdaterer veglenkesekvenser...")
            backfillVeglenkesekvenser()
            updateVeglenkesekvenser()
        }

        vegobjektTyper.forEach { typeId ->
            launch {
                println("Oppdaterer vegobjekter for type $typeId...")
                backfillVegobjekter(typeId)
                updateVegobjekter(typeId)
            }
        }
    }

    println("Oppdateringer fullført!")

    measure("Bygger vegnett-cache", true) {
        cachedVegnett.initialize()
    }

    do {
        println("Trykk:")
        println(" 1 for å generere fullt snapshot av TN-ITS fartsgrenser")
        println(" 2 for å generere delta snapshot")
        println(" 3 for å avslutte")

        val input: String = readln().trim()

        when (input) {
            "1" -> {
                println("Genererer fullt snapshot av TN-ITS fartsgrenser...")
                generateSpeedLimitsFullSnapshot()
                println("Fullt snapshot generert.")
            }

            "2" -> {
                println("Genererer delta snapshot av TN-ITS fartsgrenser...")
                generateSpeedLimitsDeltaUpdate()
            }

            "3" -> {
                println("Avslutter applikasjonen...")
                return
            }

            else -> println("Ugyldig valg, vennligst prøv igjen.")
        }
    } while (true)
}
