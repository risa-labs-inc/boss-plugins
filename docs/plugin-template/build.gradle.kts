import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "ai.rever.boss.plugin.dynamic"
version = "0.1.0"   // ← the single source of truth; processResources syncs it into plugin.json

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// CI sets CI=true and downloads the api jar; locally we use the sibling boss-plugin-api build.
val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    if (useLocalDependencies) {
        compileOnly(files("$bossPluginApiPath/build/libs/boss-plugin-api-1.0.47.jar"))
    } else {
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
}

// The loadable plugin JAR: compiled classes + the plugin.json manifest.
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-hello-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes(
            "Implementation-Title" to "BOSS Hello Plugin",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.dynamic.hello.HelloDynamicPlugin",
        )
    }
    from(sourceSets.main.get().output)
    from("src/main/resources")
}

// Keep plugin.json's version in lockstep with the build version.
tasks.processResources {
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "$version"""")
        }
    }
}

tasks.build { dependsOn("buildPluginJar") }
