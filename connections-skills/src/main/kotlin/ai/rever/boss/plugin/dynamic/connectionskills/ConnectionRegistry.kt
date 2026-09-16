package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry(
    private val context: PluginContext,
) {
    private val storage = context.pluginStorageFactory?.createStorage("connections")
    private val states = ConcurrentHashMap<ConnectionProvider, ConnectionState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val github = GitHubAdapter()
    private val googleSheets = GoogleSheetsAdapter()

    private val _statuses =
        MutableStateFlow(
            ConnectionProvider.entries.map { initialStatus(it) },
        )

    val statuses: StateFlow<List<ConnectionStatus>> =
        _statuses.asStateFlow()

    init {
        scope.launch {
            loadPersistedStates()
            refresh()
        }
    }

    fun status(provider: ConnectionProvider): ConnectionStatus =
        _statuses.value.first { it.provider == provider }

    fun refreshAsync() {
        scope.launch {
            refresh()
        }
    }

    /**
     * Checks dependency and authentication state.
     *
     * Provider CLI calls are explicitly dispatched to IO so this method
     * remains safe even when called from a UI coroutine.
     */
    suspend fun connect(provider: ConnectionProvider): ConnectionStatus =
        withContext(Dispatchers.IO) {
            val result =
                when (provider) {
                    ConnectionProvider.GITHUB -> {
                        when {
                            !github.isInstalled() ->
                                ConnectionState.MISSING_DEPENDENCY

                            !github.isAuthenticated() ->
                                ConnectionState.NOT_AUTHENTICATED

                            else ->
                                ConnectionState.CONNECTED
                        }
                    }

                    ConnectionProvider.GOOGLE_SHEETS -> {
                        when {
                            !googleSheets.isInstalled() ->
                                ConnectionState.MISSING_DEPENDENCY

                            !googleSheets.isAuthenticated() ->
                                ConnectionState.NOT_AUTHENTICATED

                            else ->
                                ConnectionState.CONNECTED
                        }
                    }
                }

            states[provider] = result
            storage?.putString(key(provider), result.name)

            refresh()

            status(provider)
        }

    fun disconnect(provider: ConnectionProvider): ConnectionStatus {
        val disconnected =
            ConnectionStatus(
                provider = provider,
                state = ConnectionState.DISCONNECTED,
                message =
                    "${provider.displayName} connection is disabled. " +
                        "Reconnect from Connections & Skills.",
            )

        states[provider] = ConnectionState.DISCONNECTED

        _statuses.value =
            _statuses.value.map {
                if (it.provider == provider) disconnected else it
            }

        scope.launch {
            storage?.putString(
                key(provider),
                ConnectionState.DISCONNECTED.name,
            )
        }

        return disconnected
    }

    fun isConnected(provider: ConnectionProvider): Boolean =
        status(provider).state == ConnectionState.CONNECTED

    fun github(): GitHubAdapter = github

    fun googleSheets(): GoogleSheetsAdapter = googleSheets

    fun dispose() {
        scope.cancel()
        states.clear()
    }

    private suspend fun loadPersistedStates() {
        for (provider in ConnectionProvider.entries) {
            val saved = storage?.getString(key(provider), null)

            states[provider] =
                saved?.let {
                    runCatching {
                        ConnectionState.valueOf(it)
                    }.getOrNull()
                } ?: ConnectionState.NOT_AUTHENTICATED
        }
    }

    /**
     * Runs exclusively on the registry IO scope.
     */
    private suspend fun refresh() {
        val updated =
            ConnectionProvider.entries.map { provider ->
                inspect(provider)
            }

        _statuses.value = updated
    }

    private fun inspect(provider: ConnectionProvider): ConnectionStatus {
        if (states[provider] == ConnectionState.DISCONNECTED) {
            return ConnectionStatus(
                provider,
                ConnectionState.DISCONNECTED,
                "${provider.displayName} connection is disabled. " +
                    "Reconnect from Connections & Skills.",
            )
        }

        if (!available(provider.executable)) {
            return ConnectionStatus(
                provider,
                ConnectionState.MISSING_DEPENDENCY,
                "Required executable ${provider.executable} was not found on PATH.",
            )
        }

        return when (provider) {
            ConnectionProvider.GITHUB -> {
                if (!github.isAuthenticated()) {
                    ConnectionStatus(
                        provider,
                        ConnectionState.NOT_AUTHENTICATED,
                        "GitHub CLI is installed but not authenticated. " +
                            "Run `gh auth login`.",
                    )
                } else {
                    ConnectionStatus(
                        provider,
                        ConnectionState.CONNECTED,
                        "GitHub CLI authentication is active.",
                    )
                }
            }

            ConnectionProvider.GOOGLE_SHEETS -> {
                if (!googleSheets.isAuthenticated()) {
                    ConnectionStatus(
                        provider,
                        ConnectionState.NOT_AUTHENTICATED,
                        "Google Workspace CLI is installed but not authenticated. " +
                            "Run `gws auth login`.",
                    )
                } else {
                    ConnectionStatus(
                        provider,
                        ConnectionState.CONNECTED,
                        "Google Workspace CLI authentication is active.",
                    )
                }
            }
        }
    }

    private fun initialStatus(
        provider: ConnectionProvider,
    ): ConnectionStatus =
        ConnectionStatus(
            provider = provider,
            state = ConnectionState.NOT_AUTHENTICATED,
            message = "Checking ${provider.displayName} connection...",
        )

    private fun available(name: String): Boolean {
        val dirs =
            (
                (System.getenv("PATH") ?: "").split(File.pathSeparator) +
                    listOf(
                        System.getProperty("user.home") + "/.local/bin",
                        "/opt/homebrew/bin",
                        "/usr/local/bin",
                        "/usr/bin",
                    )
                )

        return dirs.any { dir ->
            if (dir.isBlank()) {
                false
            } else {
                File(dir, name).let {
                    it.isFile && it.canExecute()
                }
            }
        }
    }

    private fun key(provider: ConnectionProvider): String =
        "connection.${provider.id}.state"
}
