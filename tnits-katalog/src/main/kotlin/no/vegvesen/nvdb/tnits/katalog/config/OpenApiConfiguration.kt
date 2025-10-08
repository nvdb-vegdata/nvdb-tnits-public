package no.vegvesen.nvdb.tnits.katalog.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "NVDB TN-ITS & INSPIRE Export API",
        version = "v1",
        description = "This API provides access to TN-ITS and INSPIRE data exports from the Norwegian National Road Database (NVDB).",
        contact = Contact(
            name = "Nasjonal vegdatabank (NVDB)",
            url = "https://nvdb.atlas.vegvesen.no/",
            email = "nvdb@vegvesen.no",
        ),
        license = License(
            name = "NLOD 1.0",
            url = "https://data.norge.no/nlod/no/1.0",
        ),
    ),
    servers = [Server(url = "/", description = "Default Server URL")],
)
class OpenApiConfiguration
