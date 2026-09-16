package ai.rever.boss.plugin.dynamic.connectionskills

class SkillLoader {

    fun load(skill: SkillDefinition): String? {
        val resourcePath = skill.resourcePath

        return javaClass.classLoader
            ?.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
    }
}
