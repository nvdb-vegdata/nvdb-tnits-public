package no.vegvesen.nvdb.tnits.storage

import kotlinx.serialization.ExperimentalSerializationApi
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt

@OptIn(ExperimentalSerializationApi::class)
class FeltstrekningerRocksDbStore(
    private val rocksDbConfig: RocksDbConfiguration,
) : FeltstrekningerRepository {
    private val columnFamily: ColumnFamily = ColumnFamily.FELTSTREKNINGER

    override fun batchUpdate(updates: Map<Long, Vegobjekt?>) {
//        val operations = updates.map { (id, vegobjekt) ->
//
//            // For every vegobjekt:
//            // if deleted, delete it and reverse index
//
//
//
//            val key = id.toByteArray()
//            if (vegobjekt == null) {
//                BatchOperation.Delete(key)
//            } else {
//                BatchOperation.Put(key, value)
//            }
//        }
    }
}

interface FeltstrekningerRepository {
    fun batchUpdate(updates: Map<Long, Vegobjekt?>)
}
