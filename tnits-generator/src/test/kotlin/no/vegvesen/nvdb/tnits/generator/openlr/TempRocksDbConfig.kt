package no.vegvesen.nvdb.tnits.generator.openlr

import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.RocksDbContext
import java.io.File
import java.nio.file.Files

class TempRocksDbConfig : RocksDbContext(Files.createTempDirectory("rocksdb-test").toString()) {
    private var preserveOnClose = false

    fun setPreserveOnClose(preserve: Boolean) {
        preserveOnClose = preserve
    }

    override fun close() {
        super.close()
        if (!preserveOnClose) {
            File(dbPath).deleteRecursively()
        }
    }

    companion object {
        inline fun withTempDb(block: (TempRocksDbConfig) -> Unit) {
            TempRocksDbConfig().use { config ->
                block(config)
            }
        }
    }
}
