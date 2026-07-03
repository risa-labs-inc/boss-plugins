package ai.rever.boss.plugin.dynamic.hello

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * Hello — a minimal BOSS panel plugin.
 *
 * The host instantiates this class (via `mainClass` in plugin.json) and calls
 * [register] once at load. Register your panel(s)/tab type(s) here.
 */
class HelloDynamicPlugin : DynamicPlugin {
    override val pluginId = "ai.rever.boss.plugin.dynamic.hello"
    override val displayName = "Hello (Dynamic)"
    override val version = "0.1.0"
    override val description = "A starter BOSS panel plugin"
    override val author = "Your Name"
    override val url = "https://github.com/you/boss-plugin-hello"

    override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(HelloInfo) { ctx, panelInfo ->
            HelloComponent(ctx, panelInfo)
        }
    }

    override fun dispose() {
        // Release any resources here (the host calls this on unload).
    }
}
