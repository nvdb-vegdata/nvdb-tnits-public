package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlin.math.floor
import kotlin.math.pow

/** Rounds a Double to the specified number of decimals, using "round half up" strategy for backwards compatibility with V3 */
fun Double.toRounded(decimals: Int): Double {
    val pow = 10.0.pow(decimals)
    val shifted = this * pow
    return floor(shifted + 0.5) / pow
}
