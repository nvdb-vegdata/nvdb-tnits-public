package no.vegvesen.nvdb.tnits.generator.core.model

data class VegobjektUpdate(val id: Long, val changeType: ChangeType, val vegobjekt: Vegobjekt? = null)
