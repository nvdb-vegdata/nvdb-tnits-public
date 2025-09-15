package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.tnits.model.VegobjektTyper

fun main() {
    with(Services()) {
        val fartsgrenser = measure("Fartsgrenser full scan") {
            vegobjekterRepository.getAll(VegobjektTyper.FARTSGRENSE)
        }

        val fartsgrenser2 = measure("Fartsgrenser full scan 2") {
            vegobjekterRepository.streamAll(VegobjektTyper.FARTSGRENSE).associateBy { it.id }
        }

        val lookup = measure("Feltstrekning lookup") {
            vegobjekterRepository.getVegobjektStedfestingLookup(VegobjektTyper.FELTSTREKNING)
        }
    }
}
