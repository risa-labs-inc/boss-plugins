package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.PluginContext
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

class ConnectionsSkillsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val pluginContext: PluginContext,
) : PanelComponentWithUI, ComponentContext by ctx {

    private val registry = ConnectionRegistry(pluginContext)
    private val tools = ConnectionsSkillsMcpTools(registry)

    init {
        pluginContext.registerMcpToolProvider(tools)
        lifecycle.doOnDestroy {
            dispose()
        }
    }

    @Composable
    override fun Content() {
        ConnectionsSkillsContent(registry)
    }

    fun dispose() {
        pluginContext.unregisterMcpToolProvider(tools.providerId)
        registry.dispose()
    }
}
