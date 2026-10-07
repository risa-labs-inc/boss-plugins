# BOSS Plugin Authoring

Everything you need to build, theme, secure, and ship a BOSS dynamic plugin.

A **plugin** is a self-contained JAR that BOSS (BossConsole) loads at runtime. It depends on
`boss-plugin-api` at compile time (`compileOnly` — the host provides it at runtime), declares
itself in a `plugin.json` manifest, and renders Compose UI inside the host window.

## Read in this order

1. **[Create and publish](create-and-publish.md)** — Tool Creator → coding agent → GitHub → Plugin Store. Start here; [Creating a plugin](creating-a-plugin.md) covers manual setup and implementation.
2. **[Plugin API](plugin-api.md)** — the interfaces you implement and the host services (`PluginContext`) you consume.
3. **[Manifest reference](manifest.md)** — every `plugin.json` field.
4. **[Themes](themes.md)** — the theme tokens and components; how your panel re-skins with the host.
5. **[Permissions](permissions.md)** — gating a plugin behind `requiredPermissions`.
6. **[Versioning & compatibility](versioning-and-compatibility.md)** — `apiVersion`, `minBossVersion`, and binary compatibility.
7. **[CI/CD](ci-cd.md)** — the release pipeline and the Plugin Store.

## Quickstart

Open **Toolbox → Create → Tool Creator** and follow [Create and publish](create-and-publish.md).
For manual scaffolding, copy **[`plugin-template/`](plugin-template/)** and rename `hello` → your plugin name.

## Conventions (apply to every plugin)

- Package + id: `ai.rever.boss.plugin.dynamic.<name>`; produced jar `boss-plugin-<name>-<version>.jar`.
- **Version lives only in `build.gradle.kts`** — `processResources` syncs it into `plugin.json` at build time. Never hand-edit the manifest version.
- Compose Multiplatform APIs only (not Android). All Kotlin files end with a trailing newline.
- Every `PluginContext` provider can be **null** — null-check, never crash.
- `boss-plugin-api` is `compileOnly`; bundle your own third-party deps (`implementation`).

Canonical small example to copy from: the **`git-status`** plugin in this repo.
