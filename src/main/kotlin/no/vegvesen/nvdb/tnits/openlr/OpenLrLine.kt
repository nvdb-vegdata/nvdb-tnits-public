package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.VeglenkeId
import no.vegvesen.nvdb.tnits.storage.NodePortCountRepository
import no.vegvesen.nvdb.tnits.storage.VeglenkerRepository
import no.vegvesen.nvdb.tnits.today
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import org.openlr.encoder.EncoderFactory
import org.openlr.location.LineLocation
import org.openlr.location.LocationFactory
import org.openlr.locationreference.LineLocationReference
import org.openlr.map.FormOfWay
import org.openlr.map.FunctionalRoadClass
import org.openlr.map.Line
import org.openlr.map.Node

class OpenLrService(
    private val veglenkerStore: VeglenkerRepository,
    private val nodePortCountRepository: NodePortCountRepository,
) {
    private val encoder = EncoderFactory().create()

    private val locationFactory = LocationFactory()

    fun toOpenLr(stedfestinger: List<StedfestingUtstrekning>): List<LineLocationReference> {
        val veglenker =
            veglenkerStore
                .batchGet(stedfestinger.map { it.veglenkesekvensId })
                .values
                .flatten()
                .filter { it.sluttdato == null || it.sluttdato > today() }

        val lines = getLines(veglenker)

        val locations = getLineLocations(lines)

        return locations.map {
            encoder.encode(it)
        }
    }

    fun getLineLocations(lines: List<OpenLrLine>): List<LineLocation<OpenLrLine>> = listOf(locationFactory.createLineLocation(lines))

    fun getLines(veglenker: List<Veglenke>): List<OpenLrLine> {
        val nodeConnectionCountById =
            veglenker
                .flatMap { listOf(it.startnode, it.sluttnode) }
                .toSet()
                .let { nodePortCountRepository.batchGet(it) }

        fun isNodeValid(nodeId: Long): Boolean = nodeConnectionCountById[nodeId]?.let { it <= 2 } ?: false

        // TODO: Add lines with reversed geometry and start/end nodes for bidirectional veglenker
        // (check feltoversikt or superstedfesting.kjorefelt - odd numbers are in geometric direction)
        val lines =
            veglenker.map { veglenke ->
                // TODO: Hent fra 821 Funksjonell Vegklasse
                val frc = FunctionalRoadClass.FRC_0
                val fow = veglenke.typeVeg.toFormOfWay()
                val startNodeValid = isNodeValid(veglenke.startnode)
                val endNodeValid = isNodeValid(veglenke.sluttnode)
                OpenLrLine.fromVeglenke(veglenke, frc, fow, startNodeValid, endNodeValid)
            }

        val linesByStartNode = lines.groupBy { it.startnode.id }
        val linesByEndNode = lines.groupBy { it.sluttnode.id }

        for (line in lines) {
            val incomingLines = linesByEndNode[line.startnode.id] ?: emptyList()
            val outgoingLines = linesByStartNode[line.sluttnode.id] ?: emptyList()
            line.incoming.addAll(incomingLines)
            line.outgoing.addAll(outgoingLines)
        }

        return lines
    }
}

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
    val startnode: OpenLrNode,
    val sluttnode: OpenLrNode,
    val geometri: LineString,
    val frc: FunctionalRoadClass,
    val fow: FormOfWay,
) : Line<OpenLrLine> {
    val incoming: MutableList<OpenLrLine> = mutableListOf()
    val outgoing: MutableList<OpenLrLine> = mutableListOf()

    override fun getId(): String = id.toString()

    override fun getStartNode(): Node = startnode

    override fun getEndNode(): Node = sluttnode

    override fun getIncomingLines(): List<OpenLrLine> = incoming.toList()

    override fun getOutgoingLines(): List<OpenLrLine> = outgoing.toList()

    override fun getFunctionalRoadClass(): FunctionalRoadClass = frc

    override fun getFormOfWay(): FormOfWay = fow

    override fun getGeometry(): LineString = geometri

    override fun getLength(): Double = geometri.length

    companion object {
        fun fromVeglenke(
            veglenke: Veglenke,
            frc: FunctionalRoadClass,
            fow: FormOfWay,
            startNodeValid: Boolean,
            endNodeValid: Boolean,
        ): OpenLrLine {
            val geometry = veglenke.geometri

            if (geometry !is LineString) {
                error("Expected LineString geometry, but got ${geometry.geometryType} for VeglenkeId: ${veglenke.veglenkeId}")
            }

            val startNode =
                OpenLrNode(
                    id = veglenke.startnode,
                    valid = startNodeValid,
                    point = geometry.startPoint,
                )
            val endNode =
                OpenLrNode(
                    id = veglenke.sluttnode,
                    valid = endNodeValid,
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
    val id: Long,
    val valid: Boolean,
    val point: Point,
) : Node {
    override fun isValid(): Boolean = valid

    override fun getGeometry(): Point = point
}
