package no.vegvesen.nvdb.tnits

import kotlinx.datetime.LocalDate
import org.locationtech.jts.geom.Geometry
import org.openlr.locationreference.LineLocationReference
import kotlin.time.Instant

data class SpeedLimit(
    val id: Long,
    val kmh: Int,
    val geometry: Geometry,
    val locationReferences: List<LineLocationReference>,
    val validFrom: LocalDate,
    val validTo: LocalDate? = null,
    val updateType: UpdateType,
    val beginLifespanVersion: Instant,
)
