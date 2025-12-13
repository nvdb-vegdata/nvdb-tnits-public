package no.vegvesen.nvdb.tnits.generator.core.extensions

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Splits a single upstream [Flow] into two synchronized downstream flows, broadcasting each element to both.
 *
 * Manages upstream collection lifecycle: collects when at least one consumer is active, cancels when none remain.
 * Ensures thread safety with [Mutex] and uses [Channel] buffers for distribution. Downstream flows will be synchronized to within [bufferSize].
 *
 * @param bufferSize Buffer size for intermediary [Channel]s.
 */
private class FlowSplitter<T>(
    private val source: Flow<T>,
    bufferSize: Int,
) {
    private val channel1 = Channel<T>(bufferSize)
    private val channel2 = Channel<T>(bufferSize)
    private var sharedScope: CoroutineScope? = null
    private val activeConsumers = AtomicInteger(0)
    private val mutex = Mutex()

    private suspend fun onConsumerStart(consumerScope: CoroutineScope) {
        mutex.withLock {
            if (activeConsumers.getAndIncrement() == 0) {
                sharedScope = CoroutineScope(consumerScope.coroutineContext + SupervisorJob())
                sharedScope!!.launch {
                    try {
                        source.collect { item ->
                            channel1.send(item)
                            channel2.send(item)
                        }
                    } finally {
                        channel1.close()
                        channel2.close()
                    }
                }
            }
        }
    }

    private suspend fun onConsumerEnd() {
        mutex.withLock {
            if (activeConsumers.decrementAndGet() == 0) {
                sharedScope?.cancel()
                sharedScope = null
            }
        }
    }

    val flow1 = flow {
        coroutineScope {
            onConsumerStart(this)
            try {
                for (item in channel1) {
                    emit(item)
                }
            } finally {
                onConsumerEnd()
            }
        }
    }

    val flow2 = flow {
        coroutineScope {
            onConsumerStart(this)
            try {
                for (item in channel2) {
                    emit(item)
                }
            } finally {
                onConsumerEnd()
            }
        }
    }
}

fun <T> Flow<T>.splitBuffered(bufferSize: Int = 64): Pair<Flow<T>, Flow<T>> {
    require(bufferSize > 0) { "bufferSize must be > 0" }
    val splitter = FlowSplitter(this, bufferSize)
    return Pair(splitter.flow1, splitter.flow2)
}
