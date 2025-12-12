package no.vegvesen.nvdb.tnits.generator.core.services.tnits

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import no.vegvesen.nvdb.tnits.common.model.ExportedFeatureType
import no.vegvesen.nvdb.tnits.generator.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektUpdate
import no.vegvesen.nvdb.tnits.generator.core.model.tnits.UpdateType
import kotlin.time.Instant

class FeatureTransformerTest : ShouldSpec() {

    init {
        should("not emit feature when it is identical to previously stored feature") {
            withTestServices(mockk(relaxed = true)) {
                // Arrange
                setupBackfill()

                // Pick a vegobjekt ID from the test data
                val vegobjektId = 78712521L

                // Generate the feature using the transformer and store it
                val originalFeature = featureTransformer.generateSnapshot(ExportedFeatureType.SpeedLimit)
                    .toList()
                    .first { it.id == vegobjektId }

                // Store it as the "previously exported" feature
                exportedFeatureStore.batchUpdate(mapOf(vegobjektId to originalFeature))

                val changesById = mapOf(vegobjektId to ChangeType.MODIFIED)
                val timestamp = Instant.parse("2025-01-15T10:00:00Z")

                // Act
                val emittedFeatures = featureTransformer.generateFeaturesUpdate(
                    ExportedFeatureType.SpeedLimit,
                    changesById,
                    timestamp,
                ).toList()

                // Assert
                emittedFeatures.shouldBeEmpty()
            }
        }

        should("emit feature as removed if no longer valid") {
            withTestServices(mockk(relaxed = true)) {
                // Arrange
                setupBackfill()

                // Pick a vegobjekt ID from the test data
                val vegobjektId = 78712521L

                // Generate the feature using the transformer and store it
                val originalFeature = featureTransformer.generateSnapshot(ExportedFeatureType.SpeedLimit)
                    .toList()
                    .first { it.id == vegobjektId }

                // Store it as the "previously exported" feature
                exportedFeatureStore.batchUpdate(mapOf(vegobjektId to originalFeature))

                val changesById = mapOf(vegobjektId to ChangeType.MODIFIED)
                val timestamp = Instant.parse("2025-01-15T10:00:00Z")

                // Override the vegobjekt with an invalid one
                val vegobjekt = vegobjekterRepository.findVegobjekt(105, vegobjektId)!!
                val invalidVegobjekt = vegobjekt.copy(egenskaper = emptyMap())
                dbContext.writeBatch {
                    vegobjekterRepository.batchUpdate(105, mapOf(vegobjektId to VegobjektUpdate(vegobjektId, ChangeType.MODIFIED, invalidVegobjekt)))
                }

                // Act
                val emittedFeatures = featureTransformer.generateFeaturesUpdate(
                    ExportedFeatureType.SpeedLimit,
                    changesById,
                    timestamp,
                ).toList()

                // Assert
                emittedFeatures.size shouldBe 1
                emittedFeatures.first().updateType shouldBe UpdateType.Remove
            }
        }
    }
}
