package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties::class)
@ConditionalOnProperty(prefix = "security", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SecurityConfiguration(
    private val securityProperties: SecurityProperties,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/api/v1/admin/**", hasRole(securityProperties.adminRole))
                authorize(anyRequest, permitAll)
            }
            oauth2ResourceServer {
                jwt {
                    jwtAuthenticationConverter = jwtAuthenticationConverter()
                }
            }
            headers {
                frameOptions { sameOrigin = true }
            }
        }
        return http.build()
    }

    private fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { jwt ->
            val roles = RoleClaimConverter.extractRoles(jwt, securityProperties.roleClaimPath)
            roles.map { role ->
                val roleWithPrefix = if (role.startsWith("ROLE_")) role else "ROLE_$role"
                SimpleGrantedAuthority(roleWithPrefix)
            }
        }
    }
}
