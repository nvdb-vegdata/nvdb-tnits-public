package no.vegvesen.nvdb.tnits.extensions

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class FlowExtensionsTest :
    ShouldSpec({

        should("split flow and both consumers receive all items") {
            // Arrange
            val sourceFlow = flow {
                repeat(10) { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(100)

            // Assert
            result1 shouldContainExactly (0..9).toList()
            result2 shouldContainExactly (0..9).toList()
        }

        should("coordinate backpressure when one consumer is slower") {
            // Arrange
            val emissionCount = AtomicInteger(0)
            val sourceFlow = flow {
                repeat(20) {
                    emit(it)
                    emissionCount.incrementAndGet()
                }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch {
                flow1.collect {
                    result1.add(it)
                    delay(10)
                }
            }

            launch {
                flow2.collect {
                    result2.add(it)
                    delay(50)
                }
            }

            delay(1200)

            // Assert
            emissionCount.get() shouldBe 20
            result1 shouldContainExactly (0..19).toList()
            result2 shouldContainExactly (0..19).toList()
        }

        should("handle empty flow") {
            // Arrange
            val sourceFlow = flow<Int> { }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)

            // Assert
            flow1.toList() shouldBe emptyList()
            flow2.toList() shouldBe emptyList()
        }

        should("handle single item flow") {
            // Arrange
            val sourceFlow = flow { emit(42) }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 1)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(50)

            // Assert
            result1 shouldContainExactly listOf(42)
            result2 shouldContainExactly listOf(42)
        }

        should("work with buffer size of 1") {
            // Arrange
            val sourceFlow = flow {
                repeat(5) { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 1)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(100)

            // Assert
            result1 shouldContainExactly (0..4).toList()
            result2 shouldContainExactly (0..4).toList()
        }

        should("work with large buffer size") {
            // Arrange
            val sourceFlow = flow {
                repeat(100) { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 1000)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(200)

            // Assert
            result1 shouldContainExactly (0..99).toList()
            result2 shouldContainExactly (0..99).toList()
        }

        should("only start collecting when first consumer starts") {
            // Arrange
            val started = AtomicInteger(0)
            val sourceFlow = flow {
                started.incrementAndGet()
                repeat(5) { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)

            delay(50)
            started.get() shouldBe 0

            val result1 = mutableListOf<Int>()
            launch { flow1.collect { result1.add(it) } }

            delay(50)

            // Assert
            started.get() shouldBe 1
            result1 shouldContainExactly (0..4).toList()
        }

        should("share single collection between both consumers") {
            // Arrange
            val collectionCount = AtomicInteger(0)
            val sourceFlow = flow {
                collectionCount.incrementAndGet()
                repeat(5) { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(100)

            // Assert
            collectionCount.get() shouldBe 1
            result1 shouldContainExactly (0..4).toList()
            result2 shouldContainExactly (0..4).toList()
        }

        should("maintain order of items in both flows") {
            // Arrange
            val sourceFlow = flow {
                listOf(5, 2, 8, 1, 9, 3, 7, 4, 6, 0).forEach { emit(it) }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 3)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch { flow1.collect { result1.add(it) } }
            launch { flow2.collect { result2.add(it) } }

            delay(100)

            // Assert
            val expected = listOf(5, 2, 8, 1, 9, 3, 7, 4, 6, 0)
            result1 shouldContainExactly expected
            result2 shouldContainExactly expected
        }

        should("stop collecting when both consumers complete") {
            // Arrange
            val collectionCount = AtomicInteger(0)
            val sourceFlow = flow {
                repeat(100) {
                    collectionCount.incrementAndGet()
                    emit(it)
                }
            }

            // Act
            val (flow1, flow2) = sourceFlow.splitBuffered(bufferSize = 5)
            val result1 = mutableListOf<Int>()
            val result2 = mutableListOf<Int>()

            launch {
                flow1.collect {
                    result1.add(it)
                    if (it >= 9) return@collect
                }
            }

            launch {
                flow2.collect {
                    result2.add(it)
                    if (it >= 9) return@collect
                }
            }

            delay(200)

            // Assert
            result1.take(10) shouldContainExactly (0..9).toList()
            result2.take(10) shouldContainExactly (0..9).toList()
            collectionCount.get() shouldBeGreaterThan 9
        }
    })
