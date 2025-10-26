package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType
import no.vegvesen.nvdb.tnits.generator.core.services.storage.VegobjektChange

interface DirtyCheckingRepository {
    /**
     * Retrieves all dirty vegobjekt changes for a specific type. Includes both direct changes
     * to vegobjekter and indirect changes due to related veglenkesekvenser becoming dirty.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of VegobjektChange objects containing ID and change type information
     */
    fun getDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektChange>

    /**
     * Retrieves dirty vegobjekt changes as a map, with deduplication logic that prioritizes
     * NEW changes over other change types when the same feature ID has multiple changes.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Map of vegobjekt ID to ChangeType, with NEW prioritized over MODIFIED
     */
    fun getDirtyVegobjektChangesAsMap(vegobjektType: Int): Map<Long, ChangeType> {
        val changes = getDirtyVegobjektChanges(vegobjektType)
        return changes.groupBy({ it.id }, { it.changeType })
            .mapValues { (_, changesForId) ->
                when {
                    changesForId.contains(ChangeType.NEW) -> ChangeType.NEW
                    changesForId.contains(ChangeType.DELETED) -> ChangeType.DELETED
                    else -> ChangeType.MODIFIED
                }
            }
    }

    /**
     * Finds vegobjekt IDs of a specific type that are positioned (stedfestet) on the given veglenkesekvenser.
     * This is useful for finding which vegobjekter are affected when veglenkesekvenser become dirty.
     *
     * @param veglenkesekvensIds Set of veglenkesekvens IDs to check
     * @param vegobjektType The vegobjekt type to find positioned objects for
     * @return Set of vegobjekt IDs that are positioned on any of the given veglenkesekvenser
     */
    fun findStedfestingVegobjektIds(veglenkesekvensIds: Set<Long>, vegobjektType: Int): Set<Long>

    /**
     * Clears dirty status for specific vegobjekt IDs of a given type.
     *
     * @param vegobjektType The vegobjekt type
     * @param vegobjektIds Set of vegobjekt IDs to clear from dirty tracking
     */
    fun clearDirtyVegobjektIds(vegobjektType: Int, vegobjektIds: Set<Long>)

    fun clearAllDirtyVegobjektIds(vegobjektType: Int)

    fun clearAllDirtyVeglenkesekvenser()
}
