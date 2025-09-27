package no.vegvesen.nvdb.tnits.handlers

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.vegvesen.nvdb.tnits.mainVegobjektTyper
import no.vegvesen.nvdb.tnits.supportingVegobjektTyper
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService
import no.vegvesen.nvdb.tnits.vegobjekter.VegobjekterService

class PerformUpdateHandler(
    private val veglenkesekvenserService: VeglenkesekvenserService,
    private val vegobjekterService: VegobjekterService,
) {
    suspend fun performUpdate() {
        coroutineScope {
            do {
                val veglenkesekvensHendelseCount =
                    async {
                        veglenkesekvenserService.updateVeglenkesekvenser()
                    }
                val changedMainVegobjekterCounts =
                    mainVegobjektTyper.map { typeId ->
                        async {
                            vegobjekterService.updateVegobjekter(typeId, true)
                        }
                    }
                val changedSupportingVegobjekterCounts =
                    supportingVegobjektTyper.map { typeId ->
                        async {
                            vegobjekterService.updateVegobjekter(typeId, false)
                        }
                    }

                val total =
                    veglenkesekvensHendelseCount.await() + changedMainVegobjekterCounts.sumOf { it.await() } +
                        changedSupportingVegobjekterCounts.sumOf { it.await() }
            } while (total > 0)
        }
    }
}
