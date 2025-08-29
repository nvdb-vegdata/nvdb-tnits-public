package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.extensions.today
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import org.locationtech.jts.geom.LineString
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import org.openlr.map.Line
import org.openlr.map.Node

fun Veglenke.isActive() = sluttdato == null || sluttdato > today

fun TypeVeg.toFormOfWay(): FormOfWay =
    when (this) {
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

class OpenLrLine private constructor(
    val id: VeglenkeId,
    val veglenke: Veglenke,
    val startnode: OpenLrNode,
    val sluttnode: OpenLrNode,
    val geometri: LineString,
    val frc: FunctionalRoadClass,
    val fow: FormOfWay,
    val cachedVegnett: CachedVegnett,
) : Line<OpenLrLine> {
    override fun getId(): String = id.toString()

    override fun getStartNode(): Node = startnode

    override fun getEndNode(): Node = sluttnode

    override fun getIncomingLines(): List<OpenLrLine> = cachedVegnett.getIncomingLines(startnode.id)

    override fun getOutgoingLines(): List<OpenLrLine> = cachedVegnett.getOutgoingLines(sluttnode.id)

    override fun getFunctionalRoadClass(): FunctionalRoadClass = frc

    override fun getFormOfWay(): FormOfWay = fow

    override fun getGeometry(): LineString = geometri

    override fun getLength(): Double = veglenke.lengde

    companion object {
        fun fromVeglenke(
            veglenke: Veglenke,
            frc: FunctionalRoadClass,
            fow: FormOfWay,
            cachedVegnett: CachedVegnett,
        ): OpenLrLine {
            val geometry = veglenke.geometri

            if (geometry !is LineString) {
                error("Expected LineString geometry, but got ${geometry.geometryType} for VeglenkeId: ${veglenke.veglenkeId}")
            }

            val startNode = cachedVegnett.getNode(veglenke.startnode) { geometry.startPoint }

            val endNode = cachedVegnett.getNode(veglenke.sluttnode) { geometry.endPoint }

            val line =
                OpenLrLine(
                    id = veglenke.veglenkeId,
                    veglenke = veglenke,
                    startnode = startNode,
                    sluttnode = endNode,
                    geometri = geometry,
                    frc = frc,
                    fow = fow,
                    cachedVegnett = cachedVegnett,
                )

            return line
        }
    }
}
