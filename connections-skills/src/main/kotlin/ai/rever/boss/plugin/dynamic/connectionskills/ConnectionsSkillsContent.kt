package ai.rever.boss.plugin.dynamic.connectionskills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun ConnectionsSkillsContent(registry: ConnectionRegistry) {
    val skillLoader = SkillLoader()
    val statuses by registry.statuses.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Connections & Skills",
                    style = MaterialTheme.typography.h5,
                )
                Text(
                    "Connect external services and expose governed, version-pinned capabilities to BOSS agents.",
                    style = MaterialTheme.typography.body1,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = registry::refreshAsync,
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }

        item {
            Text(
                "Connections",
                style = MaterialTheme.typography.h6,
            )
        }

        items(statuses) { status ->
            ConnectionCard(
                status = status,
                registry = registry,
                onConnect = {
                    scope.launch {
                        registry.connect(status.provider)
                    }
                },
            )
        }

        item {
            Text(
                "Skills Catalogue",
                style = MaterialTheme.typography.h6,
            )
        }

        items(SkillCatalog.skills) { skill ->
            SkillCard(
                skill = skill,
                connected = registry.isConnected(skill.provider),
                loaded = skillLoader.load(skill) != null,
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    status: ConnectionStatus,
    registry: ConnectionRegistry,
    onConnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                status.provider.displayName,
                style = MaterialTheme.typography.h6,
            )

            Text("Dependency: ${status.provider.executable}")
            Text("State: ${status.state.name.lowercase()}")
            Text(status.message)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (status.state) {
                    ConnectionState.CONNECTED -> {
                        Button(
                            onClick = {
                                registry.disconnect(status.provider)
                            },
                        ) {
                            Text("Disconnect")
                        }
                    }

                    ConnectionState.MISSING_DEPENDENCY -> {
                        Button(
                            enabled = false,
                            onClick = {},
                        ) {
                            Text("Install dependency")
                        }
                    }

                    ConnectionState.NOT_AUTHENTICATED,
                    ConnectionState.DISCONNECTED,
                    ConnectionState.ERROR,
                    ConnectionState.AVAILABLE -> {
                        Button(
                            onClick = onConnect,
                        ) {
                            Text(
                                if (status.state == ConnectionState.NOT_AUTHENTICATED) {
                                    "Connect"
                                } else {
                                    "Reconnect"
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillCard(
    skill: SkillDefinition,
    connected: Boolean,
    loaded: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    skill.name,
                    style = MaterialTheme.typography.h6,
                )
                Text("v${skill.version}")
            }

            Text(skill.description)

            Text(
                "Provider: ${skill.provider.displayName}",
            )

            Text(
                "Capabilities: ${skill.capabilities.joinToString(" • ")}",
            )

            Text(
                if (connected) {
                    "Available to connected workflows"
                } else {
                    "Requires an active connection"
                },
            )

            Text(
                if (loaded) {
                    "SKILL.md: loaded"
                } else {
                    "SKILL.md: unavailable"
                },
            )
        }
    }
}
