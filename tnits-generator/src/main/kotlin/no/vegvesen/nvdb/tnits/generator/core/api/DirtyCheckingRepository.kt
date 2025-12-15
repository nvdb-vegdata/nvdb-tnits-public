package no.vegvesen.nvdb.tnits.generator.core.api

typealias VegobjektId = Long

interface DirtyCheckingRepository {
    /**
     * Retrieves direct dirty vegobjekt changes for a specific type. These are changes that were
     * explicitly marked as dirty in the current operation.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of VegobjektIDs with direct changes
     */
    fun getDirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektId>

    /**
     * Retrieves indirect dirty vegobjekt changes for a specific type. These are changes that
     * resulted from related veglenkesekvenser becoming dirty (e.g., a feature positioned on a dirty veglenke).
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of VegobjektIDs located on veglenkesekvenser with changes
     */
    fun getIndirectDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektId>

    /**
     * Retrieves dirty vegobjekt IDs, both direct and indirect, for a specific type.
     *
     * @param vegobjektType The vegobjekt type ID to check for dirty objects
     * @return Set of vegobjekt ID with changes
     */
    fun getDirtyVegobjektChanges(vegobjektType: Int): Set<VegobjektId> {
        val directChanges = getDirectDirtyVegobjektChanges(vegobjektType)
        val indirectChanges = getIndirectDirtyVegobjektChanges(vegobjektType)

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
    fun findStedfestingVegobjektIds(veglenkesekvensIds: Set<VeglenkesekvensId>, vegobjektType: Int): Set<VegobjektId>

    /**
     * Clears dirty status for specific vegobjekt IDs of a given type.
     *
     * @param vegobjektType The vegobjekt type
     * @param vegobjektIds Set of vegobjekt IDs to clear from dirty tracking
     */
    fun clearDirtyVegobjektIds(vegobjektType: Int, vegobjektIds: Set<VegobjektId>)

    fun clearAllDirtyVegobjektIds(vegobjektType: Int)

    fun clearAllDirtyVeglenkesekvenser()
}
