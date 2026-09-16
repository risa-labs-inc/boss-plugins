package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

class ConnectionsSkillsDynamicPlugin : DynamicPlugin {
    override val pluginId = "ai.rever.boss.plugin.dynamic.connectionskills"
    override val displayName = "Connections & Skills"
    override val version = "0.1.0"
    override val description =
        "Unified catalogue and governed access layer for external service connections and version-pinned skills."
    override val author = "TechTribe"
    override val url =
        "https://github.com/risa-labs-inc/boss-plugins/tree/main/connections-skills"

    private var registered = false
    private var panelRegistry: ai.rever.boss.plugin.api.PanelRegistry? = null

    override fun register(context: PluginContext) {
        if (registered) return

        panelRegistry = context.panelRegistry

        context.panelRegistry.registerPanel(ConnectionsSkillsInfo) { componentContext, panelInfo ->
            ConnectionsSkillsComponent(
                componentContext,
                panelInfo,
                context,
            )
        }

        registered = true
    }

    override fun dispose() {
        if (!registered) return

        registered = false

        runCatching {
            panelRegistry?.unregisterPanel(ConnectionsSkillsInfo.id)
        }

        panelRegistry = null
    }
}
