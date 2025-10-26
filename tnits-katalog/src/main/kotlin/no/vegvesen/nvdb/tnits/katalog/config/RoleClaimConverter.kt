package no.vegvesen.nvdb.tnits.katalog.config

import org.springframework.security.oauth2.jwt.Jwt

object RoleClaimConverter {
    /**
     * Extracts roles from a JWT based on a given claim path, handling nested claims (e.g. "realm_access.roles").
     */
    fun extractRoles(jwt: Jwt, claimPath: String): List<String> {
        val parts = claimPath.split(".")
        var current: Any? = jwt.claims

        for (part in parts) {
            current = (current as? Map<*, *>)?.get(part) ?: return emptyList()
        }

        return when (current) {
            is List<*> -> current.filterIsInstance<String>()
            is String -> listOf(current)
            else -> emptyList()
        }
    }
}
