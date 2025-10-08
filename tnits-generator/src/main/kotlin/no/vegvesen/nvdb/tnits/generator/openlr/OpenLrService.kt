package no.vegvesen.nvdb.tnits.generator.openlr

import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.tnits.generator.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.generator.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.model.overlaps
import no.vegvesen.nvdb.tnits.generator.vegnett.CachedVegnett
import org.locationtech.jts.geom.LineString
import org.openlr.encoder.EncoderFactory
import org.openlr.location.LocationFactory
import org.openlr.locationreference.LineLocationReference
import org.openlr.map.Line
import org.openlr.map.MapOperations
import org.openlr.map.Path
import org.openlr.map.PathFactory

class OpenLrService(private val cachedVegnett: CachedVegnett) {
    private val encoder = EncoderFactory().create()

    val locationFactory = LocationFactory()

    val pathFactory = PathFactory()

    fun findOffsetInMeters(veglenke: Veglenke, posisjon: Double, isStart: Boolean): Double {
        val clamped = if (isStart) maxOf(veglenke.startposisjon, posisjon) else minOf(veglenke.sluttposisjon, posisjon)

        if (isStart && clamped == veglenke.startposisjon || !isStart && clamped == veglenke.sluttposisjon) {
            return 0.0
        }

        val posisjonRelativeToVeglenke =
            (clamped - veglenke.startposisjon) / (veglenke.sluttposisjon - veglenke.startposisjon)

        val offset =
            if (isStart) {
                posisjonRelativeToVeglenke * veglenke.lengde
            } else {
                (1.0 - posisjonRelativeToVeglenke) * veglenke.lengde
            }

        return offset
    }

    private val operations = MapOperations()

    fun toOpenLr(stedfestinger: List<StedfestingUtstrekning>): List<LineLocationReference> = stedfestinger
        .let(::createPaths)
        .map(::mergeConnectedPaths)
        .flatten()
        .map(locationFactory::createLineLocation)
        .map(encoder::encode)

    private fun createPaths(stedfestinger: List<StedfestingUtstrekning>): List<List<Path<OpenLrLine>>> {
        if (stedfestinger.isEmpty()) return emptyList()

        val forwardPaths = mutableListOf<Path<OpenLrLine>>()
        val reversePaths = mutableListOf<Path<OpenLrLine>>()

        for (stedfesting in stedfestinger) {
            val veglenker =
                cachedVegnett.getVeglenker(stedfesting.veglenkesekvensId).filter {
                    it.overlaps(stedfesting)
                }

            createPaths(veglenker, stedfesting, TillattRetning.Med).let(forwardPaths::addAll)
            createPaths(veglenker, stedfesting, TillattRetning.Mot).let(reversePaths::addAll)
        }

        val retning = stedfestinger.first().retning

        return listOfNotNull(
            forwardPaths.let { if (retning == Retning.MED) it else it.asReversed() }.ifEmpty { null },
            reversePaths.let { if (retning == Retning.MED) it.asReversed() else it }.ifEmpty { null },
        )
    }

    private fun createPaths(veglenker: List<Veglenke>, stedfesting: StedfestingUtstrekning, retning: TillattRetning): List<Path<OpenLrLine>> {
        val directed =
            veglenker.filter {
                cachedVegnett.hasRetning(it, retning)
            }

        if (directed.isEmpty()) {
            return emptyList()
        }

        // Group veglenker by connectivity
        val connectedGroups = groupConnectedVeglenker(directed)

        val paths = mutableListOf<Path<OpenLrLine>>()

        for (group in connectedGroups) {
            if (group.isEmpty()) continue

            val lines =
                cachedVegnett.getLines(
                    group.let {
                        if (retning == TillattRetning.Med) it else it.asReversed()
                    },
                    retning,
                )

            // Find offsets for this connected group (direction-agnostic)
            val positiveOffset = findOffsetInMeters(group.first(), stedfesting.startposisjon, true)
            val negativeOffset = findOffsetInMeters(group.last(), stedfesting.sluttposisjon, false)

            val path =
                pathFactory.create(
                    lines,
                    when (retning) {
                        TillattRetning.Med -> positiveOffset
                        TillattRetning.Mot -> negativeOffset
                    },
                    when (retning) {
                        TillattRetning.Med -> negativeOffset
                        TillattRetning.Mot -> positiveOffset
                    },
                )

            paths.add(path)
        }

        return paths
    }

    private fun groupConnectedVeglenker(veglenker: List<Veglenke>): List<List<Veglenke>> {
        if (veglenker.isEmpty()) return emptyList()

        val groups = mutableListOf<List<Veglenke>>()
        val currentGroup = mutableListOf<Veglenke>()

        veglenker.forEachIndexed { i, veglenke ->
            if (i == 0) {
                currentGroup.add(veglenke)
            } else {
                val previous = veglenker[i - 1]
                val previousEnd = (previous.geometri as LineString).endPoint
                val currentStart = (veglenke.geometri as LineString).startPoint

                if (previousEnd == currentStart) {
                    // Connected - add to current group
                    currentGroup.add(veglenke)
                } else {
                    // Not connected - finish current group and start new one
                    groups.add(currentGroup.toList())
                    currentGroup.clear()
                    currentGroup.add(veglenke)
                }
            }
        }

        // Add the final group
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup.toList())
        }

        return groups
    }

    private fun mergeConnectedPaths(paths: List<Path<OpenLrLine>>): MutableList<Path<OpenLrLine>> {
        // Antagelse: Stedfestinger ligger i rekkefølge??
        val mergedPaths = mutableListOf<Path<OpenLrLine>>()

        val connectedPaths = mutableListOf<Path<OpenLrLine>>()

        fun <L : Line<L>> Path<L>.isConnected(other: Path<L>): Boolean = this.end.geometry == other.start.geometry

        for (path in paths) {
            if (connectedPaths.isEmpty() || connectedPaths.last().isConnected(path)) {
                connectedPaths.add(path)
            } else {
                val mergedPath = operations.joinPaths(connectedPaths)
                mergedPaths.add(mergedPath)
                connectedPaths.clear()
                connectedPaths.add(path)
            }
        }

        if (connectedPaths.isNotEmpty()) {
            val mergedPath = operations.joinPaths(connectedPaths)
            mergedPaths.add(mergedPath)
        }
        return mergedPaths
    }
}

enum class TillattRetning {
    Med,
    Mot,
    ;

    fun reverse(): TillattRetning = when (this) {
        Med -> Mot
        Mot -> Med
    }
}
