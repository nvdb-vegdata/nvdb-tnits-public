package no.vegvesen.nvdb.tnits.katalog.config

import io.swagger.v3.oas.annotations.ExternalDocumentation
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.servers.Server
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "NVDB TN-ITS & INSPIRE Export API",
        version = "v1",
        description = """
This API provides access to TN-ITS and INSPIRE data exports from the Norwegian National Road Database (NVDB).

For a web-based viewer to explore the exported TN-ITS data, visit the [TN-ITS Export Viewer](https://nvdb-vegdata.github.io/nvdb-tnits-public/).
        """,
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
    externalDocs = ExternalDocumentation(
        description = "TN-ITS Export Viewer",
        url = "https://nvdb-vegdata.github.io/nvdb-tnits-public/",
    ),
)
class OpenApiConfiguration(
    @Value("\${spring.security.oauth2.client.provider.oidc.issuer-uri:}") private val issuerUri: String,
) {

    @Bean
    fun publicApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("public")
        .displayName("Public API")
        .pathsToMatch("/api/v1/**")
        .pathsToExclude("/api/v1/admin/**")
        .build()

    @Bean
    fun adminApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("admin")
        .displayName("Admin API")
        .pathsToMatch("/api/v1/admin/**")
        .addOpenApiCustomizer { openApi ->
            if (issuerUri.isNotBlank()) {
                val oauth2Scheme = SecurityScheme()
                    .type(SecurityScheme.Type.OPENIDCONNECT)
                    .flows(
                        OAuthFlows()
                            .authorizationCode(
                                OAuthFlow()
                                    .authorizationUrl("$issuerUri/protocol/openid-connect/auth")
                                    .tokenUrl("$issuerUri/protocol/openid-connect/token")
                                    .scopes(
                                        Scopes()
                                            .addString("openid", "OpenID Connect scope")
                                            .addString("profile", "Profile information"),
                                    ),
                            ),
                    )
                openApi.components.addSecuritySchemes("oauth2", oauth2Scheme)
            }
        }
        .build()
}
