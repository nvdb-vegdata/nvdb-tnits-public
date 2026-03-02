package no.vegvesen.nvdb.tnits.katalog.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class InstantConverterTest : ShouldSpec({
    should("parse full ISO instant unchanged") {
        val source = "2025-02-01T00:00:00Z"

        val result = InstantConverter.convert(source)

        result shouldBe Instant.parse("2025-02-01T00:00:00Z")
    }

    should("parse ISO timestamp with offset unchanged as instant") {
        val source = "2025-02-01T00:00:00+01:00"

        val result = InstantConverter.convert(source)

        result shouldBe Instant.parse("2025-01-31T23:00:00Z")
    }

    should("parse winter date at Oslo midnight") {
        val source = "2025-02-01"

        val result = InstantConverter.convert(source)

        result shouldBe Instant.parse("2025-01-31T23:00:00Z")
    }

    should("parse summer date at Oslo midnight") {
        val source = "2025-07-01"

        val result = InstantConverter.convert(source)

        result shouldBe Instant.parse("2025-06-30T22:00:00Z")
    }

    should("throw IllegalArgumentException for invalid date format") {
        val source = "2025-02-30-not-valid"

        shouldThrow<IllegalArgumentException> {
            InstantConverter.convert(source)
        }
    }
})
