# Creating a Plugin

A BOSS plugin is a JAR that the host (BossConsole) discovers, validates, and loads at runtime. It
implements the `DynamicPlugin` entry point, registers one or more **panels** (sidebar widgets)
and/or **tab types**, and renders Compose UI. This guide takes you from an empty repo to a loaded,
themed plugin.

> The fastest start is to copy **[`plugin-template/`](plugin-template/)** and rename `hello` → your
> plugin. The walkthrough below explains each piece. The `git-status` plugin in this repo is the
> canonical real example to crib from.

## 1. Plugin types

| `type` (in `plugin.json`) | What it adds |
|---|---|
| `panel` | A sidebar panel (most plugins). Implements `PanelComponentWithUI`. |
| `tab` | A new tab type opened in the main area. Implements `TabComponentWithUI`. |
| `mixed` | Both panels and tabs. |
| `service` | Background/utility only — registers APIs/providers, no UI. |

See [manifest.md](manifest.md) for `type` and [plugin-api.md](plugin-api.md) for the interfaces.

## 2. Repository layout

```
boss-plugin-<name>/
├── build.gradle.kts                  # build config + version (single source of truth)
├── settings.gradle.kts
├── gradle/ gradlew gradlew.bat        # Gradle wrapper
├── .github/workflows/build.yml        # release pipeline (see ci-cd.md)
└── src/main/
    ├── kotlin/ai/rever/boss/plugin/dynamic/<name>/
    │   ├── <Name>DynamicPlugin.kt     # entry point (implements DynamicPlugin)
    │   ├── <Name>Info.kt              # PanelInfo (id, icon, slot)
    │   ├── <Name>Component.kt         # PanelComponentWithUI + @Composable Content()
    │   └── <Name>ViewModel.kt         # state (StateFlow) — optional
    └── resources/META-INF/boss-plugin/plugin.json   # the manifest
```

**Naming**: package and `pluginId` are `ai.rever.boss.plugin.dynamic.<name>` (e.g.
`ai.rever.boss.plugin.dynamic.gitstatus`). The build produces `boss-plugin-<name>-<version>.jar`.

## 3. The entry point

`register(context)` is called once when the host loads your plugin. Register your panel(s) there;
clean up in `dispose()`.

```kotlin
package ai.rever.boss.plugin.dynamic.hello

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

class HelloDynamicPlugin : DynamicPlugin {
    override val pluginId = "ai.rever.boss.plugin.dynamic.hello"
    override val displayName = "Hello (Dynamic)"
    override val version = "0.1.0"
    override val description = "A starter panel"
    override val author = "Your Name"
    override val url = "https://github.com/you/boss-plugin-hello"

    override fun register(context: PluginContext) {
        context.panelRegistry.registerPanel(HelloInfo) { ctx, panelInfo ->
            HelloComponent(ctx, panelInfo)
        }
    }

    override fun dispose() { /* release resources if any */ }
}
```

## 4. The panel descriptor + component

`PanelInfo` describes the panel (id, icon, default sidebar slot). `PanelComponentWithUI` is the
live component; its `@Composable Content()` draws the panel. Always wrap your UI in `BossTheme { }`
so it follows the host theme (see [themes.md](themes.md)).

```kotlin
// HelloInfo.kt
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box

object HelloInfo : PanelInfo {
    override val id = PanelId("hello", 50)          // (panelId, defaultOrder)
    override val displayName = "Hello"
    override val icon = FeatherIcons.Box
    override val defaultSlotPosition = left.bottom   // sidebar slot (see plugin-api.md → Panel)
}
```

```kotlin
// HelloComponent.kt
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

class HelloComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        BossTheme {
            Text("Hello from a plugin", color = BossThemeColors.TextPrimary)
        }
    }
}
```

To consume host data, pull a provider off `PluginContext` in `register` and pass it down — **every
provider is nullable**, so handle `null` (e.g. show a fallback). See [plugin-api.md](plugin-api.md).

## 5. The manifest

`src/main/resources/META-INF/boss-plugin/plugin.json` declares your plugin to the host. Minimal
panel manifest:

```json
{
  "manifestVersion": 1,
  "pluginId": "ai.rever.boss.plugin.dynamic.hello",
  "displayName": "Hello (Dynamic)",
  "version": "0.1.0",
  "apiVersion": "1.0.20",
  "minBossVersion": "8.16.30",
  "mainClass": "ai.rever.boss.plugin.dynamic.hello.HelloDynamicPlugin",
  "type": "panel",
  "description": "A starter panel",
  "author": "Your Name",
  "url": "https://github.com/you/boss-plugin-hello",
  "panel": { "position": "left_bottom", "priority": 50 }
}
```

Every field is documented in **[manifest.md](manifest.md)**. Two things to get right now:
`apiVersion` and `minBossVersion` gate whether the host will load you — see
[versioning-and-compatibility.md](versioning-and-compatibility.md). The `version` here is **synced
from `build.gradle.kts`** at build time — don't hand-edit it.

## 6. Build config

`build.gradle.kts` (mirrors `git-status`): `boss-plugin-api` is `compileOnly` (the host provides it
at runtime); Compose/Decompose/Coroutines are bundled. The `buildPluginJar` task produces the
loadable jar; `processResources` syncs the version into `plugin.json`.

```kotlin
group = "ai.rever.boss.plugin.dynamic"
version = "0.1.0"                       // ← the ONE place version lives

val useLocalDependencies = System.getenv("CI") != "true"
val bossPluginApiPath = "../boss-plugin-api"

dependencies {
    if (useLocalDependencies) {
        compileOnly(files("$bossPluginApiPath/build/libs/boss-plugin-api-1.0.47.jar"))
    } else {
        compileOnly(files("build/downloaded-deps/boss-plugin-api.jar"))   // CI downloads it
    }
    implementation(compose.desktop.currentOs); implementation(compose.runtime)
    implementation(compose.ui); implementation(compose.foundation); implementation(compose.material)
    implementation(compose.materialIconsExtended)
    implementation("br.com.devsrsouza.compose.icons:feather:1.1.1")
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-hello-${version}.jar")
    manifest { attributes("Main-Class" to "ai.rever.boss.plugin.dynamic.hello.HelloDynamicPlugin") }
    from(sourceSets.main.get().output)
    from("src/main/resources")
}

tasks.processResources {                // version single-source → plugin.json
    filesMatching("**/plugin.json") {
        filter { it.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "$version"""") }
    }
}
tasks.build { dependsOn("buildPluginJar") }
```

Which `boss-plugin-api` jar to pin? See [versioning-and-compatibility.md](versioning-and-compatibility.md#choosing-the-api-pin).

Build:

```bash
./gradlew buildPluginJar      # → build/libs/boss-plugin-hello-0.1.0.jar
```

## 7. Test it locally

BOSS runs with a separate **dev-mode** data root so test plugins never touch your production
install. Deploy and reload there:

1. **Clean-build** (so `processResources` re-syncs the version into the jar):
   ```bash
   ./gradlew clean buildPluginJar
   ```
2. **Deploy** to the dev plugins dir — `~/.boss_debug/plugins/` (dev mode), **not** `~/.boss/plugins`
   (production). The dev host tracks installed plugins in `~/.boss_debug/plugins/installed.json`,
   keyed by the **internal `plugin.json` `pluginId`** (not the filename). For a clean redeploy,
   overwrite the jar at its `installed.json` `jarPath`:
   ```bash
   cp build/libs/boss-plugin-hello-0.1.0.jar ~/.boss_debug/plugins/
   ```
3. **Clear the extracted cache** so the new bytecode is picked up:
   ```bash
   rm -rf ~/.boss_debug/plugin-cache/ai.rever.boss.plugin.dynamic.hello
   ```
4. **Restart the dev host** — plugins are loaded at startup. (You run BOSS yourself; this guide
   doesn't.) Watch the logs: a successful load is silent; failures show `binary incompatibility`,
   `requires API version …`, or `requires BOSS version …` (see
   [versioning-and-compatibility.md](versioning-and-compatibility.md)).

## 8. Ship it

Push to `main` and the release workflow builds the jar, cuts a GitHub release, and publishes to the
BOSS Plugin Store. See **[ci-cd.md](ci-cd.md)**. If your plugin should be gated behind a permission,
declare `requiredPermissions` first — **[permissions.md](permissions.md)**.

## Next

- [Plugin API](plugin-api.md) · [Manifest](manifest.md) · [Themes](themes.md) ·
  [Permissions](permissions.md) · [Versioning & compatibility](versioning-and-compatibility.md) ·
  [CI/CD](ci-cd.md)
