# CI/CD & Releasing

Every plugin repo ships a tiny workflow that delegates to a shared, reusable release pipeline.
Pushing to `main` builds, releases, and publishes the plugin to the BOSS Plugin Store.

## The workflow

`.github/workflows/build.yml` (identical across plugins):

```yaml
name: Release
on:
  workflow_dispatch:          # manual trigger
  push:
    branches: [main]          # release on merge to main
jobs:
  release:
    uses: risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main
    with:
      boss_plugin_api_version: 'latest'
    secrets:
      BOSS_STORE_PLUGIN_PUBLISH_KEY: ${{ secrets.BOSS_STORE_PLUGIN_PUBLISH_KEY }}
```

The reusable workflow (`risa-labs-inc/BossConsole-Releases/.github/workflows/plugin-release.yml@main`)
does the heavy lifting:

1. Downloads the `boss-plugin-api` JAR (`latest`) for the `compileOnly` dependency (CI sets
   `CI=true`, so `build.gradle.kts` uses `build/downloaded-deps/boss-plugin-api.jar`).
2. Builds the plugin: `./gradlew buildPluginJar` → `build/libs/boss-plugin-<name>-<version>.jar`.
3. Creates a **GitHub release**.
4. Publishes to the **BOSS Plugin Store** (authenticated with `BOSS_STORE_PLUGIN_PUBLISH_KEY`),
   including the manifest's `requiredPermissions` so the store can gate installs (see
   [permissions.md](permissions.md)).
5. A release bot bumps the patch version with a `[skip ci]` commit (so the bump itself doesn't
   re-trigger a release).

## Versioning

`version` in `build.gradle.kts` is the **single source of truth**; `processResources` syncs it into
`plugin.json` at build time (never hand-edit the manifest version). The release bot's `[skip ci]`
bump means `main` moves one patch ahead right after a release.

```bash
# Cut a release:
git checkout main && git pull
# (make your changes)
git commit -am "feat: ..."        # version in build.gradle.kts as needed
git push origin main              # ← triggers the release workflow
```

To release work-in-progress safely, develop on a branch and open a PR; merging the PR to `main` is
what releases. (See `BossConsole`/plugin docs for branch conventions.)

## After a release: update the umbrella

`boss_plugins` tracks each plugin as a git **submodule** pinned to a commit. After a plugin
releases (its `main` advances with the new version + the `[skip ci]` bump), update the umbrella
pointer so the workspace references the released version:

```bash
cd boss_plugins
git -C <plugin> fetch origin && git -C <plugin> checkout --detach origin/main
git add <plugin>
git commit -m "Update <plugin> submodule pointer to released version"
git push origin main
```

(Or `git submodule update --remote <plugin>` then commit.) Pushing the umbrella `main` only moves
submodule pointers — it does **not** trigger plugin releases.

## Required secret

`BOSS_STORE_PLUGIN_PUBLISH_KEY` must be configured in the plugin repo (or org) secrets for the store
publish step. Without it, the build/release steps run but publishing fails.

See also: [Creating a plugin](creating-a-plugin.md) · [Permissions](permissions.md) ·
[Versioning & compatibility](versioning-and-compatibility.md).
