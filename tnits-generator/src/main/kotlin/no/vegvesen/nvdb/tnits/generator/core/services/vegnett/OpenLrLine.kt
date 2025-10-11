package no.vegvesen.nvdb.tnits.generator.core.services.vegnett

import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.core.model.VeglenkeId
import no.vegvesen.nvdb.tnits.generator.core.model.reversedView
import org.locationtech.jts.geom.LineString
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import org.openlr.map.Line
import org.openlr.map.Node

fun TypeVeg.toFormOfWay(): FormOfWay = when (this) {
    TypeVeg.KANALISERT_VEG -> FormOfWay.MULTIPLE_CARRIAGEWAY
    TypeVeg.ENKEL_BILVEG -> FormOfWay.SINGLE_CARRIAGEWAY
    TypeVeg.RAMPE -> FormOfWay.SLIP_ROAD
    TypeVeg.RUNDKJORING -> FormOfWay.ROUNDABOUT
    TypeVeg.BILFERJE -> FormOfWay.OTHER
    TypeVeg.PASSASJERFERJE -> FormOfWay.OTHER
    TypeVeg.GANG_OG_SYKKELVEG -> FormOfWay.OTHER
    TypeVeg.SYKKELVEG -> FormOfWay.OTHER
    TypeVeg.GANGVEG -> FormOfWay.OTHER
    TypeVeg.GAGATE -> FormOfWay.SINGLE_CARRIAGEWAY
    TypeVeg.FORTAU -> FormOfWay.OTHER
    TypeVeg.TRAPP -> FormOfWay.OTHER
    TypeVeg.GANGFELT -> FormOfWay.OTHER
    TypeVeg.GATETUN -> FormOfWay.TRAFFIC_SQUARE
    TypeVeg.TRAKTORVEG -> FormOfWay.OTHER
    TypeVeg.STI -> FormOfWay.OTHER
    TypeVeg.ANNET -> FormOfWay.OTHER
}

@ConsistentCopyVisibility
data class OpenLrLine private constructor(
    val id: VeglenkeId,
    val lengde: Double,
    val startnode: OpenLrNode,
    val sluttnode: OpenLrNode,
    val geometri: LineString,
    val frc: FunctionalRoadClass,
    val fow: FormOfWay,
    val cachedVegnett: CachedVegnett,
    val tillattRetning: TillattRetning,
) : Line<OpenLrLine> {
    override fun getId(): String = id.toString()

    override fun getStartNode(): Node = startnode

    override fun getEndNode(): Node = sluttnode

    override fun getIncomingLines(): List<OpenLrLine> = cachedVegnett.getIncomingLines(startnode.id, tillattRetning).filter { it != this }

    override fun getOutgoingLines(): List<OpenLrLine> = cachedVegnett.getOutgoingLines(sluttnode.id, tillattRetning).filter { it != this }

    override fun getFunctionalRoadClass(): FunctionalRoadClass = frc

    override fun getFormOfWay(): FormOfWay = fow

    override fun getGeometry(): LineString = geometri

    override fun getLength(): Double = lengde

    companion object {
        fun fromVeglenke(
            veglenke: Veglenke,
            frc: FunctionalRoadClass,
            fow: FormOfWay,
            cachedVegnett: CachedVegnett,
            tillattRetning: TillattRetning,
        ): OpenLrLine {
            val geometry = veglenke.geometri

            if (geometry !is LineString) {
                error("Expected LineString geometry, but got ${geometry.geometryType} for VeglenkeId: ${veglenke.veglenkeId}")
            }

            val startNode = cachedVegnett.getNode(veglenke.startnode, tillattRetning) { geometry.startPoint }

            val endNode = cachedVegnett.getNode(veglenke.sluttnode, tillattRetning) { geometry.endPoint }

            val line =
                OpenLrLine(
                    id = veglenke.veglenkeId,
                    lengde = veglenke.lengde,
                    startnode = when (tillattRetning) {
                        TillattRetning.Med -> startNode
                        TillattRetning.Mot -> endNode
                    },
                    sluttnode = when (tillattRetning) {
                        TillattRetning.Med -> endNode
                        TillattRetning.Mot -> startNode
                    },
                    geometri = when (tillattRetning) {
                        TillattRetning.Med -> geometry
                        TillattRetning.Mot -> geometry.reversedView()
                    },
                    frc = frc,
                    fow = fow,
                    cachedVegnett = cachedVegnett,
                    tillattRetning = tillattRetning,
                )

            return line
        }
    }
}
