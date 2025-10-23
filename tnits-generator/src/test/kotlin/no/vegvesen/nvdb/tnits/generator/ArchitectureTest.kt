package no.vegvesen.nvdb.tnits.generator

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import io.kotest.core.spec.style.ShouldSpec

class ArchitectureTest : ShouldSpec({
    should("enforce core layer does not depend on infrastructure") {
        Konsist.scopeFromProduction()
            .assertArchitecture {
                val core = Layer("Core", "no.vegvesen.nvdb.tnits.generator.core..")
                val infrastructure = Layer("Infrastructure", "no.vegvesen.nvdb.tnits.generator.infrastructure..")

                core.doesNotDependOn(infrastructure)
            }
    }
})
