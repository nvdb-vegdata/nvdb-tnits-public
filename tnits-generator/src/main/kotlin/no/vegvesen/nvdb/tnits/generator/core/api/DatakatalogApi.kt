package no.vegvesen.nvdb.tnits.generator.core.api

interface DatakatalogApi {
    suspend fun getKmhByEgenskapVerdi(): Map<Int, Int>

    suspend fun getVegkategoriByEgenskapVerdi(): Map<Int, String>

    suspend fun getVegfaseByEgenskapVerdi(): Map<Int, String>
}
