package no.vegvesen.nvdb.tnits.katalog

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller {

    @GetMapping
    fun getHello() = "Hello World!"
}
