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
        title = "NVDB TN-ITS Export API",
        version = "v1",
        description = """
This API provides access to TN-ITS data exports from the Norwegian National Road Database (NVDB).

## Getting Started

The API uses a **snapshot + incremental update** pattern: fetch a complete dataset (snapshot) once, then apply delta changes (updates) to stay synchronized.

## Workflow

**1. Get the latest snapshot** — your starting point for a complete dataset

```
GET /api/v1/tnits/speedLimit/snapshots/latest
```

**Response:**
```json
{
  "href": "{BASE_URL}/api/v1/download?path=0105-speedLimit/2025-10-20T04-23-14Z/snapshot.xml.gz",
  "newUpdates": "{BASE_URL}/api/v1/tnits/speedLimit/updates?from=2025-10-20T04:23:14Z"
}
```

Download the snapshot file from `href` (GML 3.2 format, gzip compressed). This is your initial data state.

**2. Poll for updates** — follow the `newUpdates` link to check for changes (e.g., daily)

```
GET /api/v1/tnits/speedLimit/updates?from=2025-10-20T04:23:14Z
```

**Response:**
```json
{
  "updates": [
    {
      "href": "{BASE_URL}/api/v1/download?path=0105-speedLimit/2025-10-21T04-23-14Z/update.xml.gz",
      "timestamp": "2025-10-21T04:23:14Z",
      "size": 1024000
    }
  ],
  "newUpdates": "/api/v1/tnits/speedLimit/updates?from=2025-10-21T04:23:14Z"
}
```

Download and apply each update from the `updates[]` array in timestamp order. Keep following the `newUpdates` link to stay synchronized—it automatically advances to the latest timestamp.

**Navigation Links (HATEOAS):** The API provides hypermedia links (`newUpdates`) for navigation. Simply follow the links in responses rather than constructing URLs manually.

## Best Practices

- Poll for updates daily for most use cases
- Apply updates in timestamp order to maintain consistency
- If an update fails to apply, refetch from the latest snapshot
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
    @Value($$"${spring.security.oauth2.client.provider.oidc.issuer-uri:}") private val issuerUri: String,
    @Value($$"${app.baseUrl}") private val baseUrl: String,
) {

    @Bean
    fun publicApi(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("public")
        .displayName("Public API")
        .pathsToMatch("/api/v1/**")
        .pathsToExclude("/api/v1/admin/**")
        .addOpenApiCustomizer { openApi ->
            if (baseUrl.isNotBlank()) {
                openApi.info.description = openApi.info.description?.replace("{BASE_URL}", baseUrl)
            }
        }
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
