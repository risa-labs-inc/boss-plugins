package com.example.dockerplugin

import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import java.io.BufferedReader
import java.io.InputStreamReader

object DockerPlugin : Plugin {
    override val pluginId = "com.hackathon.boss.docker"
    override val displayName = "Boss Docker Tools"

    override fun register(context: PluginContext) {
        println("====== DOCKER PLUGIN HEADLESS TEST START ======")
        println("DockerPlugin registered successfully!")
        val result = listContainers()
        println("Container check result length: ${result.length}")
        println("====== DOCKER PLUGIN HEADLESS TEST END ======")
    }
    
    fun listContainers(): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("docker", "ps", "-a", "--format", "{{json .}}"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readText()
        } catch(e: Exception) {
            "Docker error: ${e.message}"
        }
    }

    fun getLogs(containerId: String): String {
        return "logs"
    }
}
