package no.vegvesen.nvdb.tnits.generator.storage

enum class ColumnFamily(val familyName: String) {
    DEFAULT("default"),
    VEGLENKER("veglenker"),
    VEGOBJEKTER("vegobjekter"),
    KEY_VALUE("key_value"),
    DIRTY_VEGLENKESEKVENSER("dirty_veglenkesekvenser"),
    DIRTY_VEGOBJEKTER("dirty_vegobjekter"),
    VEGOBJEKTER_HASH("vegobjekter_hash"),
    EXPORTED_FEATURES("exported_features"),
    ;

    companion object {
        fun fromName(name: String): ColumnFamily? = entries.find { it.familyName == name }

        fun allFamilies(): List<ColumnFamily> = entries
    }
}
