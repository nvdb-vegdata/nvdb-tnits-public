package no.vegvesen.nvdb.tnits

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.utilities.measure
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.time.Instant

val mainVegobjektTyper = listOf(VegobjektTyper.FARTSGRENSE)

val supportingVegobjektTyper = setOf(VegobjektTyper.FELTSTREKNING, VegobjektTyper.FUNKSJONELL_VEGKLASSE)

val log: Logger = LoggerFactory.getLogger("tnits")

suspend fun main() {
    log.info("Starter NVDB TN-ITS konsollapplikasjon...")

    with(Services()) {
        performBackfill()

        val now: Instant = performUpdateAndGetTimestamp()

        log.info("Oppdateringer fullført!")

        log.measure("Bygger vegnett-cache", true) {
            cachedVegnett.initialize()
        }

        handleInput(now)
    }
}

private suspend fun Services.handleInput(now: Instant) {
    do {
        println("Trykk:")
        println(" 1 for å generere fullt snapshot av TN-ITS fartsgrenser")
        println(" 2 for å generere delta snapshot")
        println(" 3 for å avslutte")

        val input: String = readln().trim()

        when (input) {
            "1" -> {
                log.info("Genererer fullt snapshot av TN-ITS fartsgrenser...")
                speedLimitExporter.exportSpeedLimitsFullSnapshot(now)
                keyValueStore.put("last_speedlimit_snapshot", now)
                log.info("Fullt snapshot generert.")
            }

            "2" -> {
                log.info("Genererer delta snapshot av TN-ITS fartsgrenser...")
                val since =
                    keyValueStore.get<Instant>("last_speedlimit_snapshot")
                        ?: keyValueStore.get<Instant>("last_speedlimit_update")
                        ?: error("Ingen tidligere snapshot eller oppdateringstidspunkt funnet for fartsgrenser")
                speedLimitExporter.generateSpeedLimitsDeltaUpdate(now, since)
                keyValueStore.put("last_speedlimit_update", now)
            }

            "3" -> {
                log.info("Avslutter applikasjonen...")
                return
            }

            else -> log.info("Ugyldig valg, vennligst prøv igjen.")
        }
    } while (true)
}

private suspend fun Services.performBackfill() {
    coroutineScope {
        launch {
            log.info("Oppdaterer veglenkesekvenser...")
            veglenkesekvenserService.backfillVeglenkesekvenser()
        }

        mainVegobjektTyper.forEach { typeId ->
            launch {
                log.info("Oppdaterer vegobjekter for hovedtype $typeId...")
                vegobjekterService.backfillVegobjekter(typeId, true)
            }
        }
        supportingVegobjektTyper.forEach { typeId ->
            launch {
                log.info("Oppdaterer vegobjekter for støttende type $typeId...")
                vegobjekterService.backfillVegobjekter(typeId, false)
            }
        }
    }
}

private suspend fun Services.performUpdateAndGetTimestamp(): Instant {
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
