package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.model.Vegobjekt
import no.vegvesen.nvdb.tnits.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import kotlin.time.Clock

class DirtyCheckingRocksDbStoreTest :
    StringSpec({

        "getDirtyVegobjektIds should return empty set when no dirty IDs exist" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                val result = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                result.shouldBeEmpty()
            }
        }

        "getDirtyVegobjektIds should return dirty vegobjekt IDs for specific type" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseIds = setOf(123L, 456L, 789L)
                val feltstrekningIds = setOf(111L, 222L)

                // Publish dirty IDs for different types
                // TODO
//                dbContext.writeBatch {
//                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseIds)
//                    publishChangedVegobjekter(VegobjektTyper.FELTSTREKNING, feltstrekningIds)
//                }

                // Act
                val fartsgrenseResult = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)
                val feltstrekningSResult = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FELTSTREKNING)

                // Assert
                fartsgrenseResult shouldContainExactlyInAnyOrder fartsgrenseIds
                feltstrekningSResult shouldContainExactlyInAnyOrder feltstrekningIds
            }
        }

        "findStedfestingVegobjektIds should return empty set when no stedfesting exists" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val veglenkesekvensIds = setOf(1001L, 1002L)

                // Act
                val result = dirtyCheckingStore.findStedfestingVegobjektIds(veglenkesekvensIds, VegobjektTyper.FARTSGRENSE)

                // Assert
                result.shouldBeEmpty()
            }
        }

        "findStedfestingVegobjektIds should find vegobjekter positioned on veglenkesekvenser" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)

                val veglenkesekvens1 = 1001L
                val veglenkesekvens2 = 1002L
                val veglenkesekvens3 = 1003L

                // Create test vegobjekter with stedfesting on different veglenkesekvenser
                val vegobjekt1 = createTestVegobjekt(
                    id = 101L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(veglenkesekvens1, 0.0, 1.0),
                    ),
                )

                val vegobjekt2 = createTestVegobjekt(
                    id = 102L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(veglenkesekvens2, 0.0, 0.5),
                    ),
                )

                val vegobjekt3 = createTestVegobjekt(
                    id = 103L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(veglenkesekvens3, 0.2, 0.8),
                    ),
                )

                // Different type vegobjekt on same veglenkesekvens - should not be included
                val vegobjekt4 = createTestVegobjekt(
                    id = 201L,
                    type = VegobjektTyper.FELTSTREKNING,
                    stedfestinger = listOf(
                        VegobjektStedfesting(veglenkesekvens1, 0.0, 1.0),
                    ),
                )

                // Insert vegobjekter
                vegobjekterStore.insert(vegobjekt1)
                vegobjekterStore.insert(vegobjekt2)
                vegobjekterStore.insert(vegobjekt3)
                vegobjekterStore.insert(vegobjekt4)

                // Act
                val result = dirtyCheckingStore.findStedfestingVegobjektIds(
                    setOf(veglenkesekvens1, veglenkesekvens2),
                    VegobjektTyper.FARTSGRENSE,
                )

                // Assert
                result shouldContainExactlyInAnyOrder setOf(101L, 102L)
                result shouldHaveSize 2
            }
        }

        "findStedfestingVegobjektIds should handle vegobjekter with multiple stedfestinger" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)

                val veglenkesekvens1 = 2001L
                val veglenkesekvens2 = 2002L
                val veglenkesekvens3 = 2003L

                // Vegobjekt with multiple stedfestinger
                val vegobjekt = createTestVegobjekt(
                    id = 301L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(veglenkesekvens1, 0.0, 0.5),
                        VegobjektStedfesting(veglenkesekvens2, 0.5, 1.0),
                    ),
                )

                vegobjekterStore.insert(vegobjekt)

                // Act - search for one of the veglenkesekvenser
                val result1 = dirtyCheckingStore.findStedfestingVegobjektIds(
                    setOf(veglenkesekvens1),
                    VegobjektTyper.FARTSGRENSE,
                )

                // Act - search for the other veglenkesekvens
                val result2 = dirtyCheckingStore.findStedfestingVegobjektIds(
                    setOf(veglenkesekvens2),
                    VegobjektTyper.FARTSGRENSE,
                )

                // Act - search for unrelated veglenkesekvens
                val result3 = dirtyCheckingStore.findStedfestingVegobjektIds(
                    setOf(veglenkesekvens3),
                    VegobjektTyper.FARTSGRENSE,
                )

                // Assert
                result1 shouldContain 301L
                result2 shouldContain 301L
                result3.shouldBeEmpty()
            }
        }

        "clearDirtyVegobjektIds should remove specific dirty IDs" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val allDirtyIds = setOf(501L, 502L, 503L, 504L)
                val idsToClear = setOf(502L, 504L)

                // Publish dirty IDs
                // TODO
//                dbContext.writeBatch {
//                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, allDirtyIds)
//                }

                // Verify initial state
                val initialDirtyIds = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)
                initialDirtyIds shouldContainExactlyInAnyOrder allDirtyIds

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, idsToClear)

                // Assert
                val remainingDirtyIds = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)
                remainingDirtyIds shouldContainExactlyInAnyOrder setOf(501L, 503L)
            }
        }

        "clearDirtyVegobjektIds should handle empty set gracefully" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val dirtyIds = setOf(601L, 602L)

                // TODO
//                dbContext.writeBatch {
//                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, dirtyIds)
//                }

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, emptySet())

                // Assert
                val remainingDirtyIds = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)
                remainingDirtyIds shouldContainExactlyInAnyOrder dirtyIds
            }
        }

        "clearAllDirtyVegobjektIds should remove all dirty IDs for specific type" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseIds = setOf(701L, 702L, 703L)
                val feltstrekningsIds = setOf(801L, 802L)

                // Publish dirty IDs for different types
                // TODO
//                dbContext.writeBatch {
//                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseIds)
//                    publishChangedVegobjekter(VegobjektTyper.FELTSTREKNING, feltstrekningsIds)
//                }

                // Verify initial state
                dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE) shouldContainExactlyInAnyOrder fartsgrenseIds
                dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningsIds

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE).shouldBeEmpty()
                dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningsIds
            }
        }

        "clearAllDirtyVegobjektIds should handle type with no dirty IDs gracefully" {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                val result = dirtyCheckingStore.getDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)
                result.shouldBeEmpty()
            }
        }
    })

private fun createTestVegobjekt(id: Long, type: Int, stedfestinger: List<VegobjektStedfesting>): Vegobjekt = Vegobjekt(
    id = id,
    type = type,
    startdato = LocalDate(2023, 1, 1),
    sluttdato = null,
    sistEndret = Clock.System.now(),
    stedfestinger = stedfestinger,
    egenskaper = emptyMap(),
    originalStartdato = null,
)
