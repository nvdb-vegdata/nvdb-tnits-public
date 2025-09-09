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

        "should return all families" {
            val allFamilies = ColumnFamily.allFamilies()
            allFamilies.size shouldBe 3
            allFamilies shouldBe listOf(ColumnFamily.DEFAULT, ColumnFamily.NODER, ColumnFamily.VEGLENKER)
        }

        "should return all family names" {
            val allNames = ColumnFamily.allFamilyNames()
            allNames.size shouldBe 3
            allNames shouldBe listOf("default", "noder", "veglenker")
        }
    })
