package no.vegvesen.nvdb.tnits.generator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.RemoveObjectsArgs
import io.minio.messages.DeleteObject
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.tnits.generator.core.extensions.today
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenkesekvens
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.model.toDomain
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VegobjekterRocksDbStore
import java.io.InputStream
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.time.Clock
import no.vegvesen.nvdb.apiles.uberiket.Veglenkesekvens as ApiVeglenkesekvens
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

val clock = Clock.System

fun ObjectMapper.readApiVegobjekt(path: String): ApiVegobjekt = readJson<ApiVegobjekt>(path)

fun readJsonTestResources(): List<String> = Files.walk(Path("src/test/resources"), 1).filter {
    it.isRegularFile() && it.name.endsWith(".json")
}.map { it.fileName.toString() }.toList()

fun readTestData(vararg paths: String): Pair<List<Veglenkesekvens>, List<Vegobjekt>> {
    val veglenkesekvenser =
        paths.filter { it.startsWith("veglenkesekvens") }.flatMap { path ->
            streamFile(path).use { inputStream ->
                val jsonNode = objectMapper.readTree(inputStream)
                if (jsonNode.has("veglenkesekvenser")) {
                    objectMapper.treeToValue(jsonNode, VeglenkesekvenserSide::class.java).veglenkesekvenser
                } else {
                    listOf(objectMapper.treeToValue(jsonNode, ApiVeglenkesekvens::class.java))
                }
            }
        }.map { it.toDomain(clock.today()) }

    val vegobjekter = paths.filter { it.startsWith("vegobjekt") }.map { path ->
        objectMapper.readApiVegobjekt(path).toDomain()
    }

    return veglenkesekvenser to vegobjekter
}

fun setupCachedVegnett(dbContext: RocksDbContext, vararg paths: String): CachedVegnett {
    val (veglenkesekvenser, vegobjekter) = readTestData(*paths)

    val veglenkerStore = VeglenkerRocksDbStore(dbContext, clock)
    for (veglenkesekvens in veglenkesekvenser) {
        veglenkerStore.upsert(veglenkesekvens.veglenkesekvensId, veglenkesekvens.veglenker)
    }

    val vegobjekterStore = VegobjekterRocksDbStore(dbContext, clock)
    for (vegobjekt in vegobjekter) {
        vegobjekterStore.insert(vegobjekt)
    }

    return CachedVegnett(veglenkerStore, vegobjekterStore, clock)
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
