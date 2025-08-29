package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Veglenke
import no.vegvesen.nvdb.tnits.model.overlaps
import no.vegvesen.nvdb.tnits.utstrekning
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
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

    fun toOpenLr(stedfestinger: List<StedfestingUtstrekning>): List<LineLocationReference> {
        if (stedfestinger.isEmpty()) {
            return emptyList()
        }

        // TODO: How are gaps in veglenker handled?
        val paths =
            stedfestinger.map { stedfesting ->
                val veglenker =
                    cachedVegnett.getVeglenker(stedfesting.veglenkesekvensId).filter {
                        it.isActive() && it.utstrekning.overlaps(stedfesting)
                    }

                // Antagelse: Veglenker lagres sortert
                val positiveOffset = findOffsetInMeters(veglenker.first(), stedfesting.startposisjon, true)
                val negativeOffset = findOffsetInMeters(veglenker.last(), stedfesting.sluttposisjon, false)

                val lines = cachedVegnett.getLines(veglenker)

                // TODO: Handle retning and kjorefelt
                pathFactory.create(lines, positiveOffset, negativeOffset)
            }

        // TODO: Join consecutive paths
        // Antagelse: Stedfestinger ligger i rekkefølge??

        val mergedPaths = mutableListOf<Path<OpenLrLine>>()

        val connectedPaths = mutableListOf<Path<OpenLrLine>>()

        fun <L : Line<L>> Path<L>.isConnected(other: Path<L>): Boolean = this.end == other.start

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

        val locations =
            mergedPaths.map { path ->
                locationFactory.createLineLocation(path)
            }

        return locations.map { location ->
            encoder.encode(location)
        }
    }
}
