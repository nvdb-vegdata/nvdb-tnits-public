package no.vegvesen.nvdb.tnits.generator.core.services.storage

import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType

@Serializable
data class VegobjektChange(val id: Long, val changeType: ChangeType)
