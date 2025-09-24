package no.vegvesen.nvdb.tnits

import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.openlr.readJson
import no.vegvesen.nvdb.tnits.openlr.readVegobjekt
import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import no.vegvesen.nvdb.tnits.storage.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.storage.VegobjekterRocksDbStore
import no.vegvesen.nvdb.tnits.vegnett.CachedVegnett
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService.Companion.convertToDomainVeglenker

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
