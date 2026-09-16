package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Link

object ConnectionsSkillsInfo : PanelInfo {
    override val id = PanelId("connections-skills", 31)
    override val displayName = "Connections & Skills"
    override val icon = FeatherIcons.Link
    override val defaultSlotPosition = left.bottom
}
