package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb

class VegobjekterHashStoreTest :
    StringSpec({

        "put and get single hash should work correctly" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val vegobjektId = 12345L
                val hash = 9876543210L

                // Act
                hashStore.put(vegobjektType, vegobjektId, hash)
                val retrievedHash = hashStore.get(vegobjektType, vegobjektId)

                // Assert
                retrievedHash shouldBe hash
            }
        }

        "get non-existent hash should return null" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)

                // Act
                val retrievedHash = hashStore.get(105, 99999L)

                // Assert
                retrievedHash.shouldBeNull()
            }
        }

        "delete hash should remove it from storage" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val vegobjektId = 12345L
                val hash = 9876543210L

                hashStore.put(vegobjektType, vegobjektId, hash)

                // Act
                hashStore.delete(vegobjektType, vegobjektId)
                val retrievedHash = hashStore.get(vegobjektType, vegobjektId)

                // Assert
                retrievedHash.shouldBeNull()
            }
        }

        "batchGet should retrieve multiple hashes correctly" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val testData = mapOf(
                    12345L to 1111111111L,
                    23456L to 2222222222L,
                    34567L to 3333333333L,
                )

                testData.forEach { (id, hash) ->
                    hashStore.put(vegobjektType, id, hash)
                }

                // Act
                val vegobjektIds = listOf(12345L, 23456L, 34567L, 99999L) // Include non-existent ID
                val results = hashStore.batchGet(vegobjektType, vegobjektIds)

                // Assert
                results shouldContainExactly mapOf(
                    12345L to 1111111111L,
                    23456L to 2222222222L,
                    34567L to 3333333333L,
                    99999L to null,
                )
            }
        }

        "batchGet with empty list should return empty map" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)

                // Act
                val results = hashStore.batchGet(105, emptyList())

                // Assert
                results shouldBe emptyMap()
            }
        }

        "batchUpdate should update multiple hashes atomically" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val hashesById = mapOf(
                    12345L to 1111111111L,
                    23456L to 2222222222L,
                    34567L to 3333333333L,
                )

                // Act
                dbContext.writeBatch {
                    hashStore.batchUpdate(vegobjektType, hashesById)
                }

                // Assert
                val retrievedHashes = hashStore.batchGet(vegobjektType, hashesById.keys.toList())
                retrievedHashes shouldContainExactly hashesById.mapValues { it.value }
            }
        }

        "different vegobjektType values should not interfere with each other" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektId = 12345L
                val type105Hash = 1111111111L
                val type616Hash = 2222222222L

                // Act
                hashStore.put(105, vegobjektId, type105Hash)
                hashStore.put(616, vegobjektId, type616Hash)

                // Assert
                hashStore.get(105, vegobjektId) shouldBe type105Hash
                hashStore.get(616, vegobjektId) shouldBe type616Hash
            }
        }

        "large hash values should be stored and retrieved correctly" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val vegobjektId = 12345L
                val largeHash = Long.MAX_VALUE

                // Act
                hashStore.put(vegobjektType, vegobjektId, largeHash)
                val retrievedHash = hashStore.get(vegobjektType, vegobjektId)

                // Assert
                retrievedHash shouldBe largeHash
            }
        }

        "negative hash values should be stored and retrieved correctly" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val vegobjektId = 12345L
                val negativeHash = Long.MIN_VALUE

                // Act
                hashStore.put(vegobjektType, vegobjektId, negativeHash)
                val retrievedHash = hashStore.get(vegobjektType, vegobjektId)

                // Assert
                retrievedHash shouldBe negativeHash
            }
        }

        "updating existing hash should overwrite previous value" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val vegobjektType = 105
                val vegobjektId = 12345L
                val originalHash = 1111111111L
                val updatedHash = 2222222222L

                // Act
                hashStore.put(vegobjektType, vegobjektId, originalHash)
                hashStore.put(vegobjektType, vegobjektId, updatedHash)
                val retrievedHash = hashStore.get(vegobjektType, vegobjektId)

                // Assert
                retrievedHash shouldBe updatedHash
            }
        }

        "batchUpdate should handle empty map gracefully" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)

                // Act & Assert - Should not throw exception
                dbContext.writeBatch {
                    hashStore.batchUpdate(105, emptyMap())
                }
            }
        }

        "multiple batch operations in same transaction should be atomic" {
            withTempDb { dbContext ->
                // Arrange
                val hashStore = VegobjekterHashStore(dbContext)
                val type105Hashes = mapOf(100L to 1100L, 200L to 1200L)
                val type616Hashes = mapOf(300L to 6300L, 400L to 6400L)

                // Act
                dbContext.writeBatch {
                    hashStore.batchUpdate(105, type105Hashes)
                    hashStore.batchUpdate(616, type616Hashes)
                }

                // Assert
                val all105Ids = type105Hashes.keys.toList()
                val all616Ids = type616Hashes.keys.toList()

                val retrieved105 = hashStore.batchGet(105, all105Ids)
                val retrieved616 = hashStore.batchGet(616, all616Ids)

                retrieved105 shouldContainExactly type105Hashes
                retrieved616 shouldContainExactly type616Hashes
            }
        }
    })
