package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.tnits.storage.RocksDbContext
import java.io.File
import java.nio.file.Files

class TempRocksDbConfig : RocksDbContext(Files.createTempDirectory("openlr-test").toString()) {
    override fun close() {
        super.close()
        File(dbPath).deleteRecursively()
    }

    companion object {
        inline fun withTempDb(block: (RocksDbContext) -> Unit) {
            TempRocksDbConfig().use { config ->
                block(config)
            }
        }
    }
}
