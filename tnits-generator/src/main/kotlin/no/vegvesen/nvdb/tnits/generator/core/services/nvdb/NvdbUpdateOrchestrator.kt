package no.vegvesen.nvdb.tnits.generator.core.services.nvdb

import jakarta.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.vegvesen.nvdb.tnits.common.model.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.common.model.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.generator.infrastructure.VegnettLoader
import no.vegvesen.nvdb.tnits.generator.infrastructure.VegobjektLoader

/**
 * Utfører inkrementelle oppdateringer av veglenkesekvenser og vegobjekter fra NVDB (Uberiket API), basert på hendelser.
 */
@Singleton
class NvdbUpdateOrchestrator(
    private val vegnettLoader: VegnettLoader,
    private val vegobjektLoader: VegobjektLoader,
) {
    suspend fun performUpdate(): Int = coroutineScope {
        var totalCount = 0
        do {
            val veglenkesekvensHendelseCount =
                async {
                    vegnettLoader.updateVeglenkesekvenser()
                }
            val changedMainVegobjekterCounts =
                mainVegobjektTyper.map { typeId ->
                    async {
                        vegobjektLoader.updateVegobjekter(typeId, true)
                    }
                }
            val changedSupportingVegobjekterCounts =
                supportingVegobjektTyper.map { typeId ->
                    async {
                        vegobjektLoader.updateVegobjekter(typeId, false)
                    }
                }

            val total =
                veglenkesekvensHendelseCount.await() + changedMainVegobjekterCounts.sumOf { it.await() } +
                    changedSupportingVegobjekterCounts.sumOf { it.await() }
            totalCount += total
        } while (total > 0)
        totalCount
    }
}
