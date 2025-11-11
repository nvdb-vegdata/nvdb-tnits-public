package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import no.vegvesen.nvdb.apiles.uberiket.VegobjektNotifikasjon
import no.vegvesen.nvdb.tnits.generator.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType

class VegobjektLoaderTest : ShouldSpec({

    context("receiving VegobjektVersjonFjernet events for all versions") {
        should("set ChangeType.DELETED regardless of order") {
            withTestServices(mockk()) {
                every { uberiketApi.streamVegobjektHendelser(any(), any()) } returns flowOf(
                    VegobjektNotifikasjon().apply {
                        hendelseId = 2L
                        vegobjektId = 123L
                        vegobjektVersjon = 1
                        hendelseType = "VegobjektVersjonFjernet"
                    },
                    VegobjektNotifikasjon().apply {
                        hendelseId = 3L
                        vegobjektId = 123L
                        vegobjektVersjon = 2
                        hendelseType = "VegobjektVersjonFjernet"
                    },
                )

                vegobjektLoader.getVegobjektChanges(105, 1L).changesById shouldBe mapOf(
                    123L to ChangeType.DELETED,
                )
            }
        }
    }
})
