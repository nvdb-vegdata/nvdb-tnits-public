package no.vegvesen.nvdb.tnits.common.model

import kotlinx.serialization.Serializable

@Serializable
data class GeneratorFlags(
    val recalculateAll: Boolean = false,
)
