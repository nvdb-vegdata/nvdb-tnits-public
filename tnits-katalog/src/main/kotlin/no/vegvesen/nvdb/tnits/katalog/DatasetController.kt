package no.vegvesen.nvdb.tnits.katalog

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/v1/datasets"])
class DatasetController {

    @GetMapping
    fun listDatasets() = "Hello World!"
}

data class DatasetsResponse(val datasets: List<Dataset>) {
    data class Dataset(val type: Int, val name: String, val href: String)
}
