package no.vegvesen.nvdb.tnits.generator.openlr

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.generator.core.model.StedfestingUtstrekning
import no.vegvesen.nvdb.tnits.generator.core.model.getStedfestingLinjer
import no.vegvesen.nvdb.tnits.generator.core.services.vegnett.OpenLrService
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import no.vegvesen.nvdb.tnits.generator.marshaller
import no.vegvesen.nvdb.tnits.generator.objectMapper
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.generator.readJson
import no.vegvesen.nvdb.tnits.generator.setupCachedVegnett
import org.openlr.map.FunctionalRoadClass
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt as ApiVegobjekt

class OpenLrServiceTest : ShouldSpec({

    should("convert speed limit stedfesting to OpenLR with real NVDB data") {
        withTempDb { dbContext ->
            // Arrange
            val openLrService =
                setupOpenLrService(
                    dbContext,
                    "veglenkesekvens-41423.json",
                    "veglenkesekvenser-41437-41438.json",
                    "vegobjekt-821-568696095.json",
                )
            val stedfestinger = loadStedfestinger("vegobjekt-105-85283803.json")

            // Act
            val openLrReferences = openLrService.toOpenLr(stedfestinger)
            val binary = openLrReferences.map(marshaller::marshallToBase64String)

            // Assert
            openLrReferences shouldHaveSize 4

            // Første stedfesting, med lenkeretning
            openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.45458 to 63.43004,
                    10.45995 to 63.42732,
                )
            // Siste stedfesting, med lenkeretning
            openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.46041 to 63.42712,
                    10.46647 to 63.42433,
                )
            // Siste stedfesting, mot lenkeretning
            openLrReferences[2].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.46647 to 63.42433,
                    10.46041 to 63.42712,
                )
            // Første stedfesting, mot lenkeretning
            openLrReferences[3].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.45995 to 63.42732,
                    10.45458 to 63.43004,
                )
            openLrReferences.flatMap { ref -> ref.locationReferencePoints.map { it.functionalRoadClass } }.toSet() shouldBe setOf(FunctionalRoadClass.FRC_6)

            binary shouldBe
                listOf(
                    "CwdvMy0bFzLQBwIZ/vAzOy0=",
                    "CwdwQy0ajzPMBwJe/ukyfCQV",
                    "CwdxXS0aDTLcB/2iARczbBUk",
                    "CwdwLi0amDPbB/3nARAyUC0=",
                )
        }
    }

    should("convert speed limit stedfesting to OpenLR with multi-sequence NVDB data") {
        withTempDb { dbContext ->
            // Arrange
            val openLrService =
                setupOpenLrService(
                    dbContext,
                    "veglenkesekvenser-41658-2553792.json",
                    "veglenkesekvenser-42241-48174-41659.json",
                    "vegobjekt-821-568696277.json",
                    "vegobjekt-821-633410504.json",
                )
            val stedfestinger = loadStedfestinger("vegobjekt-105-85283410.json")

            // Act
            val openLrReferences = openLrService.toOpenLr(stedfestinger)
            val binary = openLrReferences.map(marshaller::marshallToBase64String)

            // Assert
            openLrReferences shouldHaveSize 2
            openLrReferences[0].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.50101 to 63.4266,
                    10.50769 to 63.42537,
                )
            openLrReferences[1].locationReferencePoints.map { it.coordinate.x to it.coordinate.y } shouldBe
                listOf(
                    10.50769 to 63.42537,
                    10.50101 to 63.4266,
                )
            openLrReferences.flatMap { ref -> ref.locationReferencePoints.map { it.functionalRoadClass } }.toSet() shouldBe setOf(FunctionalRoadClass.FRC_6)

            binary shouldBe
                listOf(
                    "Cwd3py0adjPIBgKc/4UzGg==",
                    "Cwd43i0aPTPaBv1kAHszCA==",
                )
        }
    }

    should("handle speed limits with gaps in veglenkesekvens and varied tillatt kjøreretning") {
        withTempDb { config ->
            // Arrange
            val openLrService =
                setupOpenLrService(
                    config,
                    "veglenkesekvens-365652.json",
                    "vegobjekt-821-568644314.json",
                )
            val stedfestinger = loadStedfestinger("vegobjekt-105-78712521.json")

            // Act
            val openLrReferences = openLrService.toOpenLr(stedfestinger)
            val binary = openLrReferences.map(marshaller::marshallToBase64String)

            // Assert
            openLrReferences shouldHaveSize 3
            openLrReferences.shouldForAll { ref ->
                ref.relativeNegativeOffset shouldBe 0
                ref.relativePositiveOffset shouldBe 0
                ref.locationReferencePoints.all { it.functionalRoadClass == FunctionalRoadClass.FRC_6 } shouldBe true
            }
            binary shouldBe
                listOf(
                    "CwOZ4iuf5jPeAP/dACwzDg==",
                    "CwOZwiugDTPCBgI7AKEzFw==",
                    "CwOazCugWDPXCP4K/wszHg==",
                )
        }
    }

    should("handle speed limit stedfestet MOT lenkeretning") {
        withTempDb { config ->
            // Arrange
            val openLrService =
                setupOpenLrService(
                    config,
                    "veglenkesekvenser-2518522-413032-2518519.json",
                    "vegobjekt-821-589421132.json",
                    "vegobjekt-821-568168206.json",
                    "vegobjekt-616-1020150975.json",
                )
            val stedfestinger = loadStedfestinger("vegobjekt-105-589421130.json")

            // Act
            val openLrReferences = openLrService.toOpenLr(stedfestinger)
            val binary = openLrReferences.map(marshaller::marshallToBase64String)

            // Assert
            openLrReferences shouldHaveSize 1
            openLrReferences[0].should {
                it.relativeNegativeOffset shouldBe 0
                it.relativePositiveOffset shouldBe 0
                it.locationReferencePoints shouldHaveSize 2
                it.locationReferencePoints[0].functionalRoadClass shouldBe FunctionalRoadClass.FRC_0
                it.locationReferencePoints[0].pathAttributes.get().lowestFunctionalRoadClass shouldBe FunctionalRoadClass.FRC_7
                it.locationReferencePoints[1].functionalRoadClass shouldBe FunctionalRoadClass.FRC_0
            }

            binary shouldBe listOf("Cweq+ip17APyAwB6/1kDGQ==")
        }
    }
})

private suspend fun setupOpenLrService(dbContext: RocksDbContext, vararg paths: String): OpenLrService {
    val cachedVegnett = setupCachedVegnett(dbContext, *paths)
    cachedVegnett.initialize()
    val openLrService = OpenLrService(cachedVegnett)
    return openLrService
}

private fun loadStedfestinger(path: String): List<StedfestingUtstrekning> {
    val fartsgrense = objectMapper.readJson<ApiVegobjekt>(path)
    val stedfestinger = fartsgrense.getStedfestingLinjer()
    return stedfestinger
}
