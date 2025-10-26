package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["security.enabled"], havingValue = "false", matchIfMissing = false)
@EnableAutoConfiguration(
    exclude = [
        OAuth2ClientAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
        SecurityAutoConfiguration::class,
        ManagementWebSecurityAutoConfiguration::class,
    ],
)
class DisableOAuth2Configuration
