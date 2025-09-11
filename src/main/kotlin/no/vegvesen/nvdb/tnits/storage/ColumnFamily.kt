package no.vegvesen.nvdb.tnits.storage

enum class ColumnFamily(val familyName: String) {
    DEFAULT("default"),
    NODER("noder"),
    VEGLENKER("veglenker"),
    VEGOBJEKTER("vegobjekter"),
    KEY_VALUE("key_value"),
    ;

    companion object {
        fun fromName(name: String): ColumnFamily? = entries.find { it.familyName == name }

        fun allFamilies(): List<ColumnFamily> = entries
    }
}
