package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.VeglenkesekvenserSide
import no.vegvesen.nvdb.tnits.Services.Companion.objectMapper
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.toDomain
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.openlr.readJson
import no.vegvesen.nvdb.tnits.vegnett.VeglenkesekvenserService.Companion.convertToDomainVeglenker
import no.vegvesen.nvdb.tnits.vegobjekter.getStedfestingLinjer
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

class VegobjekterRocksDbStoreTest :
    StringSpec({

        "save and retrieve vegobjekt and stedfestinger" {
            withTempDb { dbContext ->
                val vegobjekter = VegobjekterRocksDbStore(dbContext)
                val apiVegobjekt = objectMapper.readJson<ApiVegobjekt>("vegobjekt-616-1020953586.json")
                val domainVegobjekt = apiVegobjekt.toDomain()
                vegobjekter.insert(domainVegobjekt)

                val retrieved = vegobjekter.findVegobjekt(apiVegobjekt.typeId, apiVegobjekt.id)
                val stedfestinger = vegobjekter.findStedfestinger(apiVegobjekt.typeId, apiVegobjekt.getStedfestingLinjer().single().veglenkesekvensId)

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
                vegobjekter.insert(feltstrekning.toDomain())
                vegobjekter.insert(funskjonellVegklasse.toDomain())
                val veglenke = veglenker.single()

                val overlappingFeltstrekning = vegobjekter.findOverlappingVegobjekter(veglenke, 616)
                val overlappingFrc = vegobjekter.findOverlappingVegobjekter(veglenke, 821)

                overlappingFeltstrekning.size shouldBe 1
                overlappingFrc.size shouldBe 1
            }
        }

        "getVegobjektStedfestingLookup should group vegobjekter by veglenkesekvensId" {
            withTempDb { dbContext ->
                // Arrange
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)
                val baseApiVegobjekt = objectMapper.readJson<ApiVegobjekt>("vegobjekt-616-1020953586.json")
                val baseDomainVegobjekt = baseApiVegobjekt.toDomain()

                // Create test vegobjekter using .copy() with different IDs and stedfestinger
                val vegobjekt1001 = baseDomainVegobjekt.copy(
                    id = 1001L,
                    stedfestinger = listOf(
                        VegobjektStedfesting(
                            veglenkesekvensId = 100L,
                            startposisjon = 0.0,
                            sluttposisjon = 1.0,
                            retning = Retning.MED,
                        ),
                    ),
                )

                val vegobjekt1002 = baseDomainVegobjekt.copy(
                    id = 1002L,
                    stedfestinger = listOf(
                        VegobjektStedfesting(
                            veglenkesekvensId = 100L,
                            startposisjon = 0.0,
                            sluttposisjon = 0.5,
                            retning = Retning.MED,
                        ),
                        VegobjektStedfesting(
                            veglenkesekvensId = 200L,
                            startposisjon = 0.5,
                            sluttposisjon = 1.0,
                            retning = Retning.MED,
                        ),
                    ),
                )

                val vegobjekt1003 = baseDomainVegobjekt.copy(
                    id = 1003L,
                    stedfestinger = listOf(
                        VegobjektStedfesting(
                            veglenkesekvensId = 200L,
                            startposisjon = 0.0,
                            sluttposisjon = 1.0,
                            retning = Retning.MOT,
                        ),
                    ),
                )

                vegobjekterStore.insert(vegobjekt1001)
                vegobjekterStore.insert(vegobjekt1002)
                vegobjekterStore.insert(vegobjekt1003)

                // Act
                val lookup = vegobjekterStore.getVegobjektStedfestingLookup(616)

                // Assert
                lookup shouldContainKey 100L
                lookup shouldContainKey 200L
                lookup.keys shouldHaveSize 2

                // Verify veglenkesekvens 100 contains vegobjekt 1001 and 1002
                val vegobjekterOn100 = lookup[100L]!!
                vegobjekterOn100 shouldHaveSize 2
                vegobjekterOn100.map { it.id } shouldContainExactlyInAnyOrder listOf(1001L, 1002L)

                // Verify veglenkesekvens 200 contains vegobjekt 1002 and 1003
                val vegobjekterOn200 = lookup[200L]!!
                vegobjekterOn200 shouldHaveSize 2
                vegobjekterOn200.map { it.id } shouldContainExactlyInAnyOrder listOf(1002L, 1003L)

                // Verify that vegobjekt 1002 appears in both groups (spans multiple veglenkesekvenser)
                vegobjekterOn100.any { it.id == 1002L } shouldBe true
                vegobjekterOn200.any { it.id == 1002L } shouldBe true
            }
        }

        "getVegobjektStedfestingLookup should filter by vegobjekt type" {
            withTempDb { dbContext ->
                // Arrange
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)
                val feltstrekning = objectMapper.readJson<ApiVegobjekt>("vegobjekt-616-1020953586.json")
                val funksjonellVegklasse = objectMapper.readJson<ApiVegobjekt>("vegobjekt-821-642414069.json")

                vegobjekterStore.insert(feltstrekning.toDomain())
                vegobjekterStore.insert(funksjonellVegklasse.toDomain())

                // Act - Get lookup for different vegobjekt types
                val feltstrekningLookup = vegobjekterStore.getVegobjektStedfestingLookup(616)
                val vegklasseLookup = vegobjekterStore.getVegobjektStedfestingLookup(821)

                // Assert - Each lookup should only contain vegobjekter of the requested type
                feltstrekningLookup.values.flatten().forEach { vegobjekt ->
                    vegobjekt.type shouldBe 616
                }

                vegklasseLookup.values.flatten().forEach { vegobjekt ->
                    vegobjekt.type shouldBe 821
                }

                // Verify different vegobjekt types produce different lookups
                feltstrekningLookup.values.flatten().map { it.id }.toSet() shouldBe listOf(1020953586L)
                vegklasseLookup.values.flatten().map { it.id }.toSet() shouldBe listOf(642414069L)
            }
        }

        "getVegobjektStedfestingLookup should handle empty results" {
            withTempDb { dbContext ->
                // Arrange
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)

                // Act - Get lookup for type with no vegobjekter
                val lookup = vegobjekterStore.getVegobjektStedfestingLookup(105) // FARTSGRENSE type

                // Assert
                lookup shouldBe emptyMap()
            }
        }
    })
