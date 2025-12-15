package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.common.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.generator.clock
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig.Companion.withTempDb

class DirtyCheckingRocksDbStoreTest :
    ShouldSpec({

        should("return empty set when no dirty changes exist") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                val result = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)

                // Assert
                result.shouldBeEmpty()
            }
        }

        should("return dirty vegobjekt changes for specific type") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseIds = setOf(123L, 456L, 789L)
                val adresseIds = setOf(111L, 222L)

                // Publish dirty changes for different types
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseIds)
                    publishChangedVegobjekter(VegobjektTyper.ADRESSE, adresseIds)
                }

                // Act
                val fartsgrenseResult = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val adresseResult = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.ADRESSE)

                // Assert
                fartsgrenseResult shouldContainExactlyInAnyOrder fartsgrenseIds
                adresseResult shouldContainExactlyInAnyOrder adresseIds
            }
        }

        should("return empty set when no stedfesting exists") {
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

        should("find vegobjekter positioned on veglenkesekvenser") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext, clock)

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

        should("handle vegobjekter with multiple stedfestinger") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext, clock)

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

        should("remove specific dirty IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val allDirtyIds = setOf(501L, 502L, 503L, 504L)
                val idsToClear = setOf(502L, 504L)

                // Publish dirty changes
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, allDirtyIds)
                }

                // Verify initial state
                val initialDirtyChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialDirtyChanges shouldContainExactlyInAnyOrder allDirtyIds

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, idsToClear)

                // Assert
                val remainingDirtyChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val expectedRemaining = setOf(501L, 503L)
                remainingDirtyChanges shouldContainExactlyInAnyOrder expectedRemaining
            }
        }

        should("handle empty set gracefully when clearing dirty vegobjekt IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val dirtyIds = setOf(601L, 602L)

                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, dirtyIds)
                }

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, emptySet())

                // Assert
                val remainingDirtyChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingDirtyChanges shouldContainExactlyInAnyOrder dirtyIds
            }
        }

        should("remove all dirty IDs for specific type") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseIds = setOf(701L, 702L, 703L)
                val feltstrekningIds = setOf(801L, 802L)

                // Publish dirty changes for different types
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseIds)
                    publishChangedVegobjekter(VegobjektTyper.FELTSTREKNING, feltstrekningIds)
                }

                // Verify initial state
                dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE) shouldContainExactlyInAnyOrder fartsgrenseIds
                dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningIds

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE).shouldBeEmpty()
                dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningIds
            }
        }

        should("handle type with no dirty IDs gracefully when clearing all") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                val result = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                result.shouldBeEmpty()
            }
        }

        should("remove all dirty veglenkesekvens IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext, clock)
                val dirtyVeglenkesekvensIds = setOf(1001L, 1002L, 1003L)

                // Create vegobjekt positioned on one of the dirty veglenkesekvenser to test indirect changes
                val vegobjekt = createTestVegobjekt(
                    id = 901L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(1001L, 0.0, 1.0),
                    ),
                )
                vegobjekterStore.insert(vegobjekt)

                // Publish dirty veglenkesekvenser
                dbContext.writeBatch {
                    publishChangedVeglenkesekvenser(dirtyVeglenkesekvensIds)
                }

                // Verify initial state - should include indirect changes
                val initialDirectChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val initialIndirectChanges = dirtyCheckingStore.getIndirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialDirectChanges.shouldBeEmpty()
                initialIndirectChanges shouldHaveSize 1
                initialIndirectChanges shouldContain 901L

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - dirty veglenkesekvenser cleared, so no indirect changes
                val remainingIndirectChanges = dirtyCheckingStore.getIndirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingIndirectChanges.shouldBeEmpty()
            }
        }

        should("handle empty state gracefully when clearing all dirty veglenkesekvenser") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - should not throw any exceptions
                val changes = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                changes.shouldBeEmpty()
            }
        }

        should("only affect veglenkesekvenser and not vegobjekt changes when clearing all dirty veglenkesekvenser") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val dirtyVeglenkesekvensIds = setOf(2001L, 2002L)
                val dirtyVegobjektIds = setOf(1001L, 1002L)

                // Publish both dirty veglenkesekvenser and direct vegobjekt changes
                dbContext.writeBatch {
                    publishChangedVeglenkesekvenser(dirtyVeglenkesekvensIds)
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, dirtyVegobjektIds)
                }

                // Verify initial state includes both direct and indirect changes
                val initialChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialChanges shouldContainExactlyInAnyOrder dirtyVegobjektIds

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - only direct vegobjekt changes should remain
                val remainingChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingChanges shouldContainExactlyInAnyOrder dirtyVegobjektIds
            }
        }

        should("return main type vegobjekter only on dirty veglenkesekvenser when checking indirect changes") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext, clock)

                val dirtyVeglenkesekvens = 3001L
                val cleanVeglenkesekvens = 3003L

                // Create a FARTSGRENSE (main type) on the dirty veglenkesekvens
                val fartsgrenseOnDirty = createTestVegobjekt(
                    id = 5001L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(dirtyVeglenkesekvens, 0.0, 1.0),
                    ),
                )

                // Create a FARTSGRENSE (main type) on a clean veglenkesekvens
                val fartsgrenseOnClean = createTestVegobjekt(
                    id = 5002L,
                    type = VegobjektTyper.FARTSGRENSE,
                    stedfestinger = listOf(
                        VegobjektStedfesting(cleanVeglenkesekvens, 0.0, 1.0),
                    ),
                )

                vegobjekterStore.insert(fartsgrenseOnDirty)
                vegobjekterStore.insert(fartsgrenseOnClean)

                // Mark only one veglenkesekvens as dirty
                dbContext.writeBatch {
                    publishChangedVeglenkesekvenser(setOf(dirtyVeglenkesekvens))
                }

                // Act
                val directChanges = dirtyCheckingStore.getDirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val indirectChanges = dirtyCheckingStore.getIndirectDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)

                // Assert
                directChanges.shouldBeEmpty()
                indirectChanges shouldContainExactlyInAnyOrder setOf(5001L)
            }
        }
    })

private fun createTestVegobjekt(id: Long, type: Int, stedfestinger: List<VegobjektStedfesting>): Vegobjekt = Vegobjekt(
    id = id,
    type = type,
    startdato = LocalDate(2023, 1, 1),
    sluttdato = null,
    sistEndret = clock.now(),
    stedfestinger = stedfestinger,
    egenskaper = emptyMap(),
    originalStartdato = null,
)
