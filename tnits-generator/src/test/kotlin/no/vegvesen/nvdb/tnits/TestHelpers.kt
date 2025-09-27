package no.vegvesen.nvdb.tnits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectsArgs
import io.minio.messages.DeleteObject
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.openlr.readVegobjekt
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService.Companion.convertToDomainVeglenker
import java.io.InputStream

fun setupCachedVegnett(dbContext: RocksDbContext, vararg paths: String): CachedVegnett {
    val veglenkerStore = VeglenkerRocksDbStore(dbContext)
    val veglenkesekvenser =
        paths.filter { it.startsWith("veglenkesekvens") }.flatMap { path ->
            objectMapper.readJson<VeglenkesekvenserSide>(path).veglenkesekvenser
        }

    for (veglenkesekvens in veglenkesekvenser) {
        val veglenker = veglenkesekvens.convertToDomainVeglenker()
        veglenkerStore.upsert(veglenkesekvens.id, veglenker)
    }

    val vegobjekterStore = VegobjekterRocksDbStore(dbContext)
    for (path in paths.filter { it.startsWith("vegobjekt") }) {
        val vegobjekt = objectMapper.readVegobjekt(path)
        vegobjekterStore.insert(vegobjekt)
    }

    val cachedVegnett = CachedVegnett(veglenkerStore, vegobjekterStore)
    return cachedVegnett
}

fun MinioClient.clear(testBucket: String) {
    val objects = listObjects(
        ListObjectsArgs.builder()
            .recursive(true)
            .bucket(testBucket).build(),
    )
    val objectsToDelete = mutableListOf<DeleteObject>()

    for (result in objects) {
        objectsToDelete.add(DeleteObject(result.get().objectName()))
    }

    if (objectsToDelete.isNotEmpty()) {
        val deleteResults = removeObjects(
            RemoveObjectsArgs.builder()
                .bucket(testBucket)
                .objects(objectsToDelete)
                .build(),
        )
        // Process results to trigger actual deletion
        for (result in deleteResults) {
            result.get() // This triggers the deletion
        }
    }
}

inline fun <reified T> ObjectMapper.readJson(name: String): T = streamFile(name).use { inputStream ->
    readValue(inputStream)
}

fun streamFile(name: String): InputStream {
    val function = { }
    return function.javaClass.getResourceAsStream(name.let { if (it.startsWith("/")) it else "/$it" })
        ?: error("Could not find resource $name")
}

fun readFile(name: String): String = streamFile(name).reader().readText()
