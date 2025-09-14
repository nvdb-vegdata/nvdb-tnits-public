package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import kotlin.time.Clock
import kotlin.time.Instant

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(VegobjektTyper.FELTSTREKNING, VegobjektTyper.FUNKSJONELL_VEGKLASSE)

suspend fun main() {
    println("Starter NVDB TN-ITS konsollapplikasjon...")

    with(Services()) {
        coroutineScope {
            launch {
                println("Oppdaterer veglenkesekvenser...")
                veglenkesekvenserService.backfillVeglenkesekvenser()
                veglenkesekvenserService.updateVeglenkesekvenser()
            }

            mainVegobjektTyper.forEach { typeId ->
                launch {
                    println("Oppdaterer vegobjekter for type $typeId...")
                    vegobjekterService.backfillVegobjekter(typeId, true)
                    vegobjekterService.updateVegobjekter(typeId, true)
                }
            }
            supportingVegobjektTyper.forEach { typeId ->
                launch {
                    println("Oppdaterer vegobjekter for type $typeId...")
                    vegobjekterService.backfillVegobjekter(typeId, false)
                    vegobjekterService.updateVegobjekter(typeId, false)
                }
            }
        }

        // Final update check to avoid missing changes during backfill and initial update
        val now: Instant = performFinalSyncCheckAndGetTimestamp()

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

private suspend fun Services.performFinalSyncCheckAndGetTimestamp(): Instant {
    var now: Instant
    coroutineScope {
        do {
            now = Clock.System.now()
            val veglenkesekvensHendelseCount =
                async {
                    veglenkesekvenserService.updateVeglenkesekvenser()
                }
            val vegobjekterHendelseCounts =
                mainVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, true)
                    }
                }
            val vegobjekterSupportingHendelseCounts =
                supportingVegobjektTyper.map { typeId ->
                    async {
                        vegobjekterService.updateVegobjekter(typeId, false)
                    }
                }

            val total =
                veglenkesekvensHendelseCount.await() + vegobjekterHendelseCounts.sumOf { it.await() } + vegobjekterSupportingHendelseCounts.sumOf { it.await() }
        } while (total > 0)
    }
    return now
}
