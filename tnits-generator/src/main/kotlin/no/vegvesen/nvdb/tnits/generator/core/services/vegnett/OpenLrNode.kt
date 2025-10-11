package no.vegvesen.nvdb.tnits.generator.core.services.vegnett

import org.locationtech.jts.geom.Point
import org.openlr.map.Node

data class OpenLrNode(val id: Long, val valid: Boolean, val point: Point) : Node {
    override fun isValid(): Boolean = valid

    override fun getGeometry(): Point = point

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpenLrNode) return false

        if (id == other.id) return true

        return false
    }

    override fun hashCode(): Int = id.hashCode()
}
