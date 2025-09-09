package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ColumnFamilyTest :
    StringSpec({

        "should have correct family names" {
            ColumnFamily.DEFAULT.familyName shouldBe "default"
            ColumnFamily.NODER.familyName shouldBe "noder"
            ColumnFamily.VEGLENKER.familyName shouldBe "veglenker"
        }

        "should find column family by name" {
            ColumnFamily.fromName("default") shouldBe ColumnFamily.DEFAULT
            ColumnFamily.fromName("noder") shouldBe ColumnFamily.NODER
            ColumnFamily.fromName("veglenker") shouldBe ColumnFamily.VEGLENKER
            ColumnFamily.fromName("unknown") shouldBe null
        }
    })
