package no.vegvesen.nvdb.tnits.generator.infrastructure.ingest

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import no.vegvesen.nvdb.tnits.generator.TestServices.Companion.withTestServices
import no.vegvesen.nvdb.tnits.generator.core.model.VegobjektHendelse

class VegobjektLoaderTest : ShouldSpec({

    context("receiving VegobjektVersjonFjernet events for all versions") {
        should("set ChangeType.DELETED regardless of order") {
            withTestServices(mockk()) {
                every { uberiketApi.streamVegobjektHendelser(any(), any()) } returns flowOf(
                    VegobjektHendelse(
                        hendelseId = 2,
                        vegobjektId = 123,
                        vegobjektVersjon = 1,
                    ),
                    VegobjektHendelse(
                        hendelseId = 3,
                        vegobjektId = 123,
                        vegobjektVersjon = 2,
                    ),
                )

                vegobjektLoader.getVegobjektChanges(105, 1L).changedIds shouldBe setOf(123L)
            }
        }
    }
})
