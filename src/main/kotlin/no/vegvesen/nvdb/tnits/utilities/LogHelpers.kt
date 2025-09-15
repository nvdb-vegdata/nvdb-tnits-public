package no.vegvesen.nvdb.tnits.utilities

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
