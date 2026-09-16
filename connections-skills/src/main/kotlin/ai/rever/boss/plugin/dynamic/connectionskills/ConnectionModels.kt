package ai.rever.boss.plugin.dynamic.connectionskills

enum class ConnectionProvider(val id: String, val displayName: String, val executable: String) {
    GITHUB("github", "GitHub", "gh"),
    GOOGLE_SHEETS("google-sheets", "Google Sheets", "gws")
}

enum class ConnectionState { AVAILABLE, MISSING_DEPENDENCY, NOT_AUTHENTICATED, CONNECTED, DISCONNECTED, ERROR }

data class ConnectionStatus(val provider: ConnectionProvider, val state: ConnectionState, val message: String)
