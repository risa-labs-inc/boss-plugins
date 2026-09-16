package ai.rever.boss.plugin.dynamic.connectionskills

import java.util.concurrent.TimeUnit

class GitHubAdapter {

    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false,
    ) {
        val success: Boolean
            get() = !timedOut && exitCode == 0
    }

    fun isInstalled(): Boolean =
        run("gh", "--version", timeoutSec = 10).success

    fun isAuthenticated(): Boolean =
        run("gh", "auth", "status", timeoutSec = 15).success

    fun listRepositories(limit: Int): Result {
        val safeLimit = limit.coerceIn(1, 100)

        return run(
            "gh",
            "repo",
            "list",
            "--limit",
            safeLimit.toString(),
            "--json",
            "nameWithOwner,url,isPrivate",
            timeoutSec = 30,
        )
    }

    fun createIssue(
        repository: String,
        title: String,
        body: String,
    ): Result {
        if (!isValidRepository(repository)) {
            return Result(
                2,
                "Invalid repository. Expected owner/name.",
            )
        }

        if (title.isBlank()) {
            return Result(
                2,
                "Issue title must not be blank.",
            )
        }

        if (title.length > 200) {
            return Result(
                2,
                "Issue title must be at most 200 characters.",
            )
        }

        if (body.length > 20_000) {
            return Result(
                2,
                "Issue body must be at most 20000 characters.",
            )
        }

        return run(
            "gh",
            "issue",
            "create",
            "--repo",
            repository,
            "--title",
            title,
            "--body",
            body,
            timeoutSec = 60,
        )
    }

    private fun isValidRepository(value: String): Boolean =
        Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(value)

    private fun run(
        vararg cmd: String,
        timeoutSec: Long,
    ): Result =
        try {
            val process =
                ProcessBuilder(*cmd)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["GH_PAGER"] = "cat"
                        environment()["PAGER"] = "cat"
                    }
                    .start()

            process.outputStream.close()

            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()

            val readerThread =
                Thread {
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            synchronized(output) {
                                if (output.length < 200_000) {
                                    output.append(line).append('\n')
                                }
                            }
                        }
                    }
                }

            readerThread.isDaemon = true
            readerThread.start()

            if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                readerThread.join(1_000)

                return Result(
                    exitCode = -1,
                    output = "GitHub CLI timed out after ${timeoutSec}s.",
                    timedOut = true,
                )
            }

            readerThread.join(1_000)

            Result(
                exitCode = process.exitValue(),
                output = synchronized(output) {
                    output.toString().trim()
                },
            )
        } catch (e: Exception) {
            Result(
                exitCode = -1,
                output = e.message ?: e.javaClass.simpleName,
            )
        }
}
