package no.vegvesen.nvdb.tnits.generator.core.model

data class VeglenkesekvensHendelse(
    val hendelseId: Long,
    val veglenkesekvensId: Long,
)

data class VegobjektHendelse(
    val hendelseId: Long,
    val vegobjektId: Long,
    val vegobjektVersjon: Int,
)
