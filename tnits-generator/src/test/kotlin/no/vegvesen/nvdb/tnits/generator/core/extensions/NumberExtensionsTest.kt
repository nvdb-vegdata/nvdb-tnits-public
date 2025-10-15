package no.vegvesen.nvdb.tnits.generator.core.extensions

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class NumberExtensionsTest :
    StringSpec({
        "toFormattedString should format very small numbers without scientific notation" {
            val value = 0.000308
            val formatted = value.toFormattedString(8)

            formatted shouldBe "0.000308"
        }

        "toFormattedString should round to specified decimals" {
            val value = 0.123456789
            val formatted = value.toFormattedString(5)

            formatted shouldBe "0.12346"
        }

        "toFormattedString should handle whole numbers" {
            val value = 1.0
            val formatted = value.toFormattedString(8)

            formatted shouldBe "1"
        }

        "toFormattedString should strip trailing zeros" {
            val value = 1.50000000
            val formatted = value.toFormattedString(8)

            formatted shouldBe "1.5"
        }

        "toFormattedString should handle negative numbers" {
            val value = -0.000308
            val formatted = value.toFormattedString(8)

            formatted shouldBe "-0.000308"
        }
    })
