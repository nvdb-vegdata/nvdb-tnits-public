package no.vegvesen.nvdb.tnits.generator.core.services.storage

enum class ColumnFamily(val familyName: String) {
    DEFAULT("default"),
    VEGLENKER("veglenker"),
    VEGOBJEKTER("vegobjekter"),
    KEY_VALUE("key_value"),
    DIRTY_VEGLENKESEKVENSER("dirty_veglenkesekvenser"),
    DIRTY_VEGOBJEKTER("dirty_vegobjekter"),
    EXPORTED_FEATURES("exported_features"),
    ;

    companion object {
        fun fromName(name: String): ColumnFamily? = entries.find { it.familyName == name }

        fun allFamilies(): List<ColumnFamily> = entries
    }
}
