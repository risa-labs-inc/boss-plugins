# BOSS Plugin Development Guide

The single reference for building, testing, securing, and shipping BOSS dynamic
plugins. Everything here is derived from the host and plugin source as of
July 2026 — file references point at the authoritative code (BossConsole paths
are relative to the private `risa-labs-inc/BossConsole` checkout; plugin paths
are relative to this repo). Where the code and older docs disagree, this guide
follows the code and flags the discrepancy.

**Quick links**: [Anatomy](#2-anatomy-of-a-plugin) ·
[plugin.json reference](#3-pluginjson--complete-field-reference) ·
[Validation & loading](#4-validation-and-load-time-gating) ·
[Classloading](#5-classloader-model) ·
[PluginContext](#6-plugincontext--the-api-surface) ·
[Panels & tabs](#7-registering-panels-and-tabs) ·
[Plugin-to-plugin APIs](#8-plugin-to-plugin-apis) ·
[MCP tools](#9-mcp-tools--exposing-tools-to-in-terminal-agents) ·
[Permissions & RBAC](#10-permissions-and-rbac) ·
[Local development](#11-building-and-local-development) ·
[CI/CD](#12-cicd--the-release-pipeline) ·
[Store & updates](#13-plugin-store-install-and-update-flow) ·
[New-plugin checklist](#14-checklist-creating-a-new-plugin) ·
[Gotchas](#15-known-quirks-and-gotchas)

---

## 1. How the pieces fit

- **BossConsole** is the host desktop app (Kotlin/Compose Multiplatform). It
  discovers plugin JARs at startup, validates them, gives each an isolated
  classloader, and calls `register(context)`.
- **Dynamic plugins** (this repo's submodules) each build to a single JAR
  containing a `DynamicPlugin` implementation and a manifest at
  `META-INF/boss-plugin/plugin.json`.
- **boss-plugin-api** is the compile-time contract. Plugins declare it
  `compileOnly`; at runtime the classes come from the *host* classloader
  (parent-first delegation, §5), so there is exactly one copy of every API
  type and type identity holds across the classloader boundary.
- **The BOSS Plugin Store** (Supabase: `plugins` / `plugin_versions` tables +
  the `plugin-store` edge function) is the distribution channel. CI publishes
  to it on every push to `main` (§12); the host installs and updates from it
  (§13).

Plugin display-name note: the in-app "Plugin Manager" is displayed as
**Toolbox** (display-name-only rebrand; ids and repo names unchanged).

---

## 2. Anatomy of a plugin

```
boss-plugin-<name>/
├── build.gradle.kts                  # version = single source of truth
├── settings.gradle.kts
├── src/main/kotlin/ai/rever/boss/plugin/dynamic/<name>/
│   ├── <Name>DynamicPlugin.kt        # entry point (mainClass)
│   ├── <Name>Component.kt            # PanelComponentWithUI / TabComponentWithUI
│   ├── <Name>ViewModel.kt            # StateFlow-based state
│   └── <Name>McpTools.kt             # optional MCP tool provider
├── src/main/resources/META-INF/boss-plugin/plugin.json
├── .github/workflows/build.yml       # delegates to the shared release workflow
├── CLAUDE.md
└── README.md
```

### Entry point

`ai.rever.boss.plugin.api.Plugin` / `DynamicPlugin`
(boss-plugin-api `DynamicPlugin.kt`):

```kotlin
interface Plugin {
    val pluginId: String
    val displayName: String
    fun register(context: PluginContext)
    fun dispose() {}                       // default no-op
}

interface DynamicPlugin : Plugin {
    val version: String                    // must match plugin.json
    val description: String get() = ""
    val author: String get() = ""
    val url: String get() = ""             // https:// required for store publish
}
```

The host instantiates `mainClass` preferring a Kotlin `object` singleton (via
the `INSTANCE` field), falling back to a no-arg constructor
(`DynamicPluginLoader.kt:169-192`). Everything the plugin does happens in
`register(context)`: register panels/tabs, MCP tool providers, search
providers, plugin-to-plugin APIs. Clean up in `dispose()` — though the host's
`TrackingPluginContext` auto-unregisters everything it saw you register when
the plugin is disabled or unloaded.

### Core conventions (apply to every plugin)

- **UI**: Compose Multiplatform APIs only (never Android). `PanelComponentWithUI`
  with `@Composable Content()`.
- **State**: ViewModel + `StateFlow`.
- **Coroutines**: use `context.pluginScope` — it is cancelled on dispose.
- **Null-safe providers**: every `PluginContext` provider may be `null`
  (§6). Show fallback UI; never crash.
- **Version**: `build.gradle.kts` is the single source of truth;
  `processResources` syncs it into `plugin.json` at build time. Never
  hand-edit the version in `plugin.json`.
- All Kotlin files end with a newline.

---

## 3. plugin.json — complete field reference

Manifest location inside the JAR: `META-INF/boss-plugin/plugin.json`
(`PluginManifestConstants.MANIFEST_PATH`). A `LEGACY_MANIFEST_PATH`
(`META-INF/plugin.json`) constant exists but **no reader uses it** — the
legacy path is dead.

The authoritative schema is the host's
`plugins/plugin-api-core/.../api/PluginManifest.kt` (BossConsole). Parsing is
**lenient** (`ignoreUnknownKeys = true, isLenient = true,
coerceInputValues = true` — `PluginManifestReader.kt:23-27`): unknown fields
are ignored and an invalid enum value silently coerces to its default.

> ⚠️ Two copies of `PluginManifest` exist: the host's `plugin-api-core`
> (authoritative — parsed at runtime) and boss-plugin-api's own copy (lags
> behind; e.g. it lacks `requiredPermissions`/`definedPermissions`). Declare
> the newer fields in `plugin.json` anyway — the host honors them regardless
> of what the API artifact's copy knows about.

### Required fields

Missing any of these makes JSON decoding throw (`PluginManifestException`);
blank-but-present values are rejected by `validateManifest` (§4).

| Field | Type | Rules |
|---|---|---|
| `pluginId` | String | Reverse-domain, ≥ one dot. Parse-time regex `^[a-zA-Z][a-zA-Z0-9_-]*(?:\.[a-zA-Z0-9_-]+)+$`; store publishing is stricter: `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$` (lowercase only). Primary key across the whole system. |
| `displayName` | String | Human name shown in UI. |
| `version` | String | Relaxed semver `X.Y.Z` (optional `-prerelease`/`+build`). Synced from `build.gradle.kts` at build time. |
| `apiVersion` | String | Minimum boss-plugin-api version. Gates load (§4). |
| `mainClass` | String | FQCN implementing `Plugin`. Valid-FQCN regex checked. |

### Optional metadata

| Field | Type | Default | Notes |
|---|---|---|---|
| `manifestVersion` | Int | `1` | Format marker; **not validated anywhere** today. |
| `type` | enum | `panel` | `panel` \| `tab` \| `mixed` \| `hybrid` \| `service` (host enum). ⚠️ boss-plugin-api's local enum lacks `hybrid`; lenient parsing coerces unknown values to `panel`. |
| `description` | String | `""` | Shown in UI / store. |
| `author` | String | `""` | Metadata. |
| `url` | String | `""` | Homepage. Must be `https://` for store publishing. |

### Load-behavior fields

| Field | Type | Default | Effect |
|---|---|---|---|
| `systemPlugin` | Bool | `false` | Marks a system/bundled plugin. Mostly reporting metadata — unload protection is `canUnload`, and system-plugin ordering comes from the host's hard-coded list (§13), not this flag. |
| `loadPriority` | Int | `100` | Lower loads first. Documented bands: 0–10 system, 11–50 core, 51–99 high, 100+ regular. ⚠️ Advisory in practice: the loader sorts the *returned* list but actually loads JARs in filesystem order (`DynamicPluginLoader.kt:382-428`). |
| `canUnload` | Bool | `true` | `false` → runtime unload refused (`PluginUnloadException`). `uninstallPlugin(force = true)` bypasses it (used by reload/upgrade). |
| `sharedPackages` | List\<String\> | `[]` | Extra package prefixes forced **parent-first** in the plugin classloader (§5). Normalized to end with `.`. |
| `minBossVersion` | String | `""` | Minimum host version; blank skips the check. Fail-open: unparseable versions log WARN and allow the load. |
| `minIpcVersion` | String | `""` | Minimum IPC contract version for out-of-process/microkernel plugins; gates store install/spawn. Blank = legacy (WARN). |
| `isolationMode` | String | `"in-process"` | `"in-process"` or `"out-of-process"` (microkernel-spawned). |

### RBAC fields (see §10)

| Field | Type | Default | Effect |
|---|---|---|---|
| `requiresAdmin` | Bool | `false` | Legacy gate: plugin visible/active only for admins. |
| `requiredPermissions` | List\<String\> | `[]` | User must hold **all** listed permissions to see/run the plugin. Empty = any authenticated user. |
| `definedPermissions` | List\<{name, description}\> | `[]` | NEW permissions this plugin introduces to the RBAC catalog. Auto-registered (ungranted) when the store publishes the plugin; an admin grants them to roles afterwards. `name` must be `domain.action`. |

Real examples: `role-creation` declares
`"requiredPermissions": ["role.read", "role.create"]`; `secret-manager`
declares `["secret.read"]`.

### Nested objects

**`panel`** (`PluginPanelConfig`) — declarative placement for panel plugins:

| Key | Default | Notes |
|---|---|---|
| `icon` | `"Extension"` | Material Icons **Outlined** name. |
| `location` | `"left.bottom.bottom"` | `"side.slot.position"` string, parsed by `parseLocationString`; bad input falls back to `Panel.left.bottom`. |
| `order` | `100` | Order within the slot. |
| `panelId` | `null` | Defaults to `"{pluginId}-panel"`. |
| `displayName` | `null` | Falls back to the plugin `displayName`. |

**`sandbox`** (`PluginSandboxConfig`): `maxThreads` (2), `maxMemoryMb` (0 =
unlimited), `enableSandbox` (true), `heartbeatIntervalMs` (5000),
`maxRestartAttempts` (3). ⚠️ Only `maxThreads`, `maxRestartAttempts`, and
`heartbeatIntervalMs` are actually wired into the host `SandboxConfig`
(`DynamicPluginManager.kt:324-328`); `maxMemoryMb`/`enableSandbox` are
currently inert.

**`dependencies`** (`List<PluginDependency>`): `{pluginId, version = "*",
optional = false}`. ⚠️ Modeled, and a `PluginDependencyResolver` module
exists, but dependency ordering is **not enforced** in the load paths today.
Do not rely on it; resolve cross-plugin needs lazily via `getPluginAPI` (§8).

**Orchestration / self-healing fields** (used by the microkernel and repair
engine; safe to omit for ordinary plugins): `capabilities`
(`{action, inputSchemaJson, outputSchemaJson, description}`),
`healthContract` (`{heartbeatIntervalMs = 5000, startupTimeoutMs = 30000}`),
`repairHints` (`{failurePattern, severity: transient|degraded|fatal,
strategy: restart|reset_state|patch_config|patch_source|rollback|escalate,
description, suggestedFix}`), `sourceFiles`, `configFiles`, `behaviorSpec`,
`configSchema`, `stateSnapshotEnabled`, `nativeImagePath`.

**Modeled but currently unenforced** (declare for forward-compat only):
`isDynamic` (default `true`), `unloadActions`
(`{clearCaches, disposeServices, customActions}`).

### Example manifest

```json
{
  "manifestVersion": 1,
  "pluginId": "ai.rever.boss.plugin.dynamic.myplugin",
  "displayName": "My Plugin",
  "version": "1.0.0",
  "apiVersion": "1.0.48",
  "minBossVersion": "9.2.20",
  "mainClass": "ai.rever.boss.plugin.dynamic.myplugin.MyDynamicPlugin",
  "type": "panel",
  "panel": { "icon": "Star", "location": "left.top", "order": 50 },
  "requiredPermissions": ["myplugin.read"],
  "definedPermissions": [
    { "name": "myplugin.read", "description": "View My Plugin data" }
  ],
  "description": "What it does.",
  "author": "Risa Labs",
  "url": "https://github.com/risa-labs-inc/boss-plugin-myplugin"
}
```

---

## 4. Validation and load-time gating

Two distinct stages with **different** API-version logic.

### Stage A — parse-time (`PluginManifestReader.validateManifest`)

Collects all errors, then throws `PluginManifestException`:
- `pluginId` present + reverse-domain regex (≥ one dot)
- `displayName` present
- `version` present + relaxed semver
- `apiVersion` present (format not checked here)
- `mainClass` present + valid FQCN
- API-version compatibility here is **WARN-only** (major-component
  comparison; never rejects).

### Stage B — load-time (`DynamicPluginLoaderImpl.loadPlugin`), hard failures in order

1. **Duplicate id** → `PluginLoadException` ("Plugin already loaded").
2. **API version** → `PluginApiVersionException`. Stricter than Stage A:
   parses `(major, minor)` of the declared `apiVersion` and requires
   `required.major == current.major && current.minor >= required.minor`
   (patch ignored). "Current" is the host's
   `PluginManifestConstants.CURRENT_API_VERSION` in `plugin-api-core`.
3. **minBossVersion** → `PluginBossVersionException` if
   `Version.parse(currentBossVersion) < Version.parse(minBossVersion)`.
   Fail-open on unparseable versions (WARN + allow).
4. **Binary compatibility** → `PluginBinaryIncompatibilityException`
   (§5, `BinaryCompatibilityValidator`). Plugin is marked DISABLED and
   registered in `PluginCrashRegistry`.
5. **Main class** resolvable → else `PluginClassException`.
6. **Implements `Plugin`** → else `PluginClassException`.
7. **Instantiation** (object `INSTANCE` → no-arg ctor) → else
   `PluginClassException`.

RBAC gating (§10) is **not** a load rejection: an inaccessible plugin loads
but is held in `hiddenPlugins` and never registered until the user gains
access (`DynamicPluginManager.kt:1068-1118`).

### Publishing validation

`DynamicPlugin.validateManifestForPublishing` (boss-plugin-api) enforces the
stricter store rules: lowercase reverse-domain `pluginId`, strict `X.Y.Z`
semver, non-blank `mainClass`/`apiVersion`, and an `https://` `url`.

---

## 5. Classloader model

One `PluginClassLoader extends URLClassLoader` per plugin, parent = the host
app classloader (`PluginClassLoaderManager.kt`).

**Hybrid delegation** (`PluginClassLoader.loadClass`):
- Class in a **shared package** → parent-first (host wins).
- Everything else → **child-first** (plugin JAR wins, host as fallback).

**Default shared (parent-first) packages**: `kotlin.`,
`kotlinx.coroutines.`, `kotlinx.serialization.`, `androidx.compose.`,
`com.arkivanov.decompose.`, `com.arkivanov.essenty.`, `java.`, `javax.`,
`sun.`, `com.sun.`, `org.slf4j.`, and the API packages
`ai.rever.boss.plugin.api.` / `.browser.` / `.bookmark.` / `.workspace.` /
`.logging.` / `.ui.` / `.scrollbar.`. Manifest `sharedPackages` are unioned
in.

Practical consequences:
- Declare boss-plugin-api, Compose, coroutines, serialization, decompose and
  slf4j as `compileOnly` — never bundle them. They resolve from the host.
- Anything else you need at runtime **must** be bundled into your plugin JAR
  (child-first), e.g. terminal-tab shades bossterm + its child-first
  transitive deps (ktor, pty4j, jna, …) into its JAR.
- ⚠️ Shading matchers are name/path-based — when bundling, verify the actual
  artifact file names (KMP artifacts get renamed, e.g.
  `compose-ui-desktop-…`).

**Binary compatibility validator** (`BinaryCompatibilityValidator.kt`) runs
before instantiation: it parses the constant pool of every one of *your*
classes (`ai.rever.boss.plugin.*`) and reflectively resolves every
method/field reference into `ai.rever.boss.plugin.*` against the live host —
i.e. it verifies the plugin↔host contract, not your third-party libs.
References into `ai.rever.boss.plugin.runtime.*` are soft-fail (OOP-only
classes). This is why the API follows a strict **default-body convention**:
new `PluginContext`/API methods always ship with default implementations so
old JARs keep loading against newer hosts, and old hosts degrade gracefully
under newer JARs.

**Unload**: `dispose()` → `TrackingPluginContext.unregisterAll()` → sandbox
removed → classloader closed and GC-watched (`ClassLoaderGCWatcher`).
Enable/disable cycles re-`register()`/`unregisterAll()` without unloading the
classloader. System plugins keep their session classloader; true upgrades of
them take effect on app restart (`ApplicationRestarter`).

---

## 6. PluginContext — the API surface

Defined in boss-plugin-api `PluginContext.kt`. **Every provider below except
the first three is nullable and defaults to `null`** — a host may not provide
it (older host, missing feature, non-admin user). Handle `null` with fallback
UI; never crash. Current API version: see `build.gradle.kts` in
`boss-plugin-api/` (1.0.55 at time of writing).

Non-nullable core:

| Member | Type | Purpose |
|---|---|---|
| `panelRegistry` | `PanelRegistry` | Register panel components (§7) |
| `tabRegistry` | `TabRegistry` | Register tab types (§7) |
| `pluginScope` | `CoroutineScope` | Lifecycle-scoped; cancelled on dispose |

Nullable providers:

| Member | Purpose |
|---|---|
| `manifest` | Your parsed `plugin.json` (null for built-ins) |
| `sandbox` | Health reporting: `recordHeartbeat()/recordSuccess()/recordError()` |
| `browserService` | Embedded JxBrowser instances |
| `workspaceDataProvider` | Workspace info / layouts |
| `splitViewOperations` | Open tabs / files / workspaces |
| `activeTabsProvider` | Tab overview across windows |
| `windowId`, `projectPath` | Current window id / selected project path |
| `gitDataProvider` | Git log/status operations |
| `fileSystemDataProvider` | File browsing (codebase) |
| `editorContentProvider` | Code editor rendering |
| `navigationResolverProvider`, `semanticTokenProvider`, `navigationTargetProvider` | PSI navigation / highlighting |
| `secretDataProvider` | Secrets CRUD + sharing (`suspend … : Result<…>`) |
| `authDataProvider` | Auth state, roles, permissions (§10) |
| `userManagementProvider` | Admin-only; null otherwise |
| `roleManagementProvider` | Admin-only; null otherwise |
| `supabaseDataProvider` | Postgrest select/RPC without bundling the SDK (`QueryFilter`) |
| `performanceDataProvider`, `logDataProvider`, `downloadDataProvider`, `bookmarkDataProvider`, `runConfigurationDataProvider`, `projectDataProvider`, `urlHistoryProvider`, `zoomSettingsProvider` | Feature-domain data providers |
| `panelEventProvider`, `settingsProvider`, `contextMenuProvider`, `genericDialogProvider`, `notificationProvider` | Host UI services (events, settings, host-styled menus, dialogs, toasts) |
| `applicationEventBus` | React to app events without polling |
| `tabUpdateProviderFactory` | Update tab title/favicon |
| `dashboardContentProvider` | Host dashboard for about:blank |
| `clipboardProvider`, `filePickerProvider`, `directoryPickerProvider`, `screenCaptureProvider` | System integration (AWT-safe) |
| `pluginStorageFactory` | Persistent per-plugin key-value storage |
| `coBrowseRtcProvider` | WebRTC peer for co-browse |
| `pluginStoreApiKeyProvider` | CI/CD publishing keys |
| `mcpToolRegistry` | Read-side registry of all MCP tools (bridge/observability use, §9) |

Methods (all with default bodies for binary compat):

| Method | Purpose |
|---|---|
| `registerMcpToolProvider(provider)` / `unregisterMcpToolProvider(id)` | Expose MCP tools (§9) |
| `registerSearchProvider(provider)` / `unregisterSearchProvider(id)` | Contribute to global search (`SearchProvider`: `providerId`, `displayName`, `suspend search(query, limit)`) |
| `registerPluginAPI(api)` / `getPluginAPI(Class<T>)` | Plugin-to-plugin APIs (§8) |

There is **no deep-link API** in the plugin surface today.

---

## 7. Registering panels and tabs

### Panels

Implement `PanelInfo` + `PanelComponentWithUI`, register a factory:

```kotlin
object MyPanelInfo : PanelInfo {
    override val id = PanelId("my-panel", defaultOrder = 50, pluginId = MY_PLUGIN_ID)
    override val displayName = "My Panel"
    override val icon = FeatherIcons.Star
    override val defaultSlotPosition = Panel.left.top
}

class MyPanelComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
) : PanelComponentWithUI, ComponentContext by ctx {
    @Composable override fun Content() { /* UI */ }
}

// in register(context):
context.panelRegistry.registerPanel(MyPanelInfo) { ctx, info -> MyPanelComponent(ctx, info) }
```

`Panel` is a sealed position DSL (`Panel.left.top`, `left.bottom.bottom`, …).
Optional lifecycle hooks via `PanelLifecycle`: `onInitialized()`,
`onBeforeReset()`. Panel plugins may alternatively declare placement in the
manifest `panel` block (§3) instead of hardcoding `defaultSlotPosition`.

### Tabs

Implement `TabTypeInfo` + `TabComponentWithUI` (which pairs a `TabTypeInfo`
with the per-instance `TabInfo` config), register with:

```kotlin
context.tabRegistry.registerTabType(MyTabTypeInfo) { tabInfo, ctx ->
    MyTabComponent(tabInfo, ctx)
}
```

`TabRegistry.addUnregisterListener` lets the host close open tabs of your
type when the plugin unloads. `TabIcon` supports vector or image icons;
`TerminalTabInfoInterface` adds `initialCommand`/`workingDirectory` for
terminal-like tabs.

---

## 8. Plugin-to-plugin APIs

There is no RPC layer; cross-plugin calls are plain JVM interfaces resolved
through the host:

- **Expose**: `context.registerPluginAPI(myApiImpl)` — registered under every
  interface the object implements.
- **Consume**: `context.getPluginAPI(TheInterface::class.java)` — returns
  `null` if not (yet) registered.

**Load order is not guaranteed.** Resolve lazily — the API may be `null`
during your `register()` and appear later. Re-query instead of caching a
`null`.

Published APIs you can consume today: `TerminalTabPluginAPI` (embedded
terminal renderers + control), `EditorTabPluginAPI` (editor/LSP settings
panels), `McpServerController` (MCP server state/attach/port control, §9),
`PluginLoaderDelegate` (host-implemented; plugin lifecycle operations used by
Toolbox).

Follow the **default-body convention** when defining an API interface: give
every method a default implementation so older consumers/providers stay
binary-compatible (§5).

---

## 9. MCP tools — exposing tools to in-terminal agents

Any plugin can contribute MCP tools that agents (Claude Code, etc.) running
inside BOSS terminals can call. They surface as `mcp__boss__<tool_name>` and
appear/disappear automatically with your plugin. ~19 plugins already do this
(one `<Name>McpTools.kt` each — good references: `git-status`, `bookmarks`,
`secret-manager`).

### Architecture

```
your plugin ── McpToolProvider ──▶ host McpToolRegistryImpl
   (register(context))                (aggregate + RBAC filter + user toggles)
                                              │ StateFlow<List<RegisteredMcpTool>>
                       terminal-tab bridge ───▶ "boss" MCP server
                       (installDynamicPluginTools)   Ktor SSE on 127.0.0.1:7677
```

### Authoring API (boss-plugin-api `McpTool.kt`)

```kotlin
internal class MyMcpToolProvider(
    override val providerId: String,          // convention: your pluginId
    private val data: MyDataProvider?,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "myplugin_list",           // snake_case, plugin-prefixed
            description = "List My Plugin items for the current project.",
            handler = McpToolHandler { listItems() },          // readOnly defaults true
        ),
        McpToolDefinition(
            name = "myplugin_add",
            description = "Add an item.",
            inputSchema = """{"type":"object","properties":{"title":{"type":"string","description":"Item title"}},"required":["title"]}""",
            readOnly = false,
            handler = McpToolHandler { args ->
                val title = args.string("title")
                    ?: return@McpToolHandler McpToolResult("Missing required argument: title", isError = true)
                addItem(title)
            },
        ),
    )
}

// in register(context):
context.registerMcpToolProvider(MyMcpToolProvider(pluginId, dataProvider))
```

Rules and contracts:

- **`inputSchema` is a JSON-Schema object as a raw JSON string.** The bridge
  consumes only `properties` + `required`; malformed schemas fall back to
  empty. `McpToolArgs` exposes only top-level scalars
  (`string/boolean/int/double/has`); parse nested structures from `args.raw`
  yourself.
- **Handlers are `suspend` and must be cancellation-cooperative.** The host
  wraps each call in a 60 s `withTimeout`; wrap blocking I/O in
  `withContext(Dispatchers.IO)`. Throwing is caught by the host, but prefer
  returning `McpToolResult(isError = true)`.
- **`tools()` is a snapshot, not reactive.** It is queried at registration
  and only re-queried on register/unregister cycles. Gate runtime-varying
  availability *inside the handler*, not by changing the returned list.
- **RBAC per tool**: use the `McpToolDefinition.withRbac(...)` factory to set
  `requiredPermissions` / `requiresAdmin`. ⚠️ They are body properties
  excluded from `equals`/`copy()` — calling `.copy()` on a definition mutated
  via `.apply {}` silently drops the gate. Enforcement is host-side and
  live: tools appear/disappear as the signed-in user's permissions change.
- **Unregistration is automatic** on disable/unload
  (`TrackingPluginContext.unregisterAll()`); no adopter calls
  `unregisterMcpToolProvider` in `dispose()`.
- **Naming**: `<domain>_<verb>` snake_case, prefixed by your plugin's domain
  (`bookmark_add`, `git_stage`, `secret_create`, `codebase_tree`, …). No
  `boss_` prefix — the server name already namespaces the client-visible id
  (`mcp__boss__<name>`). Duplicate names across providers: first registered
  wins.
- **Reserved names** (owned by BossTerm built-ins/terminal-tab; the bridge
  skips them with a warning): `list_tabs`, `get_active_tab`, `list_panes`,
  `read_scrollback`, `search_output`, `get_last_command`,
  `read_debug_console`, `send_input`, `send_signal`, `run_in_panel`,
  `run_command`, `show_image`, `manage_tools`, `run_in_sidebar`, `cli`.

### The `boss` server (hosted by terminal-tab)

- Ktor CIO `embeddedServer` bound to **127.0.0.1 only**, **SSE transport**
  (MCP SDK 0.8.3, mounted at `/`), server name `boss`, default port **7677**
  with a +1…+9 fallback walk if busy. Loopback-`Host`-only (DNS-rebinding
  defense).
- Every PTY gets `BOSS_MCP_SERVER=boss` and `BOSS_MCP_PORT=<bound port>`
  injected so in-shell agents pick the right instance's toolset.
- Client registration happens through each CLI's own command — for Claude
  Code: `claude mcp add --scope user --transport sse boss
  http://127.0.0.1:${BOSS_MCP_PORT:-7677}` (written to `~/.claude.json`
  user scope; no project `.mcp.json` is produced).
- Users can toggle individual tools in Toolbox → MCP; the disabled set
  persists to `~/.boss/mcp-disabled-tools.json`. `mcpToolRegistry.allTools`
  (unfiltered metadata) vs `.tools` (the actually-exposed set).
- Caller-tab resolution (which BOSS tab the calling agent runs in) exists for
  BossTerm's built-in tools but is **not** passed to plugin-contributed
  handlers — plugin tools only receive `McpToolArgs`.

---

## 10. Permissions and RBAC

### The model (host + Supabase)

- Permission keys are `domain.action` — lowercase `[a-z0-9_]`, each part
  1–30 chars, single dot (e.g. `role.read`, `secret.write`).
- Roles are **table-based and dynamic** (`roles`, `permissions`,
  `user_roles`, `role_permissions`, `role_hierarchy`), with two system roles:
  `user` (baseline) and `admin` (full access — `authorize()` short-circuits
  to `true` for admins, so admins implicitly hold every permission including
  brand-new plugin-defined ones).
- Effective permissions are computed server-side
  (`get_effective_permissions`) and baked into the JWT `user_permissions`
  claim. The database (RLS policies calling `authorize('domain.action')`) is
  the real security boundary; every client-side check is UI/optimistic only.
- See `BossConsole/docs/RBAC_GUIDE.md` for the full model. (⚠️ Its companion
  `ROLE_CREATION_GUIDE.md` still describes the retired ENUM approach in
  places; the table-based model is current.)

### Three layers a plugin participates in

1. **Manifest gating** (§3): `requiresAdmin`, `requiredPermissions` control
   whether the plugin is *registered at all* for the current user. Denied
   plugins stay loaded-but-hidden and re-register automatically when access
   is granted (`pluginAccessAllowed`,
   `DynamicPluginManager.kt:1168-1177`; reconciliation at `:1068-1118`).
   Toolbox surfaces missing permissions via `getInaccessiblePlugins()`.
2. **Per-MCP-tool gating** (§9): `withRbac(requiredPermissions = …,
   requiresAdmin = …)` — enforced live in `McpToolRegistryImpl` (admin
   bypass → `requiresAdmin` → `containsAll(requiredPermissions)`).
3. **In-UI gating**: use `authDataProvider`:

   ```kotlin
   interface AuthDataProvider {
       val currentUser: StateFlow<UserData?>       // UserData.roles: List<String>
       val isAdmin: StateFlow<Boolean>
       val userPermissions: StateFlow<Set<String>> // permission names, not role names
       fun hasPermission(permission: String): Boolean
       fun hasAnyPermission(vararg permissions: String): Boolean
   }
   ```

   Example: `role-creation` collects `isAdmin` to show/hide delete controls,
   defaulting open when auth is unavailable and relying on the server to
   reject. Never treat these checks as security — the Supabase RPCs are
   independently gated by `authorize()`.

### Introducing new permissions

Declare them in `definedPermissions` (§3). When CI publishes the plugin, the
store registers them in the catalog **ungranted**; an admin then grants them
to roles (via the role-creation plugin UI or the `mcp__boss__permission_*` /
`role_*` tools). Align the permission names you check in code, in the
manifest, and in per-tool RBAC — the shipped plugins deliberately reuse the
same keys as the server-side RPC authorization (e.g. reads → `role.read`,
mutations → `role.update`).

---

## 11. Building and local development

### Build

```bash
cd <plugin>
./gradlew buildPluginJar     # → build/libs/boss-plugin-<name>-<version>.jar
./gradlew build              # full build (also runs buildPluginJar)
```

- `buildPluginJar` is a custom `Jar` task with an explicit
  `archiveFileName = "boss-plugin-<name>-${version}.jar"`. It packages your
  compiled classes + resources only (thin JAR) unless the plugin deliberately
  shades private deps (terminal-tab/bossterm pattern).
- ⚠️ The default `:jar` task still runs under `./gradlew build` and produces
  `<project>-<version>.jar` alongside. Make sure the two archive names can
  never collide (some plugins set `archiveClassifier = "thin"` on the default
  jar). Both JARs get uploaded as release assets; the store picks the one
  whose name contains "plugin"/"boss", and the host's system-plugin
  downloader skips `*-thin.jar`.

### Version sync into plugin.json

Every plugin must carry the guarded `processResources` block:

```kotlin
tasks.named<ProcessResources>("processResources") {
    inputs.property("pluginVersion", version)      // ← REQUIRED
    filesMatching("**/plugin.json") {
        filter { it.replace(Regex("\"version\":\\s*\"[^\"]*\""), "\"version\": \"$version\"") }
    }
}
```

⚠️ Without the `inputs.property` line, a version-only bump leaves the task
UP-TO-DATE and ships a **stale `plugin.json`** whose version disagrees with
the JAR name and the release tag. Some older plugins still lack the guard —
add it when touching them. The committed `plugin.json` version is expected to
drift from `build.gradle.kts`; that is by design (build-time rewrite).

### boss-plugin-api resolution (CI vs local)

```kotlin
val useLocalDependencies = System.getenv("CI") != "true"
// local:  compileOnly(files("../boss-plugin-api/build/libs/boss-plugin-api-<ver>.jar"))
// CI:     compileOnly(files("build/downloaded-deps/boss-plugin-api.jar"))  // downloaded by the shared workflow
```

- Locally you compile against a **pinned sibling JAR** at
  `../boss-plugin-api/build/libs/boss-plugin-api-<X.Y.Z>.jar`. If the pin is
  stale you get `Unresolved reference` errors for newer API symbols — fix by
  building boss-plugin-api (`./gradlew buildPluginJar`) and bumping the
  pinned version in your `build.gradle.kts`.
- Working in a git worktree? The relative `../boss-plugin-api` path breaks —
  add a symlink to the real checkout next to the worktree.

### Deploy and iterate

```bash
# Production host (~/.boss):
cp build/libs/boss-plugin-<name>-<ver>.jar ~/.boss/plugins/

# Dev-mode host (launched with -Dboss.dev.mode=true or BOSS_DEV_MODE=1):
cp build/libs/boss-plugin-<name>-<ver>.jar ~/.boss_debug/plugins/
```

Dev mode swaps the entire BOSS root from `~/.boss` to `~/.boss_debug`
(`BossDirectories`); the plugins subdir name is the same. Reload the plugin
from Toolbox (or restart the app). While iterating locally, **keep one
version and overwrite the same JAR** — bump the version only when you commit/
release; per-iteration bumps just litter the plugins dir (the host's
`PluginJarReconciler` dedupes by `pluginId`, keeping the highest version, but
don't rely on it to clean up after you).

Do **not** run BossConsole yourself to test (`./gradlew run`) — the user runs
the app. Also: the microkernel runtime JAR is not a loadable plugin; loaders
skip it by name/id.

---

## 12. CI/CD — the release pipeline

### Per-plugin caller workflow

Every plugin carries `.github/workflows/build.yml`:

```yaml
name: Release
on:
  workflow_dispatch:
  push:
    branches: [main]

jobs:
  release:
    uses: risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main
    with:
      boss_plugin_api_version: 'latest'   # or a pin like "1.0.36", or "none"
    secrets:
      BOSS_STORE_PLUGIN_PUBLISH_KEY: ${{ secrets.BOSS_STORE_PLUGIN_PUBLISH_KEY }}
```

Inputs of the shared workflow: `boss_plugin_api_version` (default `latest`)
and `version_increment` (`patch|minor|major|none`, default `patch` — no
caller currently overrides it, so **every push to main is a patch release**).
Secrets: `BOSS_STORE_PLUGIN_PUBLISH_KEY` (required, `publish`-scoped store
API key), `SUPABASE_ANON_KEY` (optional; only plugin-manager needs it).

### What the shared workflow does (in order)

1. Checkout with `GITHUB_TOKEN`.
2. **Bump version first** (before the build): patch-increments
   `version = "X.Y.Z"` in `build.gradle.kts`, commits as
   `github-actions[bot]` with message `🔖 Bump version to X.Y.Z [skip ci]`,
   and pushes to `main`. No CI loop: `[skip ci]` + GitHub never retriggers
   `push` workflows for `GITHUB_TOKEN` pushes.
3. JDK 17 + Gradle setup; download `boss-plugin-api.jar` into
   `build/downloaded-deps/` (the CI side of §11's switch).
4. `./gradlew build`.
5. Delete any existing `v<version>` release (idempotent re-runs), then create
   the GitHub Release with `files: build/libs/*.jar`, tag `v<version>`,
   auto-generated notes, marked latest.
6. **Publish to the Plugin Store**:
   `POST https://api.risaboss.com/functions/v1/plugin-store/github` with
   `X-API-Key: <publish key>` and body `{"githubUrl": "<this repo>"}`.

Consequences worth knowing:
- `main` moves ahead of your feature branch immediately after every merge
  (the bot's bump commit). Rebase/merge accordingly.
- The release ships the **bumped** version — the number you see on `main`
  before merging is one patch behind what will be released.

### Store-side publish

The edge function fetches the repo's latest GitHub release, picks the JAR
asset whose name contains "plugin"/"boss", downloads it, extracts
`plugin.json`, validates permissions, upserts the `plugins` row (keyed on
`pluginId`, author-ownership enforced), rejects duplicate versions, uploads
the JAR to the `plugin-jars` storage bucket, inserts a `plugin_versions` row
(sha256, size, `min_boss_version`, `min_ipc_version`), and registers any
`definedPermissions` (ungranted). Both tables are in the
`supabase_realtime` publication, so running hosts see new versions pushed
live.

⚠️ **50 MB limit**: `/plugin-store/github` buffers the JAR in edge-function
memory and rejects assets ≥ 50 MB. Large/fat JARs must use
`POST /plugin-store/github/metadata` (`{"githubUrl", "sha256"}`), which
extracts the manifest via HTTP range requests, verifies the sha256
server-side (500 MB cap), and stores the GitHub download URL as the JAR path
instead of copying to storage. The microkernel runtime uses this; terminal-
tab will need it if its bundled JAR ever crosses 50 MB.

### Known per-plugin variations

| Plugin | Variation |
|---|---|
| `boss-plugin-api` | `release.yml`, no `with:` block (can't download itself). |
| `plugin-manager` | Additionally passes `SUPABASE_ANON_KEY` (build-time need). |
| `fluck-browser` | Declares explicit `permissions: contents: write` (others rely on org-default token perms for the bump push). |
| `terminal-tab` | Extra `bossterm-autobump.yml` cron (every 30 min): polls Maven Central for a new `bossterm-compose`, bumps `bosstermVersion` + patch version via PR, squash-merges, and explicitly `gh workflow run build.yml` (bot merges don't trigger `push`). |
| `boss-microkernel-runtime` | Fully self-contained workflow (no shared workflow): bumps `plugin.json` via `jq`, builds a fat `-all.jar`, publishes via the `/github/metadata` endpoint. Only workflow with a `paths-ignore` filter. |

After a release, `main` gets the bot bump commit — pull before branching.

---

## 13. Plugin Store install and update flow (host side)

Useful when debugging "why isn't my plugin loading/updating":

- **Install dir**: `~/.boss/plugins` (`~/.boss_debug/plugins` in dev mode);
  download cache in `~/.boss/plugin-cache`. Installed state persists in
  `~/.boss/plugins/installed.json`
  (`{pluginId, jarPath, enabled, sourceUrl, installedVersion}`).
- **Startup sequence** (`PluginStoreSetup.loadPersistedPlugins`): copy
  bundled JARs into the plugins dir if absent/newer → ensure system plugins
  installed (auto-download from GitHub if missing or below the host's
  minimum) → `PluginJarReconciler` dedupes JARs by manifest `pluginId`
  (keeps highest version, repoints `installed.json`) → load persisted
  plugins.
- **System plugins** are a hard-coded host list with explicit priorities
  (boss-plugin-api = 0, microkernel-runtime = 1, plugin-manager = 5,
  terminal-tab/terminal/fluck-browser/editor-tab = 10). Already-installed
  system plugins keep their session classloader; background update checks
  stage newer JARs for the next launch (atomic `.tmp` → rename, IPC-compat
  gated, never downgrades a local dev build).
- **Updates**: `PluginUpdateManager` polls hourly and compares versions
  (IPC-incompatible updates become notices, never auto-installed);
  `PluginStoreRealtimeService` additionally subscribes to `plugins` /
  `plugin_versions` postgres changes for push-based refresh.
- **Bundled plugins dir** (dev): `composeApp/build/bundled-plugins`
  (populated by the host's `downloadBundledPlugins` Gradle task), overridable
  with `-Dboss.bundled.plugins.dir`.

---

## 14. Checklist: creating a new plugin

1. **Repo**: create `risa-labs-inc/boss-plugin-<name>` from an existing small
   plugin (e.g. `bookmarks` or `git-status`) as the template.
2. **Identity**: pick a lowercase reverse-domain `pluginId`
   (`ai.rever.boss.plugin.dynamic.<name>`), set `displayName`, `type`,
   `mainClass`, `apiVersion`, `minBossVersion`; `url` must be the `https://`
   repo URL (store publishing requires it).
3. **build.gradle.kts**: `version` (source of truth), `buildPluginJar` with
   explicit `archiveFileName`, the **guarded** `processResources` version
   sync (§11), the CI/local boss-plugin-api switch, `compileOnly` for all
   host-provided libs.
4. **Implement** `DynamicPlugin.register(context)`: panels/tabs via the
   registries, ViewModel + `StateFlow`, null-safe providers, `pluginScope`
   for coroutines. Implement `dispose()` for anything the tracking context
   can't clean up for you.
5. **RBAC** (if gated): `requiredPermissions` in the manifest; new permission
   keys in `definedPermissions`; gate UI via `authDataProvider`; remember an
   admin must grant new permissions to roles after first publish.
6. **MCP tools** (optional but encouraged): `<Name>McpTools.kt` implementing
   `McpToolProvider`, registered in `register()`; follow §9 naming and RBAC
   rules; avoid reserved names.
7. **Local test**: `./gradlew buildPluginJar` → copy to
   `~/.boss_debug/plugins/` (dev-mode host) → verify load, enable/disable,
   RBAC visibility, MCP tools appearing as `mcp__boss__*`.
8. **CI**: add `.github/workflows/build.yml` delegating to
   `risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main`;
   add the `BOSS_STORE_PLUGIN_PUBLISH_KEY` repo secret; confirm the token has
   `contents: write` (or declare the `permissions:` block like fluck-browser).
9. **Ship**: push to `main` → workflow bumps, builds, releases, publishes.
   Verify the store row and the release assets.
10. **Umbrella**: add the repo as a submodule here
    (`git submodule add https://github.com/risa-labs-inc/boss-plugin-<name>.git <name>`)
    and add a row to the README plugin table.
11. **Docs**: give the plugin a `CLAUDE.md` (copy the house template) and end
    every Kotlin file with a newline.

---

## 15. Known quirks and gotchas

Load-bearing oddities discovered in the code — kept here so nobody rediscovers
them the hard way. Fixing any of these should update this section.

**Manifest / loading**
- Lenient parsing: typo'd field names are silently ignored; an invalid `type`
  silently becomes `panel`. Double-check manifests by hand.
- Two API-version checks disagree: parse-time is WARN-only (major),
  load-time hard-rejects on major mismatch or newer-minor-than-host.
- `minBossVersion` is fail-open on unparseable versions.
- `loadPriority` does not actually reorder JAR loading (filesystem order);
  system-plugin ordering comes from the host's hard-coded list.
- Manifest `dependencies`, `isDynamic`, `unloadActions`,
  `sandbox.maxMemoryMb`, `sandbox.enableSandbox` are modeled but unenforced.
- Two different version comparators coexist in the host (`Version` vs the
  store's `isNewerVersion`) with subtly different pre-release semantics.
- `LEGACY_MANIFEST_PATH` is dead; only `META-INF/boss-plugin/plugin.json`
  is read.

**API surface**
- boss-plugin-api's local copies of `PluginManifest` (and its checked-in
  `plugin.json`, hardcoded `VERSION`, CLAUDE.md version) lag the host and
  the real artifact version — trust `build.gradle.kts` and the host's
  `plugin-api-core`.
- `McpToolDefinition.copy()` drops `requiredPermissions`/`requiresAdmin`;
  always use `withRbac(...)`.
- `getPluginAPI` may be null during `register()` — resolve lazily.

**Build / release**
- Missing `inputs.property("pluginVersion", version)` on `processResources`
  ships a stale `plugin.json` on version-only bumps.
- The default `:jar` runs alongside `buildPluginJar`; keep the archive names
  distinct or classifier the default jar (`"thin"`), since all
  `build/libs/*.jar` get uploaded to the release.
- Local boss-plugin-api pin goes stale → `Unresolved reference …`; rebuild
  the API and bump the pin. Worktrees need a `../boss-plugin-api` symlink.
- Every merge to a plugin's `main` produces a bot bump commit; `main` is
  always one patch ahead of what you merged.
- Store publish rejects JARs ≥ 50 MB on `/github`; use `/github/metadata`.
- Callers without `permissions: contents: write` depend on org-default token
  permissions for the bump push.

**Runtime**
- Ctrl+C on a `./gradlew run` host pane can orphan the app JVM — check for
  survivors before relaunching.
- Plugins denied by RBAC are loaded-but-hidden, not failed — look in
  Toolbox's inaccessible-plugins list before debugging a "missing" plugin.
- Packaged-host quirks (bare PATH, no bundled `bin/java`) have bitten MCP
  registration and daemons before; prefer env-expanded URLs and avoid
  spawning `java` from the app bundle.

---

## Authoritative sources

| Topic | Code |
|---|---|
| Manifest schema | BossConsole `plugins/plugin-api-core/.../api/PluginManifest.kt` |
| Manifest parsing/validation | BossConsole `plugins/plugin-loader/.../PluginManifestReader.kt` |
| Load/unload/classloading | BossConsole `plugins/plugin-loader/.../DynamicPluginLoader.kt`, `PluginClassLoader.kt`, `BinaryCompatibilityValidator.kt` |
| Lifecycle orchestration, RBAC gating | BossConsole `composeApp/.../components/plugin/DynamicPluginManager.kt` |
| Store setup/install/updates | BossConsole `composeApp/.../plugin/PluginStoreSetup.kt`, `PluginPersistence.kt`, `PluginJarReconciler.kt`, `plugin-repository/.../PluginStoreRealtimeService.kt` |
| Plugin API surface | `boss-plugin-api/src/main/kotlin/ai/rever/boss/plugin/api/PluginContext.kt`, `DynamicPlugin.kt`, `McpTool.kt` |
| MCP bridge/server | `terminal-tab/.../McpDynamicTools.kt`, `TerminalTabDynamicPlugin.kt`; BossTerm `BossTermMcpManager.kt`, `McpCliAttacher.kt` |
| Release workflow | each plugin's `.github/workflows/build.yml` → `BossConsole-Releases/.github/workflows/plugin-release.yml` |
| Store publish function | BossConsole `supabase/functions/plugin-store/` (`routes/publish.ts`, `services/github.ts`) |
| RBAC model | BossConsole `docs/RBAC_GUIDE.md`, migration `20260625000000_role_hierarchy_and_granular_rbac.sql` |
