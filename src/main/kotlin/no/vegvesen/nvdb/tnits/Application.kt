package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.extensions.get
import no.vegvesen.nvdb.tnits.extensions.put
import kotlin.time.Clock
import kotlin.time.Instant

suspend fun main() {
    println("Starter NVDB TN-ITS konsollapplikasjon...")

    with(Services()) {
        val vegobjektTyper = listOf(105, 821, 616)

        coroutineScope {
            launch {
                println("Oppdaterer veglenkesekvenser...")
                veglenkesekvenserService.backfillVeglenkesekvenser()
                veglenkesekvenserService.updateVeglenkesekvenser()
            }

            vegobjektTyper.forEach { typeId ->
                launch {
                    println("Oppdaterer vegobjekter for type $typeId...")
                    vegobjekterService.backfillVegobjekter(typeId)
                    vegobjekterService.updateVegobjekter(typeId)
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
                    speedLimitGenerator.generateSpeedLimitsFullSnapshot(now)
                    keyValueStore.put("last_speedlimit_snapshot", now)
                    println("Fullt snapshot generert.")
                }

                "2" -> {
                    println("Genererer delta snapshot av TN-ITS fartsgrenser...")
                    val since =
                        keyValueStore.get<Instant>("last_speedlimit_snapshot")
                            ?: keyValueStore.get<Instant>("last_speedlimit_update")
                            ?: error("Ingen tidligere snapshot eller oppdateringstidspunkt funnet for fartsgrenser")
                    speedLimitGenerator.generateSpeedLimitsDeltaUpdate(now, since)
                    keyValueStore.put("last_speedlimit_update", now)
                }

                "3" -> {
                    println("Avslutter applikasjonen...")
                    return
                }

                else -> println("Ugyldig valg, vennligst prøv igjen.")
            }
        } while (true)
    }
}

private suspend fun Services.performFinalSyncCheckAndGetTimestamp(vegobjektTyper: List<Int>): Instant {
    var now: Instant
    coroutineScope {
        do {
            now = Clock.System.now()
            val veglenkesekvensHendelseCount =
                async {
                    veglenkesekvenserService.updateVeglenkesekvenser()
                }
            val vegobjekterHendelseCounts =
                vegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId)
                    }
                }

            val total = veglenkesekvensHendelseCount.await() + vegobjekterHendelseCounts.sumOf { it.await() }
        } while (total > 0)
    }
    return now
}
