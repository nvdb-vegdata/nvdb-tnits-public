package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.overlaps
import no.vegvesen.nvdb.tnits.utstrekning
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import org.locationtech.jts.geom.LineString
import org.openlr.encoder.EncoderFactory
import org.openlr.location.LocationFactory
import org.openlr.locationreference.LineLocationReference
import org.openlr.map.Line
import org.openlr.map.MapOperations
import org.openlr.map.Path
import org.openlr.map.PathFactory

class OpenLrService(
    private val cachedVegnett: CachedVegnett,
) {
    private val encoder = EncoderFactory().create()

    val locationFactory = LocationFactory()

    val pathFactory = PathFactory()

    fun findOffsetInMeters(
        veglenke: Veglenke,
        posisjon: Double,
        isStart: Boolean,
    ): Double {
        require(posisjon in veglenke.startposisjon..veglenke.sluttposisjon) {
            "Posisjon $posisjon er utenfor veglenke ${veglenke.veglenkeId} som går fra ${veglenke.startposisjon} til ${veglenke.sluttposisjon}"
        }
        if (isStart && posisjon == veglenke.startposisjon || !isStart && posisjon == veglenke.sluttposisjon) {
            return 0.0
        }

        val posisjonRelativeToVeglenke =
            (posisjon - veglenke.startposisjon) / (veglenke.sluttposisjon - veglenke.startposisjon)

        val offset =
            if (isStart) {
                posisjonRelativeToVeglenke * veglenke.lengde
            } else {
                (1.0 - posisjonRelativeToVeglenke) * veglenke.lengde
            }

        return offset
    }

    private val operations = MapOperations()

    fun toOpenLr(stedfestinger: List<StedfestingUtstrekning>): List<LineLocationReference> =
        stedfestinger
            .let(::createPaths)
            .map(::mergeConnectedPaths)
            .flatten()
            .map(locationFactory::createLineLocation)
            .map(encoder::encode)

    private fun createPaths(stedfestinger: List<StedfestingUtstrekning>): List<List<Path<OpenLrLine>>> {
        val forwardPaths = mutableListOf<Path<OpenLrLine>>()
        val reversePaths = mutableListOf<Path<OpenLrLine>>()

        for (stedfesting in stedfestinger) {
            val veglenker =
                cachedVegnett.getVeglenker(stedfesting.veglenkesekvensId).filter {
                    it.utstrekning.overlaps(stedfesting)
                }

            createPath(veglenker, stedfesting, TillattRetning.Med)?.let {
                forwardPaths.add(it)
            }
            createPath(veglenker, stedfesting, TillattRetning.Mot)?.let {
                reversePaths.add(it)
            }
        }

        return listOfNotNull(forwardPaths.ifEmpty { null }, reversePaths.asReversed().ifEmpty { null })
    }

    private fun createPath(
        veglenker: List<Veglenke>,
        stedfesting: StedfestingUtstrekning,
        retning: TillattRetning,
    ): Path<OpenLrLine>? {
        val directed =
            veglenker.filter {
                cachedVegnett.hasRetning(it, retning)
            }

        if (directed.isEmpty()) {
            return null
        }

        directed.forEachIndexed { i, veglenke ->
            check(i == 0 || (veglenke.geometri as LineString).startPoint == (directed[i - 1].geometri as LineString).endPoint) {
                "Veglenker er ikke sammenhengende i retning for stedfesting $stedfesting"
            }
        }

        val lines =
            cachedVegnett.getLines(
                directed.let {
                    if (retning == TillattRetning.Med) it else it.asReversed()
                },
                retning,
            )

        val positiveOffset = findOffsetInMeters(veglenker.first(), stedfesting.startposisjon, true)
        val negativeOffset = findOffsetInMeters(veglenker.last(), stedfesting.sluttposisjon, false)

        return pathFactory.create(
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
}
