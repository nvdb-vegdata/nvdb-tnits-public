package no.vegvesen.nvdb.tnits.generator.storage

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class ColumnFamilyTest :
    ShouldSpec({

        should("have correct family names") {
            ColumnFamily.DEFAULT.familyName shouldBe "default"
            ColumnFamily.VEGLENKER.familyName shouldBe "veglenker"
        }

        should("find column family by name") {
            ColumnFamily.fromName("default") shouldBe ColumnFamily.DEFAULT
            ColumnFamily.fromName("veglenker") shouldBe ColumnFamily.VEGLENKER
            ColumnFamily.fromName("unknown") shouldBe null
        }
    })
