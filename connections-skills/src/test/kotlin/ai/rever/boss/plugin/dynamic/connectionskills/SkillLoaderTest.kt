package ai.rever.boss.plugin.dynamic.connectionskills

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkillLoaderTest {

    private val loader = SkillLoader()

    @Test
    fun `all catalog skills have packaged skill resources`() {
        SkillCatalog.skills.forEach { skill ->
            val content = loader.load(skill)

            assertNotNull(
                content,
                "Missing packaged resource: ${skill.resourcePath}",
            )

            assertTrue(
                content.contains("# "),
                "Skill resource should contain a markdown heading: ${skill.resourcePath}",
            )
        }
    }

    @Test
    fun `github skill metadata matches resource`() {
        val skill = SkillCatalog.skills.first { it.id == "github-issues" }
        val content = loader.load(skill)

        assertNotNull(content)
        assertTrue(content.contains("Skill ID: github-issues"))
        assertTrue(content.contains("Version: 1.0.0"))
        assertTrue(content.contains("Provider: github"))
    }

    @Test
    fun `google sheets skill metadata matches resource`() {
        val skill = SkillCatalog.skills.first { it.id == "google-sheets" }
        val content = loader.load(skill)

        assertNotNull(content)
        assertTrue(content.contains("Skill ID: google-sheets"))
        assertTrue(content.contains("Version: 1.0.0"))
        assertTrue(content.contains("Provider: google-sheets"))
    }

    @Test
    fun `catalog skill versions are explicit`() {
        SkillCatalog.skills.forEach { skill ->
            assertTrue(
                skill.version.matches(Regex("""\d+\.\d+\.\d+""")),
                "Skill ${skill.id} must use semantic versioning",
            )
        }
    }
}
