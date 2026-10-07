# Create and publish a BOSS plugin

Use **Toolbox → Create → Tool Creator** to scaffold a plugin, let a coding agent
implement it, and use GitHub Actions to release it to the BOSS Plugin Store.

## 1. Open Tool Creator

Sign in to BOSS and open **Toolbox → Create**. Select **Install Tool Creator**,
then **Create a new plugin…**. Hosted BOSS grants existing and new users
`plugins.create` and `api_key.create`; these enable the publishing controls,
Tool Creator installation, and publish-key creation. Sign out and back in if
your session still has the old permissions. Other deployments must grant these
permissions to their users.

Have JDK 17, Git, and your chosen coding CLI installed and signed in. For GitHub
setup, install the [GitHub CLI](https://cli.github.com/) and run `gh auth login`.

## 2. Scaffold and build with an agent

Enter the plugin name and description, choose the capabilities the agent may
use, select Claude Code, Codex, Gemini, or OpenCode, and choose the project
folder. **Start building** writes the Gradle project, manifest, skeleton UI,
release workflow, and agent instructions, then opens the chosen CLI in BOSS.

The selected capabilities guide the agent; they are not the manifest's
`requiredPermissions`, which controls who may install/use the finished plugin.

The **Create GitHub repo + release CI** option currently creates a private
`risa-labs-inc/boss-plugin-<name>` repository. Use it only if your GitHub account
can create repositories in that organisation. BOSS publishing permission does
not grant GitHub organisation access. Otherwise turn this option off and follow
the personal-repository setup below.

A local CLI or GitHub-connected coding agent can use this task:

```text
Implement the plugin described in this repository. Read its README, any
AGENTS.md/CLAUDE.md, and .claude/skills/tool-creator/SKILL.md for the generated
requirements and BOSS conventions. Work on a feature branch. Run
./gradlew buildPluginJar and relevant checks, and open a pull request with
local testing instructions. Keep version in build.gradle.kts, handle nullable
PluginContext providers, and keep credentials out of source and task text.
```

Test the resulting JAR in BOSS: copy the distributable from `build/libs/` into
`~/.boss/plugins/` (or `~/.boss_debug/plugins/` for a dev-mode instance), then
reload it in Toolbox or restart BOSS yourself. Review the agent's PR before
merging. See [Creating a plugin](creating-a-plugin.md) for the API/build details.

## 3. Connect GitHub and a publish key

For automatic organisation setup, check Tool Creator's log for both repository
creation and installation of `BOSS_STORE_PLUGIN_PUBLISH_KEY`. The initial push
can start CI before the secret is installed; once setup is complete, dispatch a
fresh **Release** run if the first attempt failed.

For your own public repository, run this in the scaffolded project (replace
`YOUR_ACCOUNT` and `my-tool`):

```sh
gh repo create YOUR_ACCOUNT/boss-plugin-my-tool --public --source=. --remote=origin
```

Update the manifest's `url`, the entry class's `url`, and README links to your
actual repository; the scaffold initially names `risa-labs-inc`.

In BOSS, create your own key through Tool Creator's publish-key controls or
**Secret Manager → + → Create Plugin Store publish key**, with the `publish`
scope. Add it under GitHub **Settings → Secrets and variables → Actions →
New repository secret**, named exactly `BOSS_STORE_PLUGIN_PUBLISH_KEY`.
Alternatively, [set the secret with `gh`](https://cli.github.com/manual/gh_secret_set)
and enter its value at the prompt:

```sh
gh secret set BOSS_STORE_PLUGIN_PUBLISH_KEY --repo YOUR_ACCOUNT/boss-plugin-my-tool
```

Keep the scaffolded `.github/workflows/build.yml`. In the repository's
[Actions settings](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository),
allow the shared `risa-labs-inc/BossConsole-Releases` workflow and give the
release job `contents: write` so it can push the version bump and create a
release. Configure the key before your first `git push -u origin main`.

## 4. Release and verify

Merge the reviewed implementation into `main`, or run **Actions → Release →
Run workflow**. The shared pipeline bumps the version, builds/tests, creates a
GitHub release containing the plugin JAR, and publishes it to the store. Pushes
that change only documentation skip releasing; a manual dispatch forces one.

Check that the release workflow's **Publish to BOSS Plugin Store** step succeeds,
then search for the plugin in **Toolbox → Browse** and install it. Future code
changes merged into `main` use the same pipeline. Use the same BOSS publisher
account for later versions: existing plugin ownership is enforced.

For an already-built GitHub release, **Toolbox → Create → Publish Plugin →
From GitHub** is the interactive alternative. See [CI/CD](ci-cd.md) for workflow
configuration. A 403 calls for a refreshed BOSS session, valid publish-scoped
key, and the correct plugin owner; a GitHub setup failure calls for repository
access and Actions/secret configuration.
