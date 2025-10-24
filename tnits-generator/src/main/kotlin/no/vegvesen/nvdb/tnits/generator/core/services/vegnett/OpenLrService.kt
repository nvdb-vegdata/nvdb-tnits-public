package no.vegvesen.nvdb.tnits.generator.core.services.vegnett

import jakarta.inject.Singleton
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.tnits.generator.core.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.core.model.overlaps
import org.locationtech.jts.geom.LineString
import org.openlr.encoder.EncoderFactory
import org.openlr.location.LocationFactory
import org.openlr.locationreference.LineLocationReference
import org.openlr.map.Line
import org.openlr.map.MapOperations
import org.openlr.map.Path
import org.openlr.map.PathFactory

@Singleton
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
            val veglenker = getOverlappingVeglenker(stedfesting)

            val forwardRetning = when (stedfesting.retning) {
                Retning.MED -> TillattRetning.Med
                Retning.MOT -> TillattRetning.Mot
                null -> TillattRetning.Med
            }

            createPaths(veglenker, stedfesting, forwardRetning).let(forwardPaths::addAll)
            createPaths(veglenker, stedfesting, forwardRetning.reverse()).let(reversePaths::addAll)
        }

        return listOfNotNull(
            forwardPaths.ifEmpty { null },
            reversePaths.asReversed().ifEmpty { null },
        )
    }

    private fun getOverlappingVeglenker(stedfesting: StedfestingUtstrekning): List<Veglenke> =
        cachedVegnett.getVeglenker(stedfesting.veglenkesekvensId).filter {
            it.overlaps(stedfesting)
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

    private fun sortPathsByConnectivity(paths: List<Path<OpenLrLine>>): List<Path<OpenLrLine>> {
        if (paths.size <= 1) return paths

        // Build lookup map: start geometry -> path (for finding what comes next)
        val pathsByStartGeometry = paths.associateBy { it.start.geometry }

        // Find all end geometries (to identify which paths have predecessors)
        val endGeometries = paths.map { it.end.geometry }.toSet()

        val visited = mutableSetOf<Path<OpenLrLine>>()
        val sorted = mutableListOf<Path<OpenLrLine>>()

        // Function to follow a chain starting from a given path
        fun followChain(startPath: Path<OpenLrLine>) {
            var current: Path<OpenLrLine>? = startPath
            while (current != null && current !in visited) {
                visited.add(current)
                sorted.add(current)
                // Find the next path in the chain (one that starts where current ends)
                current = pathsByStartGeometry[current.end.geometry]
            }
        }

        // First, process all starting paths (those with no predecessor)
        for (path in paths) {
            if (path.start.geometry !in endGeometries && path !in visited) {
                followChain(path)
            }
        }

        // Then handle any remaining paths (in case of cycles or disconnected components)
        for (path in paths) {
            if (path !in visited) {
                followChain(path)
            }
        }

        return sorted
    }

    private fun mergeConnectedPaths(paths: List<Path<OpenLrLine>>): MutableList<Path<OpenLrLine>> {
        val sortedPaths = sortPathsByConnectivity(paths)
        val mergedPaths = mutableListOf<Path<OpenLrLine>>()

        val connectedPaths = mutableListOf<Path<OpenLrLine>>()

        fun <L : Line<L>> Path<L>.isConnected(other: Path<L>): Boolean = this.end.geometry == other.start.geometry

        for (path in sortedPaths) {
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
