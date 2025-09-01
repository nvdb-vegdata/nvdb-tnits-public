package no.vegvesen.nvdb.tnits.model

import org.locationtech.jts.geom.*

class ReversedCoordinateSequence(
    private val base: CoordinateSequence,
) : CoordinateSequence {
    override fun getDimension(): Int = base.dimension

    override fun size(): Int = base.size()

    override fun setOrdinate(
        index: Int,
        ordinateIndex: Int,
        value: Double,
    ): Unit = throw UnsupportedOperationException("ReversedCoordinateSequence is read-only")

    override fun getX(index: Int): Double = base.getX(size() - 1 - index)

    override fun getY(index: Int): Double = base.getY(size() - 1 - index)

    override fun getOrdinate(
        index: Int,
        ordinateIndex: Int,
    ): Double = base.getOrdinate(size() - 1 - index, ordinateIndex)

    override fun getCoordinate(index: Int): Coordinate = base.getCoordinateCopy(size() - 1 - index)

    override fun getCoordinateCopy(index: Int): Coordinate = base.getCoordinateCopy(size() - 1 - index)

    override fun getCoordinate(
        index: Int,
        coord: Coordinate?,
    ) {
        base.getCoordinate(size() - 1 - index, coord)
    }

    override fun toCoordinateArray(): Array<Coordinate> = Array(size()) { getCoordinate(it) }

    override fun expandEnvelope(env: Envelope?): Envelope {
        val result = env ?: Envelope()
        for (i in 0 until size()) result.expandToInclude(getX(i), getY(i))
        return result
    }

    override fun clone(): Any = ReversedCoordinateSequence(base.clone() as CoordinateSequence)

    override fun copy(): CoordinateSequence = ReversedCoordinateSequence(base.copy())
}

fun reversedView(
    line: LineString,
    factory: GeometryFactory = line.factory,
): LineString {
    val reversedSeq = ReversedCoordinateSequence(line.coordinateSequence)
    return factory.createLineString(reversedSeq)
}
