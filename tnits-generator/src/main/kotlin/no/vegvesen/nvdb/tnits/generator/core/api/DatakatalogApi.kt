package no.vegvesen.nvdb.tnits.generator.core.api

interface DatakatalogApi {
    suspend fun getKmhByEgenskapVerdi(): Map<Int, Int>
}
