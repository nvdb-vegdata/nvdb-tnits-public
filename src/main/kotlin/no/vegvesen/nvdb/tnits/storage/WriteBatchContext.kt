package no.vegvesen.nvdb.tnits.storage

import java.util.concurrent.ConcurrentHashMap

class WriteBatchContext {
    private val operations = ConcurrentHashMap<ColumnFamily, MutableList<BatchOperation>>()

    fun write(columnFamily: ColumnFamily, operation: BatchOperation) {
        operations.computeIfAbsent(columnFamily) { mutableListOf() }
            .add(operation)
    }

    fun write(columnFamily: ColumnFamily, ops: List<BatchOperation>) {
        operations.computeIfAbsent(columnFamily) { mutableListOf() }
            .addAll(ops)
    }

    fun getOperations(): Map<ColumnFamily, List<BatchOperation>> = operations

    companion object {
        inline operator fun invoke(block: WriteBatchContext.() -> Unit): Map<ColumnFamily, List<BatchOperation>> {
            val dsl = WriteBatchContext()
            dsl.block()
            return dsl.getOperations()
        }
    }
}
