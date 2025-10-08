package no.vegvesen.nvdb.tnits.generator.storage

sealed class BatchOperation {
    class Put(val key: ByteArray, val value: ByteArray) : BatchOperation()

    class Delete(val key: ByteArray) : BatchOperation()
}
