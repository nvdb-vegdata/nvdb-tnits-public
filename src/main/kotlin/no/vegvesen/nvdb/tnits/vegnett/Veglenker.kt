package no.vegvesen.nvdb.tnits.vegnett

import no.vegvesen.nvdb.tnits.database.DirtyVeglenkesekvenser
import no.vegvesen.nvdb.tnits.extensions.nowOffsetDateTime
import org.jetbrains.exposed.v1.jdbc.batchInsert
import kotlin.time.Clock

fun publishChangedVeglenkesekvensIds(changedIds: Collection<Long>) {
    DirtyVeglenkesekvenser.batchInsert(changedIds) {
        this[DirtyVeglenkesekvenser.veglenkesekvensId] = it
        this[DirtyVeglenkesekvenser.sistEndret] =
            Clock.System.nowOffsetDateTime()
    }
}
