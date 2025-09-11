package no.vegvesen.nvdb.tnits.storage

interface DirtyVeglenkesekvenserRepository {
    fun publishChangedVeglenkesekvensIds(changedIds: Collection<Long>)
}
