package no.vegvesen.nvdb.tnits.generator.core.services.storage

sealed class BatchOperation {
    class Put(val key: ByteArray, val value: ByteArray) : BatchOperation()

    class Delete(val key: ByteArray) : BatchOperation()
}
