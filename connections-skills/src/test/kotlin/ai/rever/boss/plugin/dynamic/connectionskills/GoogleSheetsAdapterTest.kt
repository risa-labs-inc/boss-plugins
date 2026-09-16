package ai.rever.boss.plugin.dynamic.connectionskills

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoogleSheetsAdapterTest {

    private val adapter = GoogleSheetsAdapter()

    @Test
    fun unauthenticatedStatusIsRejected() {
        val output = """
            {
              "auth_method": "none",
              "credential_source": "none",
              "encrypted_credentials_exists": false,
              "plain_credentials_exists": false,
              "token_cache_exists": false
            }
        """.trimIndent()

        assertFalse(adapter.isAuthenticatedStatus(output))
    }

    @Test
    fun authenticatedEncryptedCredentialsAreAccepted() {
        val output = """
            {
              "auth_method": "oauth",
              "credential_source": "encrypted",
              "encrypted_credentials_exists": true,
              "plain_credentials_exists": false,
              "token_cache_exists": true
            }
        """.trimIndent()

        assertTrue(adapter.isAuthenticatedStatus(output))
    }

    @Test
    fun authenticatedPlainCredentialsAreAccepted() {
        val output = """
            {
              "auth_method": "oauth",
              "credential_source": "plain",
              "encrypted_credentials_exists": false,
              "plain_credentials_exists": true,
              "token_cache_exists": false
            }
        """.trimIndent()

        assertTrue(adapter.isAuthenticatedStatus(output))
    }

    @Test
    fun missingCredentialsAreRejected() {
        val output = """
            {
              "auth_method": "oauth",
              "credential_source": "none",
              "encrypted_credentials_exists": false,
              "plain_credentials_exists": false,
              "token_cache_exists": true
            }
        """.trimIndent()

        assertFalse(adapter.isAuthenticatedStatus(output))
    }
}
