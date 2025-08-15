package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.apiles.model.VeglenkeMedId
import no.vegvesen.nvdb.tnits.objectMapper
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

object Veglenker : Table("veglenker") {
    val veglenkesekvensId = long("veglenkesekvens_id")
    val veglenkenummer = integer("veglenkenummer")
    val data =
        jsonb<VeglenkeMedId>(
            "data",
            { objectMapper.writeValueAsString(it) },
            { objectMapper.readValue(it, VeglenkeMedId::class.java) },
        )
    val sistEndret = timestamp("sist_endret")

    override val primaryKey = PrimaryKey(veglenkesekvensId, veglenkenummer)
}

object Vegobjekter : Table("vegobjekter") {
    val vegobjektId = long("vegobjekt_id")

    override val primaryKey = PrimaryKey(vegobjektId)
}

object KeyValue : Table("key_value") {
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
