# BOSS Plugins

Master repository for all BOSS (Business Operating System Service) plugins, managed as git submodules.

**Building a plugin?** Read the [Plugin Development Guide](PLUGIN_DEVELOPMENT.md) - the exhaustive reference for the `plugin.json` manifest schema, validation and classloading, the `PluginContext` API surface, MCP tools, permissions/RBAC, local development, and the CI/CD release pipeline.

## Quick Start

```bash
# Clone with all plugins
git clone --recurse-submodules https://github.com/risa-labs-inc/boss-plugins.git

# If already cloned without submodules
git submodule update --init --recursive
```

## Plugin Authoring

New to building BOSS plugins? Start with the **[plugin authoring docs](docs/)**:

- **[Creating a plugin](docs/creating-a-plugin.md)** - repo setup → scaffold → build → local test → release
- **[Plugin API](docs/plugin-api.md)** - the interfaces you implement and the host services you consume
- **[Manifest reference](docs/manifest.md)** - every `plugin.json` field
- **[Themes](docs/themes.md)** · **[Permissions](docs/permissions.md)** · **[Versioning & compatibility](docs/versioning-and-compatibility.md)** · **[CI/CD](docs/ci-cd.md)**
- **[`docs/plugin-template/`](docs/plugin-template/)** - a build-ready "Hello panel" starter to copy

## Plugins

| Plugin | Description |
|--------|-------------|
| [admin-role-management](admin-role-management/) | Assign roles to users; the people half of RBAC |
| [analytics](analytics/) | Vendor-neutral product analytics pipeline (PostHog backend, pluggable sinks, consent + PII scrubbing) |
| [bookmarks](bookmarks/) | Bookmarks |
| [boss-microkernel-runtime](boss-microkernel-runtime/) | Shared runtime for out-of-process plugin child JVMs; not itself a plugin |
| [boss-plugin-api](boss-plugin-api/) | The SDK every plugin compiles against, and the bundled system plugin that serves it |
| [codebase](codebase/) | Project file tree: lazy loading, multi-select, context menu, background watcher |
| [console](console/) | Captured stdout/stderr with source filtering, search and per-plugin attribution |
| [deepseek-harness](deepseek-harness/) | Runs DeepSeek Harness (dsh) inside BOSS: installs and supervises it, embeds its web UI in a tab, and exposes dsh_* tools to agents |
| [dna-origami](dna-origami/) | DNA-origami design + oxDNA simulation, agentic chat-first UI |
| [docker](docker/) | Local Docker manager - containers, images, volumes, networks, project Dockerfiles/compose, with live logs and service previews |
| [downloads](downloads/) | Active and completed downloads: progress, speed, pause/resume/reveal |
| [editor-tab](editor-tab/) | Editor tab |
| [fluck-agent](fluck-agent/) | Fluck Agent - chat with Claude about the adjacent browser page |
| [fluck-browser](fluck-browser/) | Fluck browser |
| [fluck-chatgpt](fluck-chatgpt/) | chatgpt.com in a sidebar panel, with a navigation toolbar |
| [git-log](git-log/) | Commit history with cherry-pick, revert and checkout |
| [git-status](git-status/) | Working tree and staging area; the reference example for plugin authors |
| [kubernetes](kubernetes/) | Kubernetes cluster manager - workloads, pods, services, live logs, supervised port-forwards and inline service previews |
| [llmrpa](llmrpa/) | Draft RPA actions from a plain-language instruction via an LLM |
| [organisation](organisation/) | Organisation membership, roles and discovery; opens the org web pages |
| [performance](performance/) | Live JVM telemetry: heap, CPU, GC, threads, network and per-plugin memory |
| [plugin-manager](plugin-manager/) | Toolbox: the plugin store client - install, update, MCP tool toggles, publish |
| [risa-pam-button](risa-pam-button/) | Opens risalabs.ai when the hFAM Boss physical button (Arduino) is pressed |
| [role-creation](role-creation/) | Define roles and permissions; the authoring half of RBAC |
| [rpaengine](rpaengine/) | Replay recorded browser workflows against a live tab |
| [rparecorder](rparecorder/) | Record browser interactions into replayable workflows |
| [run-configurations](run-configurations/) | Auto-detected ways to run the open project, grouped by language |
| [secret-manager](secret-manager/) | Encrypted credentials, Plugin Store API keys, and all AI provider settings |
| [terminal](terminal/) | Sidebar panel that embeds the terminal-tab plugin's terminal |
| [terminal-tab](terminal-tab/) | Terminal tab |
| [tool-evolver](tool-evolver/) | Evolve installed tools with AI CLIs (hot reload + PR); probe memory/leaks/logs |
| [topofmind](topofmind/) | Every open tab across all workspaces, as a split-aware tree |
| [user-secret-list](user-secret-list/) | My Secrets: read-only view of owned and shared-with-you secrets |

## Working with Submodules

```bash
# Update all submodules to latest
git submodule update --remote --merge

# Update a specific plugin
git submodule update --remote --merge <plugin-name>

# Check submodule status
git submodule status
```
