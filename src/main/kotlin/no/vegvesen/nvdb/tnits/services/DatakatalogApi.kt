package no.vegvesen.nvdb.tnits.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import no.vegvesen.nvdb.apiles.datakatalog.Vegobjekttype

class DatakatalogApi(
    private val httpClient: HttpClient,
) {
    suspend fun getVegobjekttype(typeId: Int): Vegobjekttype =
        httpClient
            .get("vegobjekttyper/$typeId")
            .body()
}
