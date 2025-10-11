package no.vegvesen.nvdb.tnits.generator.core.services.nvdb

import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    suspend fun performBackfill(): Int = coroutineScope {
        val veglenkesekvensBackfill = async {
            log.info("Oppdaterer veglenkesekvenser...")
            vegnettLoader.backfillVeglenkesekvenser()
        }

        val mainVegobjektBackfill = mainVegobjektTyper.map { typeId ->
            async {
                log.info("Oppdaterer vegobjekter for hovedtype $typeId...")
                vegobjektLoader.backfillVegobjekter(typeId, true)
            }
        }
        val supportingVegobjektBackfill = supportingVegobjektTyper.map { typeId ->
            async {
                log.info("Oppdaterer vegobjekter for støttende type $typeId...")
                vegobjektLoader.backfillVegobjekter(typeId, false)
            }
        }

        val jobs = listOf(veglenkesekvensBackfill) + mainVegobjektBackfill + supportingVegobjektBackfill
        val counts = jobs.awaitAll()
        counts.sum()
    }
}
