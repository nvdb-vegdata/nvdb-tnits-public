package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import jakarta.inject.Singleton
import kotlinx.datetime.todayIn
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.common.extensions.WithLogger
import no.vegvesen.nvdb.tnits.generator.core.api.DirtyVeglenkesekvenser
import no.vegvesen.nvdb.tnits.generator.core.api.VegobjekterRepository
import no.vegvesen.nvdb.tnits.generator.core.extensions.OsloZone
import no.vegvesen.nvdb.tnits.generator.core.model.*
import no.vegvesen.nvdb.tnits.generator.core.services.storage.BatchOperation
import no.vegvesen.nvdb.tnits.generator.core.services.storage.ColumnFamily
import no.vegvesen.nvdb.tnits.generator.core.services.storage.WriteBatchContext
import java.nio.ByteBuffer
import kotlin.time.Clock

@Singleton
class VegobjekterRocksDbStore(
    private val rocksDbContext: RocksDbContext,
    private val clock: Clock,
) : VegobjekterRepository,
    WithLogger {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER

    override fun findVegobjektIds(vegobjektType: Int): Sequence<Long> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamKeysByPrefix(columnFamily, prefix).map(::getVegobjektId)
    }

    override fun findVegobjekter(vegobjektType: Int, ids: Collection<Long>): Map<Long, Vegobjekt?> {
        val keys = ids.map { getVegobjektKey(vegobjektType, it) }
        val values = rocksDbContext.getBatch(columnFamily, keys).map { it?.toVegobjekt() }
        return ids.mapIndexed { i, id -> id to values[i] }.toMap()
    }

    override fun getVegobjektStedfestingLookup(vegobjektType: Int, veglenkesekvensIds: List<Long>): Map<Long, List<Vegobjekt>> {
        val vegobjektIds = mutableSetOf<Long>()

        for (veglenkesekvensId in veglenkesekvensIds) {
            val prefix = getStedfestingPrefix(vegobjektType, veglenkesekvensId)
            rocksDbContext.streamKeysByPrefix(columnFamily, prefix).forEach { key ->
                vegobjektIds.add(getStedfestingVegobjektId(key))
            }
        }

        return getVegobjekterByVeglenkesekvensId(vegobjektIds, vegobjektType)
    }

    override fun getVegobjektStedfestingLookup(vegobjektType: Int): Map<Long, List<Vegobjekt>> {
        val vegobjektIds = mutableSetOf<Long>()
        rocksDbContext.streamKeysByPrefix(columnFamily, getStedfestingPrefix(vegobjektType)).forEach { key ->
            vegobjektIds.add(getStedfestingVegobjektId(key))
        }
        return getVegobjekterByVeglenkesekvensId(vegobjektIds, vegobjektType)
    }

    private fun getVegobjekterByVeglenkesekvensId(vegobjektIds: MutableSet<Long>, vegobjektType: Int): MutableMap<Long, MutableList<Vegobjekt>> {
        val vegobjektKeys = vegobjektIds.map { getVegobjektKey(vegobjektType, it) }
        val lookup = mutableMapOf<Long, MutableList<Vegobjekt>>()
        rocksDbContext.getBatch(columnFamily, vegobjektKeys).mapNotNull { it?.toVegobjekt() }.forEach { vegobjekt ->
            val stedfestingIds = vegobjekt.stedfestinger.map { it.veglenkesekvensId }.toSet()
            for (veglenkesekvensId in stedfestingIds) {
                lookup.computeIfAbsent(veglenkesekvensId) { mutableListOf() }.add(vegobjekt)
            }
        }
        return lookup
    }

    override fun countVegobjekter(vegobjektType: Int): Int {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.countEntriesByPrefix(columnFamily, prefix)
    }

    override fun cleanOldVersions(vegobjektType: Int) {
        val today = clock.todayIn(OsloZone)
        val keysToDelete = mutableListOf<ByteArray>()

        var count = 0

        rocksDbContext.streamValuesByPrefix(columnFamily, getVegobjektTypePrefix(vegobjektType)).forEach { value ->
            val vegobjekt = ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), value)
            if (vegobjekt.fjernet || vegobjekt.sluttdato != null && vegobjekt.sluttdato <= today) {
                val key = getVegobjektKey(vegobjekt.type, vegobjekt.id)
                keysToDelete.add(key)
                keysToDelete.addAll(
                    vegobjekt.stedfestinger.map {
                        getStedfestingKey(it.veglenkesekvensId, vegobjekt.type, vegobjekt.id)
                    },
                )
                count++
            }
        }

        if (keysToDelete.isNotEmpty()) {
            log.info("Sletter $count gamle/utgåtte vegobjekter")
            rocksDbContext.writeBatch(columnFamily, keysToDelete.map { BatchOperation.Delete(it) })
        }
    }

    override fun findVegobjekter(vegobjektType: Int, idRange: IdRange): List<Vegobjekt> {
        val prefix = getVegobjektTypePrefix(vegobjektType)
        return rocksDbContext.streamEntriesByPrefix(columnFamily, prefix, getVegobjektKey(vegobjektType, idRange.startId))
            .takeWhile { (key, _) -> getVegobjektId(key) <= idRange.endId }
            .map { (_, value) -> ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), value) }
            .toList()
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
    override fun batchUpdate(vegobjektType: Int, updates: Map<Long, VegobjektUpdate>): DirtyVeglenkesekvenser {
        val dirtyVeglenkesekvenser = mutableSetOf<Long>()
        for ((vegobjektId, update) in updates) {
            val vegobjektKey = getVegobjektKey(vegobjektType, vegobjektId)

            if (update.changeType == ChangeType.DELETED) {
                val existingVegobjekt = rocksDbContext.get(columnFamily, vegobjektKey)?.toVegobjekt()
                    ?: continue // Already deleted
                val vegobjektDeletion = createVegobjektDeletion(existingVegobjekt)
                context.write(columnFamily, vegobjektDeletion)

                val stedfestingerDeletions = existingVegobjekt.stedfestinger.map {
                    dirtyVeglenkesekvenser.add(it.veglenkesekvensId)
                    getStedfestingKey(it.veglenkesekvensId, vegobjektType, vegobjektId)
                }.toSet().map { BatchOperation.Delete(it) }

                context.write(columnFamily, stedfestingerDeletions)
            } else {
                val vegobjekt = update.vegobjekt
                    ?: error("Vegobjekt must be provided for change type ${update.changeType} (id=$vegobjektId)")
                val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
                context.write(columnFamily, vegobjektUpsert)

                val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }
                val stedfestingerUpserts = createStedfestingerUpserts(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
                context.write(columnFamily, stedfestingerUpserts)

                val removedVeglenkesekvensIds = findRemovedVeglenkesekvensIds(vegobjektType, vegobjekt.id, stedfestingerByVeglenkesekvens)
                val stedfestingerDeletions = createStedfestingerDeletions(vegobjektType, vegobjekt.id, removedVeglenkesekvensIds)
                context.write(columnFamily, stedfestingerDeletions)
                dirtyVeglenkesekvenser.addAll(stedfestingerByVeglenkesekvens.keys + removedVeglenkesekvensIds)
            }
        }

        return dirtyVeglenkesekvenser
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

    private fun createStedfestingerDeletions(vegobjektType: Int, vegobjektId: Long, removedVeglenkesekvensIds: Set<Long>): List<BatchOperation.Delete> {
        val stedfestingerDeletions = removedVeglenkesekvensIds.map {
            BatchOperation.Delete(getStedfestingKey(it, vegobjektType, vegobjektId))
        }
        return stedfestingerDeletions
    }

    private fun findRemovedVeglenkesekvensIds(
        vegobjektType: Int,
        vegobjektId: Long,
        stedfestingerByVeglenkesekvens: Map<Long, List<VegobjektStedfesting>>,
    ): Set<Long> {
        val existingStedfestinger = rocksDbContext.get(columnFamily, getVegobjektKey(vegobjektType, vegobjektId))?.let {
            ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it).stedfestinger
        }?.groupBy { it.veglenkesekvensId } ?: emptyMap()
        val removedVedlenkesekvensIds = existingStedfestinger.keys - stedfestingerByVeglenkesekvens.keys
        return removedVedlenkesekvensIds
    }

    private fun createVegobjektDeletion(vegobjekt: Vegobjekt): BatchOperation.Put = createVegobjektUpsert(vegobjekt.copy(fjernet = true))

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

    override fun clearVegobjektType(vegobjektTypeId: Int) {
        log.info("Clearing feature type $vegobjektTypeId from VEGOBJEKTER column family")

        // Delete all vegobjekt keys for this type (prefix: [0][typeId])
        val vegobjektPrefix = getVegobjektTypePrefix(vegobjektTypeId)
        rocksDbContext.deleteByPrefix(ColumnFamily.VEGOBJEKTER, vegobjektPrefix)

        // Delete all stedfesting keys for this type (prefix: [1][typeId])
        val stedfestingPrefix = getStedfestingPrefix(vegobjektTypeId)
        rocksDbContext.deleteByPrefix(ColumnFamily.VEGOBJEKTER, stedfestingPrefix)
    }

    companion object {

        fun getVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 5, 8).long

        fun getStedfestingVegobjektId(key: ByteArray): Long = ByteBuffer.wrap(key, 13, 8).long

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType][8 bytes vegobjektId] = 13 bytes
         */
        fun getVegobjektKey(vegobjektType: Int, vegobjektId: Long): ByteArray =
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
        fun getVegobjektTypePrefix(vegobjektType: Int): ByteArray = ByteBuffer.allocate(5).put(VEGOBJEKT_KEY_PREFIX).putInt(vegobjektType).array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType] = 5 bytes
         */
        fun getStedfestingPrefix(vegobjektType: Int): ByteArray = ByteBuffer.allocate(5).put(STEDFESTING_KEY_PREFIX).putInt(vegobjektType).array()

        /**
         * Key structure: [1 byte prefix][4 bytes vegobjektType][8 bytes veglenkesekvensId] = 13 bytes
         */
        fun getStedfestingPrefix(vegobjektType: Int, veglenkesekvensId: Long): ByteArray =
            ByteBuffer.allocate(13).put(STEDFESTING_KEY_PREFIX).putInt(vegobjektType).putLong(veglenkesekvensId).array()

        const val VEGOBJEKT_KEY_PREFIX: Byte = 0
        const val STEDFESTING_KEY_PREFIX: Byte = 1
    }
}
