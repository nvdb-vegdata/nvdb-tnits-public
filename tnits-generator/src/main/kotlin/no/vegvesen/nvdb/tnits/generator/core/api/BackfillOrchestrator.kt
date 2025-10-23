package no.vegvesen.nvdb.tnits.generator.core.api

interface BackfillOrchestrator {
    suspend fun performBackfill(): Int
}

interface UpdateOrchestrator {
    suspend fun performUpdate(): Int
}
