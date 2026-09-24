plugins {
    kotlin("jvm") version "2.4.10"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("../BossConsole/plugin-platform/plugin-api-core/build/api-contract/boss-plugin-api-1.0.93.jar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

tasks.register<Jar>("buildPluginJar") {
    archiveBaseName.set("boss-plugin-docker")
    archiveVersion.set("0.1.0")
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
