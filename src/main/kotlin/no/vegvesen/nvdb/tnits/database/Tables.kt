package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.apiles.uberiket.Retning
import no.vegvesen.nvdb.apiles.uberiket.Sideposisjon
import no.vegvesen.nvdb.apiles.uberiket.Vegobjekt
import no.vegvesen.nvdb.tnits.database.JacksonJsonbColumnType.Companion.jacksonJsonb
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

object Vegobjekter : Table("vegobjekter") {
    val vegobjektId = long("vegobjekt_id")
    val vegobjektVersjon = integer("vegobjekt_versjon")
    val vegobjektType = integer("vegobjekt_type")
    val data = jacksonJsonb<Vegobjekt>("data")
    val sistEndret = timestampWithTimeZone("sist_endret")

    override val primaryKey = PrimaryKey(vegobjektId)

    init {
        index(isUnique = true, vegobjektType, vegobjektId)
        index(isUnique = false, sistEndret)
    }
}

object Stedfestinger : Table("vegobjekter_stedfestinger") {
    val vegobjektId = long("vegobjekt_id")
    val vegobjektType = integer("vegobjekt_type")
    val veglenkesekvensId = long("veglenkesekvens_id")
    val startposisjon = decimal("startposisjon", precision = 9, scale = 8)
    val sluttposisjon = decimal("sluttposisjon", precision = 9, scale = 8)
    val retning = enumeration<Retning>("retning").nullable()
    val sideposisjon = enumeration<Sideposisjon>("sideposisjon").nullable()
    val kjorefelt = jacksonJsonb<List<String>>("kjorefelt")

    val vegobjektFk =
        foreignKey(
            vegobjektId,
            target = Vegobjekter.primaryKey,
            onDelete = ReferenceOption.CASCADE,
        )

    init {
        index(isUnique = false, veglenkesekvensId)
        index(isUnique = false, vegobjektId)
    }
}

object KeyValue : Table("key_value") {
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}

object DirtyVeglenkesekvenser : Table("dirty_veglenkesekvenser") {
    val veglenkesekvensId = long("veglenkesekvens_id")
    val sistEndret = timestampWithTimeZone("sist_endret")

    init {
        index(isUnique = false, sistEndret)
    }
}
