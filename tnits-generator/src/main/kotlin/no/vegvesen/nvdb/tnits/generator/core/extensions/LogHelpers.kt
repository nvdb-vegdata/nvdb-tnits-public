package no.vegvesen.nvdb.tnits.generator.core.extensions

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import kotlin.reflect.full.companionObject
import kotlin.time.measureTime

fun <T : Any> getClassForLogging(javaClass: Class<T>): Class<*> = javaClass.enclosingClass?.takeIf { it.kotlin.companionObject?.java == javaClass } ?: javaClass

interface WithLogger {
    val log: Logger get() = LoggerFactory.getLogger(getClassForLogging(javaClass))
}

inline fun <T> Logger.measure(label: String, logStart: Boolean = false, level: Level = Level.INFO, block: () -> T): T {
    val logger = atLevel(level)
    if (logStart) {
        logger.log("Start: $label")
    }

    var result: T
    val time = measureTime { result = block() }
    logger.log("${if (logStart) "End: " else "Timed: "}$label, time: $time")
    return result
}

fun Logger.logMemoryUsage(label: String) {
    System.gc()
    val runtime = Runtime.getRuntime()
    val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val maxMemoryMB = runtime.maxMemory() / 1024 / 1024
    val percentUsed = (usedMemoryMB * 100.0 / maxMemoryMB).toInt()
    info("$label - Memory: ${usedMemoryMB}MB / ${maxMemoryMB}MB ($percentUsed%)")
}
