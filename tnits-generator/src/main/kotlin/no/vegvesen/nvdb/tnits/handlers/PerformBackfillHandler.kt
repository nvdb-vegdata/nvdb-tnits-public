package no.vegvesen.nvdb.tnits.handlers

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.log
import no.vegvesen.nvdb.tnits.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjekterService

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
