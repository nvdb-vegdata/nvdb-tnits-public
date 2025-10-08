package no.vegvesen.nvdb.tnits.generator.handlers

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.generator.log
import no.vegvesen.nvdb.tnits.generator.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.generator.vegobjekter.VegobjekterService

class PerformBackfillHandler(
    private val veglenkesekvenserService: VeglenkesekvenserService,
    private val vegobjekterService: VegobjekterService,
) {
    suspend fun performBackfill() {
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
}
