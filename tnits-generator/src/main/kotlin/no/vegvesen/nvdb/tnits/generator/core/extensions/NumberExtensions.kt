package no.vegvesen.nvdb.tnits.generator.core.extensions

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.pow

/** Rounds a Double to the specified number of decimals, using "round half up" strategy for backwards compatibility with V3 */
fun Double.toRounded(decimals: Int): Double {
    val pow = 10.0.pow(decimals)
    val shifted = this * pow
    return floor(shifted + 0.5) / pow
}

/** Formats a Double as a string with the specified number of decimals, avoiding scientific notation */
fun Double.toFormattedString(decimals: Int): String = BigDecimal(this).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
