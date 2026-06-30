package ai.rever.boss.plugin.dynamic.hello

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box

/** Describes the Hello panel: its id, sidebar icon, and default slot. */
object HelloInfo : PanelInfo {
    override val id = PanelId("hello", 50)          // (panelId, defaultOrder)
    override val displayName = "Hello"
    override val icon = FeatherIcons.Box
    override val defaultSlotPosition = left.bottom   // left sidebar, bottom slot
}
