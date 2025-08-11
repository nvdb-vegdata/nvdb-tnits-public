package no.vegvesen.nvdb.database

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class SpeedLimit(
    val id: Long,
    val nvdbId: Long,
    val roadSystemReference: Int,
    val roadNumber: Int,
    val roadCategory: String,
    val fromPosition: Double,
    val toPosition: Double,
    val speedLimit: Int,
    val geometry: String,
    val openLrReference: String?,
    val validFrom: Instant?,
    val validTo: Instant?,
    val municipality: String?,
    val county: String?,
    val createdAt: Instant,
    val modifiedAt: Instant
)

@Serializable
data class RoadnetSegment(
    val id: Long,
    val roadSystemReference: Int,
    val roadNumber: Int,
    val roadCategory: String,
    val fromPosition: Double,
    val toPosition: Double,
    val geometry: String,
    val municipality: String?,
    val county: String?,
    val createdAt: Instant,
    val modifiedAt: Instant
)
