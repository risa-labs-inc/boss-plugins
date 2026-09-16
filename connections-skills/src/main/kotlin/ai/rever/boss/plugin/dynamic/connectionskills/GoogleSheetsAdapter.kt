package ai.rever.boss.plugin.dynamic.connectionskills

import java.util.concurrent.TimeUnit

class GoogleSheetsAdapter {

    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false,
    ) {
        val success: Boolean
            get() = !timedOut && exitCode == 0
    }

    fun isInstalled(): Boolean =
        run("gws", "--version", timeoutSec = 10).success

    fun isAuthenticated(): Boolean {
        val result = run("gws", "auth", "status", timeoutSec = 15)
        return result.success && isAuthenticatedStatus(result.output)
    }

    internal fun isAuthenticatedStatus(output: String): Boolean {
        val normalized = output.lowercase()

        if (normalized.contains("\"auth_method\": \"none\"") ||
            normalized.contains("\"credential_source\": \"none\"")
        ) {
            return false
        }

        return normalized.contains("\"encrypted_credentials_exists\": true") ||
            normalized.contains("\"plain_credentials_exists\": true") ||
            normalized.contains("\"token_cache_exists\": true") ||
            normalized.contains("\"auth_method\": \"oauth\"") ||
            normalized.contains("\"auth_method\": \"adc\"")
    }

    fun readValues(
        spreadsheetId: String,
        range: String,
    ): Result {
        if (!isValidSpreadsheetId(spreadsheetId)) {
            return Result(2, "Invalid spreadsheet ID.")
        }
        if (range.isBlank() || range.length > 500) {
            return Result(2, "Range must be non-empty and at most 500 characters.")
        }

        val params = jsonObject(
            "spreadsheetId" to spreadsheetId,
            "range" to range,
        )

        return run(
            "gws", "sheets", "spreadsheets", "values", "get",
            "--params", params,
            timeoutSec = 60,
        )
    }

    fun writeValues(
        spreadsheetId: String,
        range: String,
        valuesJson: String,
        valueInputOption: String = "USER_ENTERED",
    ): Result {
        if (!isValidSpreadsheetId(spreadsheetId)) {
            return Result(2, "Invalid spreadsheet ID.")
        }
        if (range.isBlank() || range.length > 500) {
            return Result(2, "Range must be non-empty and at most 500 characters.")
        }
        if (valueInputOption !in setOf("RAW", "USER_ENTERED")) {
            return Result(2, "valueInputOption must be RAW or USER_ENTERED.")
        }
        if (valuesJson.isBlank() || valuesJson.length > 100_000) {
            return Result(2, "valuesJson must be non-empty and at most 100000 characters.")
        }

        val params = jsonObject(
            "spreadsheetId" to spreadsheetId,
            "range" to range,
            "valueInputOption" to valueInputOption,
        ).dropLast(1) +
            ",\"includeValuesInResponse\":true}"

        return run(
            "gws", "sheets", "spreadsheets", "values", "update",
            "--params", params,
            "--json", valuesJson,
            timeoutSec = 60,
        )
    }

    private fun isValidSpreadsheetId(value: String): Boolean =
        value.matches(Regex("^[A-Za-z0-9_-]{10,200}$"))

    private fun jsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "\"${escape(key)}\":\"${escape(value)}\""
        }

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    private fun run(
        vararg cmd: String,
        timeoutSec: Long,
    ): Result = try {
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .apply {
                environment()["GWS_PAGER"] = "cat"
                environment()["GH_PAGER"] = "cat"
                environment()["PAGER"] = "cat"
            }
            .start()

        process.outputStream.close()

        val output = StringBuilder()
        val reader = process.inputStream.bufferedReader()

        val readerThread = Thread {
            reader.useLines { lines ->
                lines.forEach {
                    synchronized(output) {
                        if (output.length < 200_000) {
                            output.append(it).append('\n')
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
                -1,
                "Google Workspace CLI timed out after ${timeoutSec}s.",
                timedOut = true,
            )
        }

        readerThread.join(1_000)

        Result(
            process.exitValue(),
            synchronized(output) { output.toString().trim() },
        )
    } catch (e: Exception) {
        Result(-1, e.message ?: e.javaClass.simpleName)
    }
}
