package no.vegvesen.nvdb.tnits.database

import no.vegvesen.nvdb.apiles.model.Retning
import no.vegvesen.nvdb.apiles.model.Sideposisjon
import no.vegvesen.nvdb.apiles.model.VeglenkeMedId
import no.vegvesen.nvdb.apiles.model.Vegobjekt
import no.vegvesen.nvdb.tnits.database.JacksonJsonbColumnType.Companion.jacksonJsonb
import no.vegvesen.nvdb.tnits.objectMapper
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.JsonColumnType
import kotlin.reflect.KClass

class JacksonJsonbColumnType<T : Any>(
    clazz: KClass<T>,
) : JsonColumnType<T>({
        objectMapper.writeValueAsString(it)
    }, {
        objectMapper.readValue(it, clazz.java)
    }) {
    override val usesBinaryFormat: Boolean = true

    override fun sqlType(): String =
        when (currentDialect) {
            is H2Dialect -> (currentDialect as H2Dialect).originalDataTypeProvider.jsonBType()
            else -> currentDialect.dataTypeProvider.jsonBType()
        }

    companion object {
        inline fun <reified T : Any> jacksonJsonb(name: String): JacksonJsonbColumnType<T> = JacksonJsonbColumnType(T::class)
    }
}

object Veglenker : Table("veglenker") {
    val veglenkesekvensId = long("veglenkesekvens_id")
    val veglenkenummer = integer("veglenkenummer")
    val data = jacksonJsonb<VeglenkeMedId>("data")
    val sistEndret = timestamp("sist_endret")

    override val primaryKey = PrimaryKey(veglenkesekvensId, veglenkenummer)
}

object Vegobjekter : Table("vegobjekter") {
    val vegobjektId = long("vegobjekt_id")
    val vegobjektVersjon = integer("vegobjekt_versjon")
    val vegobjektType = integer("vegobjekt_type")
    val data = jacksonJsonb<Vegobjekt>("data")
    val sistEndret = timestamp("sist_endret")

    override val primaryKey = PrimaryKey(vegobjektId, vegobjektVersjon)
}

object Stedfestinger : Table("vegobjekter_stedfestinger") {
    val vegobjektId = long("vegobjekt_id")
    val vegobjektVersjon = integer("vegobjekt_versjon")
    val vegobjektType = integer("vegobjekt_type")
    val veglenkesekvensId = long("veglenkesekvens_id")
    val startposisjon = decimal("startposisjon", precision = 9, scale = 8)
    val sluttposisjon = decimal("sluttposisjon", precision = 9, scale = 8)
    val retning = enumeration<Retning>("retning").nullable()
    val sideposisjon = enumeration<Sideposisjon>("sideposisjon").nullable()
    val kjorefelt = jacksonJsonb<List<String>>("kjorefelt")
    val hash = long("hash")

    override val primaryKey =
        PrimaryKey(
            vegobjektId,
            vegobjektVersjon,
            veglenkesekvensId,
            hash,
        )
}

object KeyValue : Table("key_value") {
    val key = varchar("key", 255)
    val value = text("value")

    override val primaryKey = PrimaryKey(key)
}
