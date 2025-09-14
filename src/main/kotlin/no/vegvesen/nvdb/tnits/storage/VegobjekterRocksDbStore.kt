package no.vegvesen.nvdb.tnits.storage

import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.IdRange
import no.vegvesen.nvdb.tnits.model.*
import java.nio.ByteBuffer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

@OptIn(ExperimentalSerializationApi::class)
class VegobjekterRocksDbStore(private val rocksDbContext: RocksDbContext) : VegobjekterRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER

    private val relevanteEgenskaperPerType: Map<Int, Set<Int>> = mapOf(
        VegobjektTyper.FARTSGRENSE to setOf(EgenskapsTyper.FARTSGRENSE),
        VegobjektTyper.FUNKSJONELL_VEGKLASSE to setOf(EgenskapsTyper.VEGKLASSE),
        VegobjektTyper.FELTSTREKNING to setOf(EgenskapsTyper.FELTOVERSIKT_I_VEGLENKERETNING),
    )

    // TODO: Kanskje dette er unødvendig hvis vi kan anta at endringer på 616 og 821 alltid kommer sammen med endringer på tilhørende vegnett?
    private val dirtyCheckForTypes = setOf(821, 616)

    private fun getVegobjektTypePrefix(vegobjektType: Int): ByteArray = ByteBuffer.allocate(5).put(VegobjektKeyPrefix).putInt(vegobjektType).array()

    override fun findVegobjektIds(vegobjektType: Int): Sequence<Long> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.keysByPrefixSequence(columnFamily, prefix).map(::getVegobjektId)
    }

    override fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.entriesByPrefixSequence(columnFamily, prefix, getVegobjektKey(vegobjektType, idRange.startId))
            .takeWhile { (key, _) -> getVegobjektId(key) <= idRange.endId }
            .map { (_, value) -> ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), value) }
            .toList()
    }

    private fun getVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 5, 8).long

    private fun getStedfestingVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 13, 8).long

    override fun findOverlappingVegobjekter(utstrekning: StedfestingUtstrekning, vegobjektType: Int): List<Vegobjekt> {
        val prefix = getStedfestingPrefix(utstrekning.veglenkesekvensId, vegobjektType)
        val allVegobjektIds = rocksDbContext.findKeysByPrefix(columnFamily, prefix)
            .map(::getStedfestingVegobjektId).toSet()

        val keys = allVegobjektIds.map {
            getVegobjektKey(vegobjektType, it)
        }
        return rocksDbContext.getBatch(columnFamily, keys)
            .mapNotNull {
                it?.let { ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it) }
            }
    }

    private fun getStedfestingPrefix(veglenkesekvensId: Long, vegobjektType: Int): ByteArray =
        ByteBuffer.allocate(13).put(StedfestingKeyPrefix).putLong(veglenkesekvensId).putInt(vegobjektType).array()

    fun insert(vegobjekt: ApiVegobjekt) {
        val vegobjektDomain = mapToDomain(vegobjekt)
        val vegobjektUpsert = createVegobjektUpsert(vegobjektDomain)
        rocksDbContext.write(columnFamily, vegobjektUpsert)

        val stedfestingerByVeglenkesekvens = vegobjektDomain.stedfestinger.groupBy { it.veglenkesekvensId }
        val stedfestingerUpserts = createStedfestingerUpserts(vegobjekt.typeId, vegobjekt.id, stedfestingerByVeglenkesekvens)
        rocksDbContext.writeBatch(columnFamily, stedfestingerUpserts)
    }

    context(context: WriteBatchContext)
    override fun batchInsert(vegobjektType: Int, vegobjekter: List<ApiVegobjekt>, validFromById: Map<Long, LocalDate>) {
        for (apiVegobjekt in vegobjekter) {
            val vegobjekt = mapToDomain(apiVegobjekt, validFromById[apiVegobjekt.id])

            val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
            context.write(columnFamily, vegobjektUpsert)

            val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }

            val stedfestingerUpserts = createStedfestingerUpserts(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
            context.write(columnFamily, stedfestingerUpserts)
        }
    }

    private fun mapToDomain(apiVegobjekt: ApiVegobjekt, overrideValidFrom: LocalDate? = null): Vegobjekt {
        val relevanteEgenskaper = relevanteEgenskaperPerType[apiVegobjekt.typeId] ?: error("Ukjent vegobjekttype: ${apiVegobjekt.typeId}")
        val vegobjekt = apiVegobjekt.toDomain(*relevanteEgenskaper.toIntArray(), originalStartdato = overrideValidFrom)
        return vegobjekt.let { if (overrideValidFrom != null) it.copy(startdato = overrideValidFrom) else it }
    }

    context(context: WriteBatchContext)
    override fun batchUpdate(vegobjektType: Int, updates: Map<Long, ApiVegobjekt?>, validFromById: Map<Long, LocalDate>) {
        for ((vegobjektId, apiVegobjekt) in updates) {
            val vegobjektKey = getVegobjektKey(vegobjektType, vegobjektId)

            if (apiVegobjekt == null) {
                val vegobjekt = rocksDbContext.get(columnFamily, vegobjektKey)
                    ?: continue // Already deleted

                val stedfestingerDeletions = ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), vegobjekt).stedfestinger.map {
                    getStedfestingKey(it.veglenkesekvensId, vegobjektType, vegobjektId)
                }.toSet().map { BatchOperation.Delete(it) }

                context.write(columnFamily, BatchOperation.Delete(vegobjektKey))
                context.write(columnFamily, stedfestingerDeletions)
            } else {
                val vegobjekt = mapToDomain(apiVegobjekt, validFromById[vegobjektId])

                val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
                context.write(columnFamily, vegobjektUpsert)

                val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }

                val stedfestingerUpserts = createStedfestingerUpserts(vegobjektType, vegobjektId, stedfestingerByVeglenkesekvens)
                context.write(columnFamily, stedfestingerUpserts)

                val stedfestingerDeletions = createStedfestingerDeletions(vegobjektType, vegobjektId, stedfestingerByVeglenkesekvens)
                context.write(columnFamily, stedfestingerDeletions)
            }
        }
    }

    private fun createStedfestingerUpserts(
        vegobjektType: Int,
        vegobjektId: Long,
        stedfestingerByVeglenkesekvens: Map<Long, List<VegobjektStedfesting>>,
    ): List<BatchOperation.Put> {
        val stedfestingerUpserts = stedfestingerByVeglenkesekvens.map { (veglenkesekvensId, stedfestinger) ->
            BatchOperation.Put(
                getStedfestingKey(veglenkesekvensId, vegobjektType, vegobjektId),
                ProtoBuf.encodeToByteArray(ListSerializer(VegobjektStedfesting.serializer()), stedfestinger),
            )
        }
        return stedfestingerUpserts
    }

    private fun createStedfestingerDeletions(
        vegobjektType: Int,
        vegobjektId: Long,
        stedfestingerByVeglenkesekvens: Map<Long, List<VegobjektStedfesting>>,
    ): List<BatchOperation.Delete> {
        val existingStedfestinger = rocksDbContext.get(columnFamily, getVegobjektKey(vegobjektType, vegobjektId))?.let {
            ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it).stedfestinger
        }?.groupBy { it.veglenkesekvensId } ?: emptyMap()
        val removedVedlenkesekvensIds = existingStedfestinger.keys - stedfestingerByVeglenkesekvens.keys
        val stedfestingerDeletions = removedVedlenkesekvensIds.map {
            BatchOperation.Delete(getStedfestingKey(it, vegobjektType, vegobjektId))
        }
        return stedfestingerDeletions
    }

    private fun createVegobjektUpsert(vegobjekt: Vegobjekt): BatchOperation.Put {
        val serializedVegobjekt = ProtoBuf.encodeToByteArray(Vegobjekt.serializer(), vegobjekt)
        val vegobjektUpsert = BatchOperation.Put(getVegobjektKey(vegobjekt.type, vegobjekt.id), serializedVegobjekt)
        return vegobjektUpsert
    }

    private fun getVegobjektKey(vegobjektType: Int, vegobjektId: Long): ByteArray =
        ByteBuffer.allocate(13).put(VegobjektKeyPrefix).putInt(vegobjektType).putLong(vegobjektId).array()

    private fun getStedfestingKey(veglenkesekvensId: Long, vegobjektType: Int, vegobjektId: Long): ByteArray =
        ByteBuffer.allocate(21).put(StedfestingKeyPrefix).putLong(veglenkesekvensId).putInt(vegobjektType).putLong(vegobjektId)
            .array()

    fun findVegobjekt(typeId: Int, vegobjektId: Long): Vegobjekt? {
        val key = getVegobjektKey(typeId, vegobjektId)
        val data = rocksDbContext.get(columnFamily, key) ?: return null
        return ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), data)
    }

    fun findStedfestinger(typeId: Int, veglenkesekvensId: Long): List<VegobjektStedfesting> {
        val prefix = getStedfestingPrefix(veglenkesekvensId, typeId)
        val values = rocksDbContext.valuesByPrefixSequence(columnFamily, prefix).single()
            .let { ProtoBuf.decodeFromByteArray(ListSerializer(VegobjektStedfesting.serializer()), it) }
        return values
    }
}

const val VegobjektKeyPrefix: Byte = 0
const val StedfestingKeyPrefix: Byte = 1
