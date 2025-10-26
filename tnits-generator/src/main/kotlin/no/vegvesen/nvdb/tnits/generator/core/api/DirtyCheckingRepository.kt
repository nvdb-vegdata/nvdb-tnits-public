package no.vegvesen.nvdb.tnits.generator.core.api

import no.vegvesen.nvdb.tnits.generator.core.model.ChangeType
import no.vegvesen.nvdb.tnits.generator.core.services.storage.VegobjektChange

interface DirtyCheckingRepository {
    /**
     * Retrieves direct dirty vegobjekt changes for a specific type. These are changes that were
     * explicitly marked as dirty in the current operation.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of VegobjektChange objects containing ID and change type information
     */
    fun getDirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektChange>

    /**
     * Retrieves indirect dirty vegobjekt changes for a specific type. These are changes that
     * resulted from related veglenkesekvenser becoming dirty (e.g., a feature positioned on a dirty veglenke).
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of VegobjektChange objects containing ID and ChangeType.MODIFIED.
     */
    fun getIndirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektChange>

    /**
     * Retrieves dirty vegobjekt changes as a map, with deduplication logic that prioritizes
     * direct changes over indirect changes. When a feature has both direct and indirect changes,
     * the direct change type is used.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Map of vegobjekt ID to ChangeType, with direct changes prioritized
     */
    fun getDirtyVegobjektChangesAsMap(vegobjektType: Int): Map<Long, ChangeType> {
        val directChanges = getDirectDirtyVegobjektChanges(vegobjektType).associate { it.id to it.changeType }
        val indirectChanges = getIndirectDirtyVegobjektChanges(vegobjektType).associate { it.id to it.changeType }

        // Direct changes take precedence over indirect changes
        return indirectChanges + directChanges
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
