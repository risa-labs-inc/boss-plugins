import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
version = "0.1.0"

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

// Auto-detect CI environment
val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    if (useLocalDependencies) {
        // Local dev: boss-plugin-api JAR from sibling repo (pinned version)
        compileOnly(files("$bossPluginApiPath/build/libs/boss-plugin-api-1.0.74.jar"))
    } else {
        // CI: downloaded JAR
        compileOnly(files("build/downloaded-deps/boss-plugin-api.jar"))
    }

    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)

    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}

// Thin plugin JAR: compiled classes + manifest only (API and Compose come from the host)
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-connections-skills-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title" to "BOSS Connections & Skills Plugin",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.dynamic.connectionskills.ConnectionsSkillsDynamicPlugin"
        )
    }
    from(sourceSets.main.get().output)
    from("src/main/resources")
}

// The default `jar` (classes-only) writes the SAME archive path as buildPluginJar
// and silently clobbers the bundled plugin JAR whenever it runs later in the task
// graph. Classify it so the two archives never collide; buildPluginJar's output
// stays canonical.
tasks.named<Jar>("jar") {
    archiveClassifier.set("thin")
}

// Sync version from build.gradle.kts into plugin.json (single source of truth)
tasks.processResources {
    inputs.property("pluginVersion", version)
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "$version"""")
        }
    }
}

tasks.build { dependsOn("buildPluginJar") }

// Fat JAR for out-of-process plugin execution
tasks.register<Jar>("shadowJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes("Main-Class" to "ai.rever.boss.plugin.runtime.PluginProcessMainKt")
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(sourceSets.main.get().output)
    from("src/main/resources")
}
