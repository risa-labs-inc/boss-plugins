# CI/CD & Releasing

Every plugin repo ships a tiny workflow that delegates to a shared, reusable release pipeline.
Code changes pushed to `main` build, release, and publish to the BOSS Plugin Store;
documentation-only pushes skip releasing. For first-time setup, follow
[Tool Creator → agent → GitHub → store](create-and-publish.md).

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

1. Bumps the version in `build.gradle.kts` and pushes a `[skip ci]` commit.
2. Downloads the `boss-plugin-api` JAR (`latest`) for the `compileOnly` dependency (CI sets
   `CI=true`, so `build.gradle.kts` uses `build/downloaded-deps/boss-plugin-api.jar`).
3. Runs `./gradlew build`, including the distributable `buildPluginJar` task.
4. Creates a **GitHub release**.
5. Publishes to the **BOSS Plugin Store** (authenticated with `BOSS_STORE_PLUGIN_PUBLISH_KEY`),
   including the manifest's `requiredPermissions` so the store can gate installs (see
   [permissions.md](permissions.md)).

## Versioning

`version` in `build.gradle.kts` is the **single source of truth**; `processResources` syncs it into
`plugin.json` at build time (never hand-edit the manifest version). The release bot's `[skip ci]`
bump happens before the build, so `main` advances as the release starts.

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
