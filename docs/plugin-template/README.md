# Plugin template — "Hello panel"

A minimal, build-ready BOSS panel plugin. Use it as a starting point.

## Use it

1. Copy this `plugin-template/` directory to a new repo `boss-plugin-<name>`.
2. Rename everything `hello` → `<name>`: the package dir
   `src/main/kotlin/ai/rever/boss/plugin/dynamic/hello/`, the three `Hello*.kt` files/classes,
   `pluginId`/`mainClass`/`displayName` in `plugin.json`, and `archiveFileName`/`Main-Class` in
   `build.gradle.kts`.
3. Add the Gradle wrapper (`gradle/`, `gradlew`, `gradlew.bat`) — copy from any existing plugin.
4. Build: `./gradlew buildPluginJar` → `build/libs/boss-plugin-<name>-<version>.jar`.

`boss-plugin-api` is `compileOnly`; the local build expects it at
`../boss-plugin-api/build/libs/boss-plugin-api-<ver>.jar` (build that sibling first, or adjust the
pin). See **[../creating-a-plugin.md](../creating-a-plugin.md)** for the full walkthrough,
local-testing steps, and links to the API, manifest, themes, permissions, versioning, and CI/CD docs.
