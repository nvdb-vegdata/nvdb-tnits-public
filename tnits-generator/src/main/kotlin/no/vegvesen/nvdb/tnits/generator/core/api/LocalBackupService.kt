package no.vegvesen.nvdb.tnits.generator.core.api

/**
 * Service for creating and restoring backups of the local database.
 */
interface LocalBackupService {
    fun createBackup(): Boolean
    fun restoreIfNeeded()
    fun restoreFromBackup(): Boolean
}
