package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.apiles.uberiket.*
import no.vegvesen.nvdb.tnits.database.GeometryWkbColumnType.Companion.geometryWkb
import no.vegvesen.nvdb.tnits.database.JacksonJsonbColumnType.Companion.jacksonJsonb
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

object Veglenker : Table("veglenker") {
    val veglenkesekvensId = long("veglenkesekvens_id")
    val veglenkenummer = integer("veglenkenummer")
    val sistEndret = timestamp("sist_endret")
    val startdato = date("startdato")
    val sluttdato = date("sluttdato")
    val startposisjon = decimal("startposisjon", precision = 9, scale = 8)
    val sluttposisjon = decimal("sluttposisjon", precision = 9, scale = 8)
    val geometri = geometryWkb("geometri")
    val startnode = long("startnode")
    val sluttnode = long("sluttnode")
    val typeVeg = enumeration<TypeVeg>("type_veg")
    val detaljniva = enumeration<Detaljniva>("detaljniva")
    val superstedfestingId = long("superstedfesting_id").nullable()
    val superstedfestingStartposisjon =
        decimal("superstedfesting_startposisjon", precision = 9, scale = 8).nullable()
    val superstedfestingSluttposisjon =
        decimal("superstedfesting_sluttposisjon", precision = 9, scale = 8).nullable()
    val superstedfestingKjorefelt = jacksonJsonb<List<String>>("superstedfesting_kjorefelt").nullable()

    override val primaryKey = PrimaryKey(veglenkesekvensId, veglenkenummer)

    init {
        index(isUnique = false, sistEndret)
        index(isUnique = false, startnode)
        index(isUnique = false, sluttnode)
    }
}

object Vegobjekter : Table("vegobjekter") {
    val vegobjektId = long("vegobjekt_id")
    val vegobjektVersjon = integer("vegobjekt_versjon")
    val vegobjektType = integer("vegobjekt_type")
    val data = jacksonJsonb<Vegobjekt>("data")
    val sistEndret = timestamp("sist_endret")
//    val startdato = date("startdato")
//    val sluttdato = date("sluttdato")

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
    val sistEndret = timestamp("sist_endret")

    init {
        index(isUnique = false, sistEndret)
    }
}
