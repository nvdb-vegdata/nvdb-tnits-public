package no.vegvesen.nvdb.tnits.openlr

import no.vegvesen.nvdb.tnits.storage.RocksDbConfiguration
import java.io.File
import java.nio.file.Files

class TempRocksDbConfig : RocksDbConfiguration(Files.createTempDirectory("openlr-test").toString()) {
    override fun close() {
        super.close()
        File(dbPath).deleteRecursively()
    }

    companion object {
        fun withTempDb(block: (RocksDbConfiguration) -> Unit) {
            TempRocksDbConfig().use { config ->
                block(config)
            }
        }
    }
}
