package no.vegvesen.nvdb.tnits.generator.core.services.nvdb

import jakarta.inject.Singleton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import no.vegvesen.nvdb.tnits.common.model.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.common.model.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.infrastructure.VegnettLoader
import no.vegvesen.nvdb.tnits.generator.infrastructure.VegobjektLoader
import no.vegvesen.nvdb.tnits.generator.log

/**
 * Orkestrerer backfilling av veglenkesekvenser og vegobjekter fra NVDB (Uberiket API).
 * Dette gjøres vanligvis én gang, når systemet settes opp for første gang.
 */
@Singleton
class NvdbBackfillOrchestrator(
    private val vegnettLoader: VegnettLoader,
    private val vegobjektLoader: VegobjektLoader,
) {
    suspend fun performBackfill() {
        coroutineScope {
            launch {
                log.info("Oppdaterer veglenkesekvenser...")
                vegnettLoader.backfillVeglenkesekvenser()
            }

            mainVegobjektTyper.forEach { typeId ->
                launch {
                    log.info("Oppdaterer vegobjekter for hovedtype $typeId...")
                    vegobjektLoader.backfillVegobjekter(typeId, true)
                }
            }
            supportingVegobjektTyper.forEach { typeId ->
                launch {
                    log.info("Oppdaterer vegobjekter for støttende type $typeId...")
                    vegobjektLoader.backfillVegobjekter(typeId, false)
                }
            }
        }
    }
}
