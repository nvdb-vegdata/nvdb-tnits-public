package no.vegvesen.nvdb.tnits.generator.core.api

import kotlinx.coroutines.flow.Flow
import no.vegvesen.nvdb.apiles.uberiket.*
import kotlin.time.Instant

// Exposing direct API types as a compromise here to reduce boilerplate mapping code.
// TODO: Consider mapping to domain types before returning, if it gives value.
interface UberiketApi {

    suspend fun streamVeglenkesekvenser(
        start: Long? = null,
        slutt: Long? = null,
        ider: Collection<Long>? = null,
        antall: Int = VEGLENKER_PAGE_SIZE,
    ): Flow<Veglenkesekvens>

    suspend fun getLatestVeglenkesekvensHendelseId(tidspunkt: Instant): Long

    suspend fun streamVegobjekter(typeId: Int, ider: Collection<Long>? = null, start: Long? = null, antall: Int = VEGOBJEKTER_PAGE_SIZE): Flow<Vegobjekt>

    suspend fun getLatestVegobjektHendelseId(typeId: Int, tidspunkt: Instant): Long

    fun getVegobjektHendelserPaginated(typeId: Int, startDato: Instant, antall: Int = HENDELSER_PAGE_SIZE): Flow<VegobjektNotifikasjon>

    suspend fun getVegobjektHendelser(typeId: Int, start: Long? = null, antall: Int = HENDELSER_PAGE_SIZE, startDato: Instant? = null): VegobjektHendelserSide

    fun getVegobjekterPaginated(
        typeId: Int,
        vegobjektIds: Set<Long>,
        inkluderIVegobjekt: Set<InkluderIVegobjekt> = setOf(InkluderIVegobjekt.ALLE),
    ): Flow<Vegobjekt>

    fun streamVeglenkesekvensHendelser(start: Long?): Flow<VegnettNotifikasjon>
    fun streamVegobjektHendelser(typeId: Int, start: Long?): Flow<VegobjektNotifikasjon>
}
