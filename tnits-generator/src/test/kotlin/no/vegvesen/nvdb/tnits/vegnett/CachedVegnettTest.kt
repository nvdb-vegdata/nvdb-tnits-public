package no.vegvesen.nvdb.tnits.vegnett

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import no.vegvesen.nvdb.tnits.openlr.TempRocksDbConfig.Companion.withTempDb
import no.vegvesen.nvdb.tnits.openlr.TillattRetning
import no.vegvesen.nvdb.tnits.setupCachedVegnett

class CachedVegnettTest :
    ShouldSpec({

        should("correctly build vegnett where veglenker have opposite start and end nodes") {
            withTempDb { dbContext ->
                val cachedVegnett = setupCachedVegnett(dbContext, "veglenkesekvenser-1901376-1901377-1901381-1901382.json")

                cachedVegnett.initialize()

                cachedVegnett.getNode(1901475, TillattRetning.Med) { TODO() }.valid shouldBe true
                cachedVegnett.getNode(1901474, TillattRetning.Med) { TODO() }.valid shouldBe true
                cachedVegnett.getNode(1901475, TillattRetning.Mot) { TODO() }.valid shouldBe true
                cachedVegnett.getNode(1901474, TillattRetning.Mot) { TODO() }.valid shouldBe true
            }
        }
    })
