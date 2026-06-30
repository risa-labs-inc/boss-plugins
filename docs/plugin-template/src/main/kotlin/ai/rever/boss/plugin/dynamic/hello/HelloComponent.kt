package ai.rever.boss.plugin.dynamic.hello

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext

/**
 * The live Hello panel. [Content] draws the UI; wrap it in [BossTheme] so it
 * follows the active host theme, and paint with [BossThemeColors] tokens.
 */
class HelloComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        BossTheme {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Hello from a BOSS plugin", color = BossThemeColors.TextPrimary)
            }
        }
    }
}
