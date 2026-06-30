# Plugin API

The API you implement and consume lives in `boss-plugin-api`
(`src/main/kotlin/ai/rever/boss/plugin/api/`). It's a `compileOnly` dependency — the **host provides
these classes at runtime**, so only reference documented, host-provided symbols (see the
binary-compatibility rule in [versioning-and-compatibility.md](versioning-and-compatibility.md)).

## Entry point — `DynamicPlugin`

`DynamicPlugin.kt`. Your plugin class implements this; the host instantiates it via `mainClass`
(from the manifest) and calls `register` once, then `dispose` on unload.

```kotlin
interface Plugin {
    val pluginId: String
    val displayName: String
    fun register(context: PluginContext)
    fun dispose() {}
}

interface DynamicPlugin : Plugin {
    val version: String
    val description: String get() = ""
    val author: String get() = ""
    val url: String get() = ""
}
```

In `register`, register UI via `context.panelRegistry` / `context.tabRegistry` and grab any
providers you need.

## Panel plugins

`PanelInterfaces.kt`, `PanelLifecycle.kt`.

```kotlin
interface PanelComponentWithUI : ComponentContext, PanelLifecycle {
    val panelInfo: PanelInfo
    @Composable fun Content()
}

interface PanelInfo {
    val id: PanelId
    val displayName: String
    val icon: ImageVector
    val defaultSlotPosition: Panel        // sidebar slot, e.g. Panel.left.bottom
}

data class PanelId(val panelId: String, val defaultOrder: Int, val pluginId: String = "ai.rever.boss")

interface PanelLifecycle {              // optional hooks
    fun onInitialized() {}
    fun onBeforeReset() {}
}
```

Your component is usually `class XComponent(ctx: ComponentContext, ...) : PanelComponentWithUI,
ComponentContext by ctx` (Decompose component context delegated in).

## Tab plugins

`TabInterfaces.kt`.

```kotlin
interface TabComponentWithUI : ComponentContext {
    val tabTypeInfo: TabTypeInfo
    val config: TabInfo
    @Composable fun Content()
}

interface TabTypeInfo { val typeId: TabTypeId; val displayName: String; val icon: ImageVector }
interface TabInfo { val id: String; val typeId: TabTypeId; val title: String; val icon: ImageVector; val tabIcon: TabIcon? get() = null }

sealed class TabIcon {
    data class Vector(val imageVector: ImageVector, val tint: Color? = null) : TabIcon()
    data class Image(val painter: Painter) : TabIcon()
}
```

## Registries

`PanelRegistry.kt`, `TabRegistry.kt`. Register in `DynamicPlugin.register`; the host calls your
factory to create a component instance per placement.

```kotlin
panelRegistry.registerPanel(content: PanelInfo, factory: (ComponentContext, PanelInfo) -> PanelComponentWithUI)
panelRegistry.unregisterPanel(id: PanelId)

tabRegistry.registerTabType(content: TabTypeInfo, factory: (TabInfo, ComponentContext) -> TabComponentWithUI)
tabRegistry.unregisterTabType(typeId: TabTypeId)
```

## `PluginContext` — host services

`PluginContext.kt`. The single object passed to `register`. **Every provider below is nullable** —
the host wires a provider only when the capability exists / you have access. **Always null-check;
never crash on a missing provider** (show a fallback, disable a feature).

**Core**
- `panelRegistry: PanelRegistry`, `tabRegistry: TabRegistry`
- `pluginScope: CoroutineScope` — lifecycle-tied scope for your coroutines
- `sandbox: PluginSandboxRef?`, `manifest: PluginManifest?` (null for built-ins)
- `windowId: String?`, `projectPath: String?`

**Data providers** (all `?`): `workspaceDataProvider`, `gitDataProvider`, `fileSystemDataProvider`,
`secretDataProvider`, `runConfigurationDataProvider`, `performanceDataProvider`,
`downloadDataProvider`, `bookmarkDataProvider`, `logDataProvider`, `authDataProvider`,
`userManagementProvider`, `roleManagementProvider`, `supabaseDataProvider`, `projectDataProvider`,
`activeTabsProvider`, `urlHistoryProvider`, `zoomSettingsProvider`, `editorContentProvider`,
`navigationResolverProvider` / `semanticTokenProvider` / `navigationTargetProvider` (PSI),
`screenCaptureProvider`, `dashboardContentProvider`, `pluginStoreApiKeyProvider`.

**UI & feedback** (all `?`): `notificationProvider` (toasts), `genericDialogProvider` (text input /
confirm / choice), `contextMenuProvider` (host-styled menus), `settingsProvider` (open settings),
`tabUpdateProviderFactory` (update a tab's title/favicon), `panelEventProvider`,
`filePickerProvider` / `directoryPickerProvider`, `clipboardProvider`, `browserService` (embedded
JxBrowser, if licensed).

**Events & storage** (all `?`): `applicationEventBus`, `pluginStorageFactory` (persistent plugin
data), `coBrowseRtcProvider`.

### Plugin ↔ plugin
```kotlin
fun <T : Any> getPluginAPI(apiClass: Class<T>): T?   // consume another plugin's API
fun registerPluginAPI(api: Any)                       // expose your own
fun registerSearchProvider(provider: SearchProvider)  // contribute to global search
```

## Events — `ApplicationEventBus`

`ApplicationEventBus.kt`. Subscribe to app-wide changes:

```kotlin
applicationEventBus?.fileChanges()          // FileChangeEvent
applicationEventBus?.projectChanges()       // ProjectChangeEvent
applicationEventBus?.tabEvents()            // TabEvent (opened/closed/selected/…)
applicationEventBus?.authEvents()           // AuthEvent (signed_in/out, session_*)
applicationEventBus?.terminalSessionEvents()
applicationEventBus?.pluginLifecycleEvents()
applicationEventBus?.publish(CustomPluginEvent(...))   // plugin↔plugin messaging
```

## Panel slots — `Panel`

`Panel.kt`. A sealed hierarchy navigated fluently to address a sidebar slot:

```kotlin
Panel.left.bottom      // left sidebar, bottom slot
Panel.right.top.top
```

Use it for `PanelInfo.defaultSlotPosition`. The manifest's `panel.position`/`location` mirrors this
(see [manifest.md](manifest.md)).

See also: [Manifest](manifest.md) · [Themes](themes.md) · [Creating a plugin](creating-a-plugin.md).
