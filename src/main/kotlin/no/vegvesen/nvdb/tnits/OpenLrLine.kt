package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import org.openlr.map.Line
import org.openlr.map.Node

data class OpenLrLine(
    val id: VeglenkeId,
    val startnode: OpenLrNode,
    val sluttnode: OpenLrNode,
    val geometri: LineString,
    val frc: FunctionalRoadClass,
    val fow: FormOfWay,
) : Line<OpenLrLine> {
    override fun getId(): String = id.toString()

    override fun getStartNode(): Node = startnode

    override fun getEndNode(): Node = sluttnode

    override fun getIncomingLines(): List<OpenLrLine> {
        TODO("Not yet implemented")
    }

    override fun getOutgoingLines(): List<OpenLrLine> {
        TODO("Not yet implemented")
    }

    override fun getFunctionalRoadClass(): FunctionalRoadClass = frc

    override fun getFormOfWay(): FormOfWay = fow

    override fun getGeometry(): LineString = geometri

    override fun getLength(): Double = geometri.length

    companion object {
        fun fromVeglenke(
            veglenke: Veglenke,
            frc: FunctionalRoadClass,
            fow: FormOfWay,
        ): OpenLrLine {
            val geometry = veglenke.geometri

            if (geometry !is LineString) {
                error("Expected LineString geometry, but got ${geometry.geometryType} for VeglenkeId: ${veglenke.veglenkeId}")
            }

            val startNode =
                OpenLrNode(
                    valid = true,
                    point = geometry.startPoint,
                )
            val endNode =
                OpenLrNode(
                    valid = true,
                    point = geometry.endPoint,
                )
            return OpenLrLine(
                id = veglenke.veglenkeId,
                startnode = startNode,
                sluttnode = endNode,
                geometri = geometry,
                frc = frc,
                fow = fow,
            )
        }
    }
}

data class OpenLrNode(
    val valid: Boolean,
    val point: Point,
) : Node {
    override fun isValid(): Boolean = valid

    override fun getGeometry(): Point = point
}
