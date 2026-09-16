package ai.rever.boss.plugin.dynamic.connectionskills

import kotlin.test.Test
import kotlin.test.assertTrue

class GitHubAdapterE2ETest {

    @Test
    fun `github adapter performs real authenticated read and write`() {
        if (System.getenv("RUN_GITHUB_E2E") != "1") return

        val repository =
            System.getenv("E2E_GITHUB_REPO")
                ?: error("E2E_GITHUB_REPO must be set")

        val adapter = GitHubAdapter()

        assertTrue(
            adapter.isInstalled(),
            "GitHub CLI (gh) is not installed",
        )

        assertTrue(
            adapter.isAuthenticated(),
            "GitHub CLI is not authenticated",
        )

        val repositories = adapter.listRepositories(limit = 5)

        assertTrue(
            repositories.success,
            "GitHub repository listing failed: ${repositories.output}",
        )

        val title =
            "BOSS Connections & Skills E2E ${System.currentTimeMillis()}"

        val created =
            adapter.createIssue(
                repository = repository,
                title = title,
                body = "Temporary E2E verification for the Connections & Skills BOSS plugin.",
            )

        assertTrue(
            created.success,
            "GitHub issue creation failed: ${created.output}",
        )

        println("===== GITHUB ADAPTER E2E PASSED =====")
        println("Authenticated: true")
        println("Repository read: PASSED")
        println("Issue write: PASSED")
        println("Created issue: ${created.output}")
    }
}
