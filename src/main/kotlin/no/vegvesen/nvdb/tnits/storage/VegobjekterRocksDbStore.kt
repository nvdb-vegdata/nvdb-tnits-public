package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt

@OptIn(ExperimentalSerializationApi::class)
class VegobjekterRocksDbStore(private val rocksDbConfig: RocksDbConfiguration) : VegobjekterRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.VEGOBJEKTER

    override fun batchUpdate(updates: Map<Long, Vegobjekt?>) {
        val operations =
            updates.map { (id, vegobjekt) ->

                // For every vegobjekt:
                // if deleted, delete it and reverse index

//
//            val key = id.toByteArray()
//            if (vegobjekt == null) {
//                BatchOperation.Delete(key)
//            } else {
//                BatchOperation.Put(key, value)
//            }
            }
    }
}

interface VegobjekterRepository {
    fun batchUpdate(updates: Map<Long, Vegobjekt?>)
}
