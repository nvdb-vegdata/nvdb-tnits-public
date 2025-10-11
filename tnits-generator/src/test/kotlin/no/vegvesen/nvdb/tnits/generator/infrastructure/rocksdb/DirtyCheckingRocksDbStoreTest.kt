package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.tnits.common.model.VegobjektTyper
import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType
import no.vegvesen.nvdb.tnits.generator.core.model.Vegobjekt
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektStedfesting
import no.vegvesen.nvdb.tnits.generator.core.services.storage.VegobjektChange
import no.vegvesen.nvdb.tnits.generator.core.services.storage.publishChangedVeglenkesekvenser
import no.vegvesen.nvdb.tnits.generator.core.services.storage.publishChangedVegobjekter
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.DirtyCheckingRocksDbStore
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VegobjekterRocksDbStore
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig.Companion.withTempDb
import kotlin.time.Clock

class DirtyCheckingRocksDbStoreTest :
    ShouldSpec({

        should("return empty set when no dirty changes exist") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                val result = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)

                // Assert
                result.shouldBeEmpty()
            }
        }

        should("return dirty vegobjekt changes for specific type") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseChanges = listOf(
                    VegobjektChange(123L, ChangeType.NEW),
                    VegobjektChange(456L, ChangeType.MODIFIED),
                    VegobjektChange(789L, ChangeType.DELETED),
                )
                val feltstrekningChanges = listOf(
                    VegobjektChange(111L, ChangeType.NEW),
                    VegobjektChange(222L, ChangeType.MODIFIED),
                )

                // Publish dirty changes for different types
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseChanges)
                    publishChangedVegobjekter(VegobjektTyper.FELTSTREKNING, feltstrekningChanges)
                }

                // Act
                val fartsgrenseResult = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val feltstrekningSResult = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FELTSTREKNING)

                // Assert
                fartsgrenseResult shouldContainExactlyInAnyOrder fartsgrenseChanges
                feltstrekningSResult shouldContainExactlyInAnyOrder feltstrekningChanges
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

        should("handle vegobjekter with multiple stedfestinger") {
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

        should("remove specific dirty IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val allDirtyChanges = listOf(
                    VegobjektChange(501L, ChangeType.NEW),
                    VegobjektChange(502L, ChangeType.MODIFIED),
                    VegobjektChange(503L, ChangeType.NEW),
                    VegobjektChange(504L, ChangeType.DELETED),
                )
                val idsToClear = setOf(502L, 504L)

                // Publish dirty changes
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, allDirtyChanges)
                }

                // Verify initial state
                val initialDirtyChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialDirtyChanges shouldContainExactlyInAnyOrder allDirtyChanges

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, idsToClear)

                // Assert
                val remainingDirtyChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                val expectedRemaining = listOf(
                    VegobjektChange(501L, ChangeType.NEW),
                    VegobjektChange(503L, ChangeType.NEW),
                )
                remainingDirtyChanges shouldContainExactlyInAnyOrder expectedRemaining
            }
        }

        should("handle empty set gracefully when clearing dirty vegobjekt IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val dirtyChanges = listOf(
                    VegobjektChange(601L, ChangeType.NEW),
                    VegobjektChange(602L, ChangeType.MODIFIED),
                )

                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, dirtyChanges)
                }

                // Act
                dirtyCheckingStore.clearDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE, emptySet())

                // Assert
                val remainingDirtyChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingDirtyChanges shouldContainExactlyInAnyOrder dirtyChanges
            }
        }

        should("remove all dirty IDs for specific type") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val fartsgrenseChanges = listOf(
                    VegobjektChange(701L, ChangeType.NEW),
                    VegobjektChange(702L, ChangeType.MODIFIED),
                    VegobjektChange(703L, ChangeType.DELETED),
                )
                val feltstrekningChanges = listOf(
                    VegobjektChange(801L, ChangeType.NEW),
                    VegobjektChange(802L, ChangeType.MODIFIED),
                )

                // Publish dirty changes for different types
                dbContext.writeBatch {
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, fartsgrenseChanges)
                    publishChangedVegobjekter(VegobjektTyper.FELTSTREKNING, feltstrekningChanges)
                }

                // Verify initial state
                dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE) shouldContainExactlyInAnyOrder fartsgrenseChanges
                dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningChanges

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE).shouldBeEmpty()
                dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FELTSTREKNING) shouldContainExactlyInAnyOrder feltstrekningChanges
            }
        }

        should("handle type with no dirty IDs gracefully when clearing all") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                dirtyCheckingStore.clearAllDirtyVegobjektIds(VegobjektTyper.FARTSGRENSE)

                // Assert
                val result = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                result.shouldBeEmpty()
            }
        }

        should("remove all dirty veglenkesekvens IDs") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val vegobjekterStore = VegobjekterRocksDbStore(dbContext)
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
                val initialChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialChanges shouldHaveSize 1
                initialChanges shouldContain VegobjektChange(901L, ChangeType.MODIFIED)

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - dirty veglenkesekvenser cleared, so no indirect changes
                val remainingChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingChanges.shouldBeEmpty()
            }
        }

        should("handle empty state gracefully when clearing all dirty veglenkesekvenser") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - should not throw any exceptions
                val changes = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                changes.shouldBeEmpty()
            }
        }

        should("only affect veglenkesekvenser and not vegobjekt changes when clearing all dirty veglenkesekvenser") {
            withTempDb { dbContext ->
                // Arrange
                val dirtyCheckingStore = DirtyCheckingRocksDbStore(dbContext)
                val dirtyVeglenkesekvensIds = setOf(2001L, 2002L)
                val dirtyVegobjektChanges = listOf(
                    VegobjektChange(1001L, ChangeType.NEW),
                    VegobjektChange(1002L, ChangeType.MODIFIED),
                )

                // Publish both dirty veglenkesekvenser and direct vegobjekt changes
                dbContext.writeBatch {
                    publishChangedVeglenkesekvenser(dirtyVeglenkesekvensIds)
                    publishChangedVegobjekter(VegobjektTyper.FARTSGRENSE, dirtyVegobjektChanges)
                }

                // Verify initial state includes both direct and indirect changes
                val initialChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                initialChanges shouldContainExactlyInAnyOrder dirtyVegobjektChanges

                // Act
                dirtyCheckingStore.clearAllDirtyVeglenkesekvenser()

                // Assert - only direct vegobjekt changes should remain
                val remainingChanges = dirtyCheckingStore.getDirtyVegobjektChanges(VegobjektTyper.FARTSGRENSE)
                remainingChanges shouldContainExactlyInAnyOrder dirtyVegobjektChanges
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
