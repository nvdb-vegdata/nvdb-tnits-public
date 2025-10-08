package no.vegvesen.nvdb.tnits.generator.extensions

import kotlin.math.pow
import kotlin.math.round

fun Double.toRounded(decimals: Int): Double {
    val pow = 10.0.pow(decimals)
    return round(this * pow) / pow
}
