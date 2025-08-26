package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.tnits.model.Veglenke
import java.io.File

fun saveVeglenkerToCache(
    veglenker: Map<Long, List<Veglenke>>,
    cacheFile: File,
) {
    measure("Saving veglenker to cache") {
//        cacheFile.parentFile?.mkdirs()
//        FileOutputStream(cacheFile).use { fos ->
//            Output(fos).use { output ->
//                kryo.writeObject(output, veglenker)
//            }
//        }
        println("Veglenker cachet til ${cacheFile.absolutePath}")
    }
}

fun loadVeglenkerFromCache(cacheFile: File): Map<Long, List<Veglenke>> = TODO()
//    try {
//        measure("Loading veglenker from cache") {
//            FileInputStream(cacheFile).use { fis ->
//                Input(fis).use { input ->
//                    @Suppress("UNCHECKED_CAST")
//                    kryo.readObject(input, HashMap::class.java) as Map<Long, List<Veglenke>>
//                }
//            }
//        }
//    } catch (e: Exception) {
//        println("Kunne ikke laste veglenker fra cache: ${e.message}")
//        null
//    }

suspend fun loadVeglenkerWithCache(): Map<Long, List<Veglenke>> {
    val cacheFile = File("veglenker-cache.kryo")
    val cacheTimestamp = if (cacheFile.exists()) cacheFile.lastModified() else null
    val dbTimestamp = getCacheFileTimestamp()

    return if (cacheFile.exists() && cacheTimestamp != null && dbTimestamp != null && cacheTimestamp >= dbTimestamp) {
        println("Laster veglenker fra cache...")
        loadVeglenkerFromCache(cacheFile) ?: run {
            println("Cache feilet, laster fra database...")
            val veglenker = loadAllActiveVeglenker()
            saveVeglenkerToCache(veglenker, cacheFile)
            veglenker
        }
    } else {
        println("Cache er utdatert eller eksisterer ikke, laster fra database...")
        val veglenker = loadAllActiveVeglenker()
        saveVeglenkerToCache(veglenker, cacheFile)
        veglenker
    }
}
