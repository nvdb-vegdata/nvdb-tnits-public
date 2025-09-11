package no.vegvesen.nvdb.tnits.model

import kotlinx.serialization.Serializable
import no.vegvesen.nvdb.apiles.uberiket.VeglenkeMedId

@Serializable
data class VeglenkeId(val veglenkesekvensId: Long, val veglenkenummer: Int) {
    override fun toString(): String = "$veglenkesekvensId-$veglenkenummer"

    companion object {
        fun fromString(id: String): VeglenkeId {
            val parts = id.split("-")
            if (parts.size != 2) throw IllegalArgumentException("Invalid VeglenkeId format: $id")
            return VeglenkeId(parts[0].toLong(), parts[1].toInt())
        }
    }
}

val VeglenkeMedId.veglenkeId: VeglenkeId
    get() = VeglenkeId(veglenkesekvensId, veglenkenummer)
