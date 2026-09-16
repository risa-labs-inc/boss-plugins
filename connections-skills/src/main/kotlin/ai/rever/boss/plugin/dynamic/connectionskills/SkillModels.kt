package ai.rever.boss.plugin.dynamic.connectionskills

data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val provider: ConnectionProvider,
    val capabilities: List<String>,
    val resourcePath: String,
)

object SkillCatalog {
    val skills = listOf(
        SkillDefinition(
            id = "github-issues",
            name = "GitHub Issues",
            description =
                "Create and inspect GitHub issues through the authenticated gh CLI.",
            version = "1.0.0",
            provider = ConnectionProvider.GITHUB,
            capabilities = listOf(
                "Read repositories",
                "Create issues",
            ),
            resourcePath = "skills/github-issues/SKILL.md",
        ),
        SkillDefinition(
            id = "google-sheets",
            name = "Google Sheets",
            description =
                "Read and update spreadsheet ranges through Google Workspace CLI.",
            version = "1.0.0",
            provider = ConnectionProvider.GOOGLE_SHEETS,
            capabilities = listOf(
                "Read cells",
                "Write cells",
            ),
            resourcePath = "skills/google-sheets/SKILL.md",
        ),
    )
}
