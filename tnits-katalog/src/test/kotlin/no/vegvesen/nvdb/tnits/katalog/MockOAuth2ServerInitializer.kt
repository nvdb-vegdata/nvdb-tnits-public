package no.vegvesen.nvdb.tnits.katalog

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.support.GenericApplicationContext
import org.springframework.context.support.registerBean

/**
 * necessary in order to create and start the server before the ApplicationContext is initialized, due to
 * the spring boot oauth2 resource server dependency invoking the server on application context creation.
 */
class MockOAuth2ServerInitializer : ApplicationContextInitializer<GenericApplicationContext> {
    override fun initialize(applicationContext: GenericApplicationContext) {
        val server = registerMockOAuth2Server(applicationContext)
        val baseUrl = server.baseUrl().toString().replace("/$".toRegex(), "")
        TestPropertyValues.of(mapOf(MOCK_OAUTH_2_SERVER_BASE_URL to baseUrl))
            .applyTo(applicationContext)
    }

    private fun registerMockOAuth2Server(applicationContext: GenericApplicationContext): MockOAuth2Server {
        val server = MockOAuth2Server()
        server.start()
        applicationContext.apply {
            registerBean { server }
        }
        return server
    }

    companion object {
        const val MOCK_OAUTH_2_SERVER_BASE_URL = "mock-oauth2-server.baseUrl"
    }
}
