# Versioning & Compatibility

Three manifest version gates apply to in-process plugins: **`apiVersion`**, **`minBossVersion`**,
and **`minApiVersion`**. The host also checks **binary compatibility**. A failure on any one
disables the plugin (it won't load) and surfaces an error in the logs / crash registry. The checks live in
`plugin-loader/.../DynamicPluginLoader.kt` (+ `BinaryCompatibilityValidator.kt`, `Version.kt`).

## `apiVersion` (manifest)

The Plugin API version your code targets, e.g. `"1.0.20"`. The host compares it to its own
`PluginManifestConstants.CURRENT_API_VERSION`:

> **Major must match exactly; the host's minor must be ≥ your required minor.**

Patch versions are ignored: `apiVersion 1.0.20` passes this gate on a host at `1.0.18`, while
`1.1.0` requires host API major 1 and minor 1 or later. A `2.x` plugin never loads on a `1.x` host.
Mismatch → `PluginApiVersionException` → plugin **disabled**. Set `apiVersion` to the lowest API
major/minor contract you require; use the other gates below for SDK-only and host-implemented
capabilities.

## `minBossVersion` (manifest)

The minimum BOSS app version, semver e.g. `"8.16.30"`. The host loads you only if its version ≥
yours (`Version.parse` comparison; prerelease order `alpha < beta < rc < stable`). Mismatch →
`PluginBossVersionException` → "requires newer BOSS". If either version string is malformed the
check **fails open** (loads with a warning). Leave empty if you have no hard floor.

<a id="which-version-gate"></a>

## `minApiVersion` (manifest)

The minimum **boss-plugin-api** version: not the host, but the runtime API layer the host resolves
from the installed api jar. Empty skips the check. An unknown installed API version also skips
the check with a warning. Unparseable versions fail open, like `minBossVersion`. Violation raises
`PluginApiLevelException`, which exists to turn a
"class not found" binary-compatibility failure into an actionable "requires API x.y.z, installed
a.b.c".

**This is a different gate from `apiVersion` above, and the difference decides which field a given
requirement belongs in.** The api jar is updatable at runtime, independently of the host, so what
ships through it and what does not is the whole question:

| What you started using | Ships via | Gate with |
|---|---|---|
| A required API major/minor contract | the host API contract | `apiVersion` |
| A brand-new interface, object or data class from the api jar | the api jar alone | `minApiVersion` |
| A new member on an existing type the host implements | **not** the jar | `minBossVersion` |

The host-implemented row is the trap. Types the host implements are marked `@HostImplemented`, and the host
compiles in its own copy, which **shadows** the jar's newer one. A new provider on `PluginContext`
is a member addition to a `@HostImplemented` type, so a newer api jar does not deliver it and
`minApiVersion` will not gate it; the requirement is a host contract and belongs in
`minBossVersion`.

Using `minApiVersion` requires a host at least as new as the platform release that introduced the
ApiClassLoader.

## `minIpcVersion` (out-of-process plugins only)

For `isolationMode: out-of-process` plugins, declares the minimum host IPC protocol version. The
host refuses to spawn the child if its IPC major differs or its version is below your `minIpcVersion`.
Blank = treated as legacy/unknown (accepted with a warning). In-process plugins ignore this.

## Binary compatibility

Beyond declared versions, the host **structurally verifies** your jar at load time: it parses the
constant pool and checks that every `ai.rever.boss.plugin.*` class/method/field you reference
actually resolves against the running host. Any missing symbol →
`PluginBinaryIncompatibilityException` → plugin **disabled** ("binary incompatibility"). Third-party
classes you bundle yourself are not checked.

**Author takeaway:** only use documented host-provided API/theme symbols (see
[plugin-api.md](plugin-api.md), [themes.md](themes.md)); don't depend on internals or on symbols
newer than the hosts you target. **Host-maintainer takeaway:** never change a public `@Composable`
(or other public) JVM signature in `plugin-ui-core`/`boss-plugin-api` in place — add an overload —
or every plugin compiled against the old signature breaks.

<a id="choosing-the-api-pin"></a>

## Choosing the `boss-plugin-api` pin

`build.gradle.kts` pins the api jar for local builds:

```kotlin
compileOnly(files("$bossPluginApiPath/build/libs/boss-plugin-api-1.0.47.jar"))
```

- Pick a jar that **exists** in `../boss-plugin-api/build/libs/` (build it there, or use the version
  CI resolves). A stale pin (file not present) makes the local build fail to resolve.
- This pin is **compile-time only**: it controls which symbols you can compile against. At runtime,
  host-compiled types take precedence over the installed api jar. Set `apiVersion` for the required
  API major/minor contract, `minApiVersion` for SDK-only additions, and `minBossVersion` for
  host-implemented capabilities. A newer compile-time pin alone does not require raising all
  three gates; declare the requirements of the symbols you actually use.
- In CI, the workflow downloads `boss_plugin_api_version: 'latest'` (see [ci-cd.md](ci-cd.md)).

## Quick reference

| Field / check | Rule | Failure |
|---|---|---|
| `apiVersion` | major ==, host minor ≥ yours | `PluginApiVersionException` → disabled |
| `minBossVersion` | host ≥ yours (semver; fail-open if unparseable) | `PluginBossVersionException` → disabled |
| `minApiVersion` | installed api jar ≥ yours (fail-open if unknown or unparseable) | `PluginApiLevelException` → disabled |
| `minIpcVersion` (OOP) | host IPC major ==, host ≥ yours | not spawned |
| binary compat | all referenced `ai.rever.boss.plugin.*` symbols resolve | `PluginBinaryIncompatibilityException` → disabled |

See also: [Manifest](manifest.md) · [Themes](themes.md) · [CI/CD](ci-cd.md).
