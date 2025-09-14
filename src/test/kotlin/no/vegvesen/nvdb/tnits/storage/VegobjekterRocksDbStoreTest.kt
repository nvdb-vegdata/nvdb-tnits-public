package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.openlr.readJson
import no.vegvesen.nvdb.tnits.utstrekning
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService.Companion.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

class VegobjekterRocksDbStoreTest :
    StringSpec({

        "save and retrieve vegobjekt and stedfestinger" {
            withTempDb { dbContext ->
                val vegobjekter = VegobjekterRocksDbStore(dbContext)
                val vegobjekt = objectMapper.readJson<ApiVegobjekt>("vegobjekt-616-1020953586.json")
                vegobjekter.insert(vegobjekt)

                val retrieved = vegobjekter.findVegobjekt(vegobjekt.typeId, vegobjekt.id)
                val stedfestinger = vegobjekter.findStedfestinger(vegobjekt.typeId, vegobjekt.getStedfestingLinjer().single().veglenkesekvensId)

                retrieved.shouldNotBeNull()
                stedfestinger.shouldNotBeEmpty()
            }
        }

        "find overlapping vegobjekter" {
            withTempDb { dbContext ->
                val veglenkesekvenser = VeglenkerRocksDbStore(dbContext)
                val vegobjekter = VegobjekterRocksDbStore(dbContext)
                val veglenkesekvens = objectMapper.readJson<VeglenkesekvenserSide>("veglenkesekvens-8967.json").veglenkesekvenser.single()
                val feltstrekning = objectMapper.readJson<ApiVegobjekt>("vegobjekt-616-1020953586.json")
                val funskjonellVegklasse = objectMapper.readJson<ApiVegobjekt>("vegobjekt-821-642414069.json")
                val veglenker = veglenkesekvens.convertToDomainVeglenker()
                veglenkesekvenser.upsert(veglenkesekvens.id, veglenker)
                vegobjekter.insert(feltstrekning)
                vegobjekter.insert(funskjonellVegklasse)
                val veglenkeUtstrekning = veglenker.single().utstrekning

                val overlappingFeltstrekning = vegobjekter.findOverlappingVegobjekter(veglenkeUtstrekning, 616)
                val overlappingFrc = vegobjekter.findOverlappingVegobjekter(veglenkeUtstrekning, 821)

                overlappingFeltstrekning.size shouldBe 1
                overlappingFrc.size shouldBe 1
            }
        }
    })
