package no.vegvesen.nvdb.tnits.generator.core.api

import kotlinx.coroutines.flow.Flow
import no.vegvesen.nvdb.apiles.uberiket.InkluderIVegobjekt
import no.vegvesen.nvdb.apiles.uberiket.VegobjektNotifikasjon
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenkesekvens
import no.vegvesen.nvdb.tnits.generator.core.model.VeglenkesekvensHendelse
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektHendelse
import kotlin.time.Instant

typealias VeglenkesekvensId = Long

// Exposing direct API types as a compromise here to reduce boilerplate mapping code.
// TODO: Consider mapping to domain types before returning, if it gives value.
interface UberiketApi {

    suspend fun getVeglenkesekvenser(start: Long? = null, antall: Int = VEGLENKESEKVENSER_PAGE_SIZE): List<Veglenkesekvens>

    suspend fun getVeglenkesekvenserWithIds(ider: Collection<Long>): List<Veglenkesekvens>

    suspend fun getLatestVeglenkesekvensHendelseId(tidspunkt: Instant): Long

    suspend fun getCurrentVegobjekter(typeId: Int, start: Long? = null, antall: Int = VEGOBJEKTER_PAGE_SIZE): List<Vegobjekt>

    suspend fun getLatestVegobjektHendelseId(typeId: Int, tidspunkt: Instant): Long

    fun getVegobjektHendelserPaginated(typeId: Int, startDato: Instant, antall: Int = HENDELSER_PAGE_SIZE): Flow<VegobjektNotifikasjon>

    fun getVegobjekterPaginated(
        typeId: Int,
        vegobjektIds: Set<Long>,
        inkluderIVegobjekt: Set<InkluderIVegobjekt> = setOf(InkluderIVegobjekt.ALLE),
    ): Flow<Vegobjekt>

    fun streamVeglenkesekvensHendelser(start: Long?): Flow<VeglenkesekvensHendelse>
    fun streamVegobjektHendelser(typeId: Int, start: Long?): Flow<VegobjektHendelse>
}
