# Manifest Reference (`plugin.json`)

Every plugin ships `src/main/resources/META-INF/boss-plugin/plugin.json`. The host reads it to
decide whether/where/how to load your plugin. The canonical schema is `PluginManifest`
(`BossConsole/plugins/plugin-api-core/.../api/PluginManifest.kt`); the host validates it via
`plugin-loader/.../PluginManifestReader.kt`.

> Note: the `boss-plugin-api` JAR you compile against may lag the host schema slightly. Manifest
> fields are plain JSON read by the host loader — adding a field (e.g. `requiredPermissions`) does
> **not** require a newer api JAR.

## Fields

| Field | Type | Req? | Default | Meaning |
|---|---|---|---|---|
| `manifestVersion` | int | rec. | `1` | Manifest format version. |
| `pluginId` | string | **yes** | — | Reverse-domain id, e.g. `ai.rever.boss.plugin.dynamic.hello`. Must match `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$`. |
| `displayName` | string | **yes** | — | Human name shown in UI. |
| `version` | string | **yes** | — | Semver `X.Y.Z[-pre]`. **Synced from `build.gradle.kts`** by `processResources` — don't hand-edit. |
| `apiVersion` | string | **yes** | — | Plugin API version you target (e.g. `1.0.20`). Host loads you only if major matches and host minor ≥ yours. See [versioning](versioning-and-compatibility.md). |
| `mainClass` | string | **yes** | — | FQN of your `DynamicPlugin` class. |
| `type` | enum | rec. | `panel` | `panel` \| `tab` \| `mixed` \| `service`. |
| `description` | string | no | `""` | Short description. |
| `author` | string | no | `""` | Author/org. |
| `url` | string | no | `""` | Homepage/repo. |
| `minBossVersion` | string | no | `""` | Minimum BOSS version (semver). Host refuses older. Empty = no floor. |
| `dependencies` | list | no | `[]` | Other plugins required: `{pluginId, version ("*"/range), optional}`. |
| `sharedPackages` | list | no | `[]` | Packages this plugin shares with the host classloader (system plugins). |
| `isDynamic` | bool | no | `true` | Supports dynamic load/unload. |
| `loadPriority` | int | no | `100` | Load order: 0–10 system, 11–50 core, 51–99 high, 100+ regular. |
| `canUnload` | bool | no | `true` | May be unloaded at runtime. |
| `unloadActions` | obj | no | — | `{clearCaches, disposeServices, customActions[]}`. |
| `sandbox` | obj | no | — | `{maxThreads=2, maxMemoryMb=0, enableSandbox=true, heartbeatIntervalMs=5000, maxRestartAttempts=3}`. |
| `panel` | obj | for panels | — | Placement: `position`/`location` (slot), `priority`/`order`, `icon`. |
| `systemPlugin` | bool | no | `false` | Bundled-with-BOSS plugin (not for third parties). |
| `requiresAdmin` | bool | no | `false` | Visible/usable only to admins. |
| `requiredPermissions` | list&lt;string&gt; | no | `[]` | Permissions the user must hold to install/see the plugin. See [permissions](permissions.md). |
| `definedPermissions` | list | no | `[]` | New permissions this plugin introduces. See [permissions](permissions.md). |
| `minIpcVersion` | string | no | `""` | Out-of-process plugins only — minimum host IPC version. |

### Runtime placement fields (out-of-process)

Real manifests for sandboxed plugins also carry fields the host **runtime/loader** consumes:

- `isolationMode` — `"out-of-process"` (runs in a child JVM, crash-isolated) or `"in-process"`.
- `fallback` — what to do if isolation fails, e.g. `"in-process"`.
- `stateHolderClass` — FQN of a host-side state holder that survives plugin restarts.

If you don't need crash isolation, omit them (in-process is the simpler default).

## Validation (host-side)

`PluginManifestReader` rejects a manifest that fails:
- `pluginId` reverse-domain regex (above),
- `version` semver `^\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?(?:\+[A-Za-z0-9.]+)?$`,
- non-blank `displayName`, `apiVersion`, `mainClass` (and `apiVersion` major ≤ host).

## Example

```json
{
  "manifestVersion": 1,
  "pluginId": "ai.rever.boss.plugin.dynamic.gitstatus",
  "displayName": "Git Status (Dynamic)",
  "version": "1.0.11",
  "apiVersion": "1.0.20",
  "minBossVersion": "8.16.30",
  "mainClass": "ai.rever.boss.plugin.dynamic.gitstatus.GitStatusDynamicPlugin",
  "type": "panel",
  "description": "View working tree status and staged changes",
  "author": "Risa Labs",
  "url": "https://github.com/risa-labs-inc/boss-plugin-git-status",
  "panel": { "position": "left_bottom", "priority": 14 },
  "sandbox": { "maxThreads": 4, "maxRestartAttempts": 3, "heartbeatIntervalMs": 5000 },
  "isolationMode": "out-of-process",
  "fallback": "in-process",
  "stateHolderClass": "ai.rever.boss.plugin.runtime.stateholders.GitStateHolder"
}
```

See also: [Creating a plugin](creating-a-plugin.md) · [Permissions](permissions.md) ·
[Versioning & compatibility](versioning-and-compatibility.md).
