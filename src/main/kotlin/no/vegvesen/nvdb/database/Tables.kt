package no.vegvesen.nvdb.database

import org.jetbrains.exposed.v1.core.Table

object Veglenker : Table("veglenker") {
    val veglenkesekvensId = long("veglenkesekvens_id")
    val veglenkenummer = integer("veglenkenummer")

    override val primaryKey = PrimaryKey(veglenkesekvensId, veglenkenummer)
}

object Vegobjekter : Table("vegobjekter") {
    val vegobjektId = long("vegobjekt_id")

    override val primaryKey = PrimaryKey(vegobjektId)
}
