package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.IdRange
import no.vegvesen.nvdb.tnits.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.model.Vegobjekt
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.overlaps
import java.nio.ByteBuffer

@OptIn(ExperimentalSerializationApi::class)
class VegobjekterRocksDbStore(private val rocksDbContext: RocksDbContext) : VegobjekterRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER

    // TODO: Kanskje dette er unødvendig hvis vi kan anta at endringer på 616 og 821 alltid kommer sammen med endringer på tilhørende vegnett?
    private val dirtyCheckForTypes = setOf(821, 616)

    override fun findVegobjektIds(vegobjektType: Int): Sequence<Long> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamKeysByPrefix(columnFamily, prefix).map(::getVegobjektId)
    }

    override fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamEntriesByPrefix(columnFamily, prefix, getVegobjektKey(vegobjektType, idRange.startId))
            .takeWhile { (key, _) -> getVegobjektId(key) <= idRange.endId }
            .map { (_, value) -> ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), value) }
            .toList()
    }

    override fun getVegobjektStedfestingLookup(vegobjektType: Int): Map<Long, List<Vegobjekt>> {
        val vegobjektIds = mutableSetOf<Long>()
        rocksDbContext.streamKeysByPrefix(columnFamily, getStedfestingPrefix(vegobjektType)).forEach { key ->
            vegobjektIds.add(getStedfestingVegobjektId(key))
        }
        val vegobjektKeys = vegobjektIds.map { getVegobjektKey(vegobjektType, it) }
        val lookup = mutableMapOf<Long, MutableList<Vegobjekt>>()
        rocksDbContext.getBatch(columnFamily, vegobjektKeys).mapNotNull { it?.toVegobjekt() }.forEach { vegobjekt ->
            val veglenkesekvensIds = vegobjekt.stedfestinger.map { it.veglenkesekvensId }.toSet()
            for (veglenkesekvensId in veglenkesekvensIds) {
                lookup.computeIfAbsent(veglenkesekvensId) { mutableListOf() }.add(vegobjekt)
            }
        }
        return lookup
    }

    private fun ByteArray.toVegobjekt(): Vegobjekt = ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), this)

    override fun streamAll(vegobjektType: Int): Sequence<Vegobjekt> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamValuesByPrefix(columnFamily, prefix).map { ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it) }
    }

    override fun getAll(vegobjektType: Int): List<Vegobjekt> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.findValuesByPrefix(columnFamily, prefix).map { ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it) }
    }

    override fun findOverlappingVegobjekter(utstrekning: StedfestingUtstrekning, vegobjektType: Int): List<Vegobjekt> {
        val prefix = getStedfestingPrefix(vegobjektType, utstrekning.veglenkesekvensId)
        val allVegobjektIds = rocksDbContext.findKeysByPrefix(columnFamily, prefix)
            .map(::getStedfestingVegobjektId).toSet()

        val keys = allVegobjektIds.map {
            getVegobjektKey(vegobjektType, it)
        }
        return rocksDbContext.getBatch(columnFamily, keys)
            .mapNotNull {
                it?.let { ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it) }
            }.filter {
                it.stedfestinger.any { stedfesting ->
                    stedfesting.overlaps(utstrekning)
                }
            }
    }

    /**
     * Convenience method for inserting a single domain vegobjekt.
     */
    fun insert(vegobjekt: Vegobjekt) {
        val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
        rocksDbContext.write(columnFamily, vegobjektUpsert)

        val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }
        val stedfestingerUpserts = createStedfestingerUpserts(vegobjekt.type, vegobjekt.id, stedfestingerByVeglenkesekvens)
        rocksDbContext.writeBatch(columnFamily, stedfestingerUpserts)
    }

    context(context: WriteBatchContext)
    override fun batchInsert(vegobjektType: Int, vegobjekter: List<Vegobjekt>) {
        for (vegobjekt in vegobjekter) {
            val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
            context.write(columnFamily, vegobjektUpsert)

            val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }
            val stedfestingerUpserts = createStedfestingerUpserts(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
            context.write(columnFamily, stedfestingerUpserts)
        }
    }

    context(context: WriteBatchContext)
    override fun batchUpdate(vegobjektType: Int, updates: Map<Long, Vegobjekt?>) {
        for ((vegobjektId, vegobjekt) in updates) {
            val vegobjektKey = getVegobjektKey(vegobjektType, vegobjektId)

            if (vegobjekt == null) {
                val existingVegobjekt = rocksDbContext.get(columnFamily, vegobjektKey)
                    ?: continue // Already deleted

                val stedfestingerDeletions = ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), existingVegobjekt).stedfestinger.map {
                    getStedfestingKey(it.veglenkesekvensId, vegobjektType, vegobjektId)
                }.toSet().map { BatchOperation.Delete(it) }

                context.write(columnFamily, BatchOperation.Delete(vegobjektKey))
                context.write(columnFamily, stedfestingerDeletions)
            } else {
                val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
                context.write(columnFamily, vegobjektUpsert)

                val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }
                val stedfestingerUpserts = createStedfestingerUpserts(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
                context.write(columnFamily, stedfestingerUpserts)

                val stedfestingerDeletions = createStedfestingerDeletions(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
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

    fun findVegobjekt(typeId: Int, vegobjektId: Long): Vegobjekt? {
        val key = getVegobjektKey(typeId, vegobjektId)
        val data = rocksDbContext.get(columnFamily, key) ?: return null
        return ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), data)
    }

    fun findStedfestinger(typeId: Int, veglenkesekvensId: Long): List<VegobjektStedfesting> {
        val prefix = getStedfestingPrefix(typeId, veglenkesekvensId)
        val values = rocksDbContext.streamValuesByPrefix(columnFamily, prefix).single()
            .let { ProtoBuf.decodeFromByteArray(ListSerializer(VegobjektStedfesting.serializer()), it) }
        return values
    }

    companion object {

        private fun getVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 5, 8).long

        private fun getStedfestingVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 13, 8).long

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType][8 bytes vegobjektId] = 13 bytes
         */
        private fun getVegobjektKey(vegobjektType: Int, vegobjektId: Long): ByteArray =
            ByteBuffer.allocate(13).put(VEGOBJEKT_KEY_PREFIX).putInt(vegobjektType).putLong(vegobjektId).array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType][8 bytes veglenkesekvensId][8 bytes vegobjektId] = 21 bytes
         */
        private fun getStedfestingKey(veglenkesekvensId: Long, vegobjektType: Int, vegobjektId: Long): ByteArray =
            ByteBuffer.allocate(21).put(STEDFESTING_KEY_PREFIX).putInt(vegobjektType).putLong(veglenkesekvensId).putLong(vegobjektId)
                .array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType] = 5 bytes
         */
        private fun getVegobjektTypePrefix(vegobjektType: Int): ByteArray = ByteBuffer.allocate(5).put(VEGOBJEKT_KEY_PREFIX).putInt(vegobjektType).array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType] = 5 bytes
         */
        private fun getStedfestingPrefix(vegobjektType: Int): ByteArray = ByteBuffer.allocate(5).put(STEDFESTING_KEY_PREFIX).putInt(vegobjektType).array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType][8 bytes veglenkesekvensId] = 13 bytes
         */
        private fun getStedfestingPrefix(vegobjektType: Int, veglenkesekvensId: Long): ByteArray =
            ByteBuffer.allocate(13).put(STEDFESTING_KEY_PREFIX).putInt(vegobjektType).putLong(veglenkesekvensId).array()

        const val VEGOBJEKT_KEY_PREFIX: Byte = 0
        const val STEDFESTING_KEY_PREFIX: Byte = 1
    }
}
