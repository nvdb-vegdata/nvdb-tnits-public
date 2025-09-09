package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
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
import kotlin.time.Clock
import kotlin.time.Instant

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

    val vegobjektTyper = listOf(105, 821, 616)

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

    // Final update check to avoid missing changes during backfill and initial update
    val now: Instant = performFinalSyncCheckAndGetTimestamp(vegobjektTyper)

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
                generateSpeedLimitsFullSnapshot(now)
                println("Fullt snapshot generert.")
            }

            "2" -> {
                println("Genererer delta snapshot av TN-ITS fartsgrenser...")
                generateSpeedLimitsDeltaUpdate(now)
            }

            "3" -> {
                println("Avslutter applikasjonen...")
                return
            }

            else -> println("Ugyldig valg, vennligst prøv igjen.")
        }
    } while (true)
}

private suspend fun performFinalSyncCheckAndGetTimestamp(vegobjektTyper: List<Int>): Instant {
    var now: Instant
    coroutineScope {
        do {
            now = Clock.System.now()
            val veglenkesekvensHendelseCount =
                async {
                    updateVeglenkesekvenser()
                }
            val vegobjekterHendelseCounts =
                vegobjektTyper.map { typeId ->
                    async {
                        updateVegobjekter(typeId)
                    }
                }

            val total = veglenkesekvensHendelseCount.await() + vegobjekterHendelseCounts.sumOf { it.await() }
        } while (total > 0)
    }
    return now
}
