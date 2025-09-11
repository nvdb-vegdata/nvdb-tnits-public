package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.protobuf.ProtoBuf
import no.vegvesen.nvdb.tnits.model.Vegobjekt
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.toDomain
import java.nio.ByteBuffer
import kotlin.time.Clock
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

fun WriteBatchContext.publishChangedVeglenkesekvenser(veglenkesekvensIds: Set<Long>) {
    val now = Clock.System.now()
    val dirtyVeglenkesekvenserUpserts = veglenkesekvensIds.map {
        BatchOperation.Put(it.toByteArray(), now.toEpochMilliseconds().toByteArray())
    }
    write(ColumnFamily.DIRTY_VEGLENKESEKVENSER, dirtyVeglenkesekvenserUpserts)
}

@OptIn(ExperimentalSerializationApi::class)
class VegobjekterRocksDbStore(private val rocksDbConfig: RocksDbContext) : VegobjekterRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER
    private val dirtyVeglenkesekvenserColumnFamily = ColumnFamily.DIRTY_VEGLENKESEKVENSER

    private val relevanteEgenskaperPerType: Map<Int, Set<Int>> = mapOf(
        105 to setOf(2021),
    )

    // TODO: Kanskje dette er unødvendig hvis vi kan anta at endringer på 616 og 821 alltid kommer sammen med endringer på tilhørende vegnett?
    private val dirtyCheckForTypes = setOf(821, 616)

    context(context: WriteBatchContext)
    override fun batchInsert(vegobjekter: List<ApiVegobjekt>) {
        for (apiVegobjekt in vegobjekter) {
            val vegobjekt = mapToDomain(apiVegobjekt)

            val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
            context.write(columnFamily, vegobjektUpsert)

            val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }

            val stedfestingerUpserts = createStedfestingerUpserts(stedfestingerByVeglenkesekvens, vegobjekt.id)
            context.write(columnFamily, stedfestingerUpserts)

            if (apiVegobjekt.typeId in dirtyCheckForTypes) {
                context.publishChangedVeglenkesekvenser(stedfestingerByVeglenkesekvens.keys)
            }
        }
    }

    private fun mapToDomain(apiVegobjekt: ApiVegobjekt): Vegobjekt {
        val relevanteEgenskaper = relevanteEgenskaperPerType[apiVegobjekt.typeId] ?: error("Ukjent vegobjekttype: ${apiVegobjekt.typeId}")
        val vegobjekt = apiVegobjekt.toDomain(*relevanteEgenskaper.toIntArray())
        return vegobjekt
    }

    context(context: WriteBatchContext)
    override fun batchUpdate(updates: Map<Long, ApiVegobjekt?>) {
        for ((vegobjektId, apiVegobjekt) in updates) {
            val vegobjektKey = getVegobjektKey(vegobjektId)

            if (apiVegobjekt == null) {
                val vegobjekt = rocksDbConfig.get(columnFamily, vegobjektKey)
                    ?: continue // Already deleted
                val stedfestingerDeletions = ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), vegobjekt).stedfestinger.map {
                    getStedfestingKey(vegobjektId, it.veglenkesekvensId)
                }.toSet().map { BatchOperation.Delete(it) }

                context.write(columnFamily, BatchOperation.Delete(vegobjektKey))
                context.write(columnFamily, stedfestingerDeletions)
            } else {
                val vegobjekt = mapToDomain(apiVegobjekt)

                val vegobjektUpsert = createVegobjektUpsert(vegobjekt)
                context.write(columnFamily, vegobjektUpsert)

                val stedfestingerByVeglenkesekvens = vegobjekt.stedfestinger.groupBy { it.veglenkesekvensId }

                val stedfestingerUpserts = createStedfestingerUpserts(stedfestingerByVeglenkesekvens, vegobjektId)
                context.write(columnFamily, stedfestingerUpserts)

                val stedfestingerDeletions = createStedfestingerDeletions(stedfestingerByVeglenkesekvens, vegobjektId)
                context.write(columnFamily, stedfestingerDeletions)
            }
        }
    }

    private fun createStedfestingerUpserts(stedfestingerByVeglenkesekvens: Map<Long, List<VegobjektStedfesting>>, vegobjektId: Long): List<BatchOperation.Put> {
        val stedfestingerUpserts = stedfestingerByVeglenkesekvens.map { (veglenkesekvensId, stedfestinger) ->
            BatchOperation.Put(
                getStedfestingKey(vegobjektId, veglenkesekvensId),
                ProtoBuf.encodeToByteArray(ListSerializer(VegobjektStedfesting.serializer()), stedfestinger),
            )
        }
        return stedfestingerUpserts
    }

    private fun createStedfestingerDeletions(
        stedfestingerByVeglenkesekvens: Map<Long, List<VegobjektStedfesting>>,
        vegobjektId: Long,
    ): List<BatchOperation.Delete> {
        val existingStedfestinger = rocksDbConfig.get(columnFamily, getVegobjektKey(vegobjektId))?.let {
            ProtoBuf.decodeFromByteArray(Vegobjekt.serializer(), it).stedfestinger
        }?.groupBy { it.veglenkesekvensId } ?: emptyMap()
        val removedVedlenkesekvensIds = existingStedfestinger.keys - stedfestingerByVeglenkesekvens.keys
        val stedfestingerDeletions = removedVedlenkesekvensIds.map {
            BatchOperation.Delete(getStedfestingKey(vegobjektId, it))
        }
        return stedfestingerDeletions
    }

    private fun createVegobjektUpsert(vegobjekt: Vegobjekt): BatchOperation.Put {
        val serializedVegobjekt = ProtoBuf.encodeToByteArray(Vegobjekt.serializer(), vegobjekt)
        val vegobjektUpsert = BatchOperation.Put(getVegobjektKey(vegobjekt.id), serializedVegobjekt)
        return vegobjektUpsert
    }

    private fun getVegobjektKey(vegobjektId: Long): ByteArray = ByteBuffer.allocate(9).put(VegobjektKeyPrefix).putLong(vegobjektId).array()

    private fun getStedfestingKey(vegobjektId: Long, veglenkesekvensId: Long): ByteArray =
        ByteBuffer.allocate(17).put(StedfestingKeyPrefix).putLong(veglenkesekvensId).putLong(vegobjektId)
            .array()
}

const val VegobjektKeyPrefix: Byte = 0
const val StedfestingKeyPrefix: Byte = 1
