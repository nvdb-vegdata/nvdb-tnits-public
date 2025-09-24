package no.vegvesen.vt.nvdb.tnits.generator

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

fun main() {
    println("Hello World! The time is: ${LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)}")
}
