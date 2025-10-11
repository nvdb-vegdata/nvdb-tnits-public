package no.vegvesen.nvdb.tnits.generator.core.services.storage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class WriteBatchContext {
    private val operations = ConcurrentHashMap<ColumnFamily, MutableList<BatchOperation>>()

    fun write(columnFamily: ColumnFamily, operation: BatchOperation) {
        operations.computeIfAbsent(columnFamily) { CopyOnWriteArrayList() }
            .add(operation)
    }

    fun write(columnFamily: ColumnFamily, ops: List<BatchOperation>) {
        operations.computeIfAbsent(columnFamily) { CopyOnWriteArrayList() }
            .addAll(ops)
    }

    fun getOperations(): Map<ColumnFamily, List<BatchOperation>> = operations

    companion object {
        inline fun collectOperations(block: WriteBatchContext.() -> Unit): Map<ColumnFamily, List<BatchOperation>> {
            val context = WriteBatchContext()
            context.block()
            return context.getOperations()
        }
    }
}
