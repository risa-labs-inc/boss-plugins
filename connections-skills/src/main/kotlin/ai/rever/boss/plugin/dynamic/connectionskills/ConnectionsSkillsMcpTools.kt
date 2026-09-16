package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult

class ConnectionsSkillsMcpTools(
    private val registry: ConnectionRegistry,
) : McpToolProvider {

    override val providerId = "connections-skills"

    override fun tools(): List<McpToolDefinition> =
        listOf(
            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__list",
                description =
                    "List supported external service connections and their current dependency, authentication, and connection state.",
                handler = { _ -> list() },
                inputSchema = """{"type":"object","properties":{}}""",
                readOnly = true,
                requiredPermissions = listOf("connections.read"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__github_status",
                description =
                    "Check the real GitHub CLI installation and authentication state.",
                handler = { _ -> githubStatus() },
                inputSchema = """{"type":"object","properties":{}}""",
                readOnly = true,
                requiredPermissions = listOf("connections.read"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__github_list_repos",
                description =
                    "List repositories available to the authenticated GitHub account.",
                handler = { args ->
                    listRepos(args.int("limit") ?: 20)
                },
                inputSchema =
                    """{"type":"object","properties":{"limit":{"type":"integer","minimum":1,"maximum":100}}}""",
                readOnly = true,
                requiredPermissions = listOf("connections.read"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__github_create_issue",
                description =
                    "Create a GitHub issue in an owner/name repository using the authenticated GitHub CLI account.",
                handler = { args ->
                    createIssue(
                        repository = args.string("repository"),
                        title = args.string("title"),
                        body = args.string("body"),
                    )
                },
                inputSchema =
                    """{"type":"object","properties":{"repository":{"type":"string"},"title":{"type":"string"},"body":{"type":"string"}},"required":["repository","title","body"]}""",
                readOnly = false,
                requiredPermissions = listOf("connections.write"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__google_sheets_status",
                description =
                    "Check the Google Workspace CLI installation and authentication state for Google Sheets.",
                handler = { _ -> googleSheetsStatus() },
                inputSchema = """{"type":"object","properties":{}}""",
                readOnly = true,
                requiredPermissions = listOf("connections.read"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__google_sheets_read",
                description =
                    "Read values from a Google Sheet using spreadsheet ID and A1 notation.",
                handler = { args ->
                    googleSheetsRead(
                        spreadsheetId = args.string("spreadsheetId"),
                        range = args.string("range"),
                    )
                },
                inputSchema =
                    """{"type":"object","properties":{"spreadsheetId":{"type":"string"},"range":{"type":"string"}},"required":["spreadsheetId","range"]}""",
                readOnly = true,
                requiredPermissions = listOf("connections.read"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__google_sheets_write",
                description =
                    "Write a JSON ValueRange to a Google Sheet using RAW or USER_ENTERED input.",
                handler = { args ->
                    googleSheetsWrite(
                        spreadsheetId = args.string("spreadsheetId"),
                        range = args.string("range"),
                        valuesJson = args.string("valuesJson"),
                        valueInputOption = args.string("valueInputOption") ?: "USER_ENTERED",
                    )
                },
                inputSchema =
                    """{"type":"object","properties":{"spreadsheetId":{"type":"string"},"range":{"type":"string"},"valuesJson":{"type":"string","description":"JSON ValueRange body containing the values matrix to write."},"valueInputOption":{"type":"string","enum":["RAW","USER_ENTERED"]}},"required":["spreadsheetId","range","valuesJson"]}""",
                readOnly = false,
                requiredPermissions = listOf("connections.write"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__connect",
                description =
                    "Verify the provider dependency and authentication state, then activate the connection.",
                handler = { args ->
                    connect(args.string("provider"))
                },
                inputSchema =
                    """{"type":"object","properties":{"provider":{"type":"string","enum":["github","google-sheets"]}},"required":["provider"]}""",
                readOnly = false,
                requiredPermissions = listOf("connections.write"),
            ),

            McpToolDefinition.withRbac(
                name = "mcp__ai_rever_boss_plugin_dynamic_connectionskills__disconnect",
                description =
                    "Disable a connection so connector operations are rejected until it is explicitly reconnected.",
                handler = { args ->
                    disconnect(args.string("provider"))
                },
                inputSchema =
                    """{"type":"object","properties":{"provider":{"type":"string","enum":["github","google-sheets"]}},"required":["provider"]}""",
                readOnly = false,
                requiredPermissions = listOf("connections.write"),
            ),
        )

    private fun googleSheetsStatus(): McpToolResult {
        val status = registry.status(ConnectionProvider.GOOGLE_SHEETS)

        return McpToolResult(
            json(status),
            status.state == ConnectionState.ERROR,
        )
    }

    private fun googleSheetsRead(
        spreadsheetId: String?,
        range: String?,
    ): McpToolResult {
        if (!registry.isConnected(ConnectionProvider.GOOGLE_SHEETS)) {
            return McpToolResult(
                "Google Sheets connection is not active. Connect Google Sheets before using this tool.",
                true,
            )
        }

        if (spreadsheetId.isNullOrBlank() || range.isNullOrBlank()) {
            return McpToolResult("spreadsheetId and range are required.", true)
        }

        val result = registry.googleSheets().readValues(spreadsheetId, range)

        return McpToolResult(
            result.output.ifBlank { "gws returned no output." },
            !result.success,
        )
    }

    private fun googleSheetsWrite(
        spreadsheetId: String?,
        range: String?,
        valuesJson: String?,
        valueInputOption: String,
    ): McpToolResult {
        if (!registry.isConnected(ConnectionProvider.GOOGLE_SHEETS)) {
            return McpToolResult(
                "Google Sheets connection is not active. Connect Google Sheets before using this tool.",
                true,
            )
        }

        if (
            spreadsheetId.isNullOrBlank() ||
            range.isNullOrBlank() ||
            valuesJson.isNullOrBlank()
        ) {
            return McpToolResult(
                "spreadsheetId, range, and valuesJson are required.",
                true,
            )
        }

        val result = registry.googleSheets().writeValues(
            spreadsheetId,
            range,
            valuesJson,
            valueInputOption,
        )

        return McpToolResult(
            result.output.ifBlank { "gws returned no output." },
            !result.success,
        )
    }

    private fun provider(value: String?): ConnectionProvider? =
        ConnectionProvider.entries.firstOrNull {
            it.id.equals(value, ignoreCase = true)
        }

    private fun list(): McpToolResult =
        McpToolResult(
            ConnectionProvider.entries.joinToString(
                prefix = "[",
                postfix = "]",
            ) { json(registry.status(it)) },
            false,
        )

    private fun githubStatus(): McpToolResult {
        val status = registry.status(ConnectionProvider.GITHUB)

        return McpToolResult(
            json(status),
            status.state == ConnectionState.ERROR,
        )
    }

    private fun listRepos(limit: Int): McpToolResult {
        if (!registry.isConnected(ConnectionProvider.GITHUB)) {
            return McpToolResult(
                "GitHub connection is not active. Connect GitHub before using this tool.",
                true,
            )
        }

        val result = registry.github().listRepositories(limit)

        return McpToolResult(
            result.output.ifBlank {
                "GitHub CLI returned no output."
            },
            !result.success,
        )
    }

    private fun createIssue(
        repository: String?,
        title: String?,
        body: String?,
    ): McpToolResult {
        if (!registry.isConnected(ConnectionProvider.GITHUB)) {
            return McpToolResult(
                "GitHub connection is not active. Connect GitHub before using this tool.",
                true,
            )
        }

        if (repository.isNullOrBlank() ||
            title.isNullOrBlank() ||
            body.isNullOrBlank()
        ) {
            return McpToolResult(
                "repository, title, and body are required.",
                true,
            )
        }

        val result =
            registry.github().createIssue(
                repository = repository,
                title = title,
                body = body,
            )

        return McpToolResult(
            result.output.ifBlank {
                "GitHub CLI returned no output."
            },
            !result.success,
        )
    }

    private suspend fun connect(value: String?): McpToolResult {
        val provider =
            provider(value)
                ?: return McpToolResult(
                    "Unsupported provider.",
                    true,
                )

        val status = registry.connect(provider)

        return McpToolResult(
            json(status),
            status.state == ConnectionState.ERROR,
        )
    }

    private fun disconnect(value: String?): McpToolResult {
        val provider =
            provider(value)
                ?: return McpToolResult(
                    "Unsupported provider.",
                    true,
                )

        return McpToolResult(
            json(registry.disconnect(provider)),
            false,
        )
    }

    private fun json(status: ConnectionStatus): String =
        """{"provider":"${status.provider.id}","displayName":"${status.provider.displayName}","state":"${status.state.name.lowercase()}","message":"${escape(status.message)}"}"""

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}
