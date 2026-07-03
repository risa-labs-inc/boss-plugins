# BOSS Plugins

Master repository for all BOSS (Business Operating System Service) plugins, managed as git submodules.

## Quick Start

```bash
# Clone with all plugins
git clone --recurse-submodules https://github.com/risa-labs-inc/boss-plugins.git

# If already cloned without submodules
git submodule update --init --recursive
```

## Plugins

| Plugin | Description |
|--------|-------------|
| [admin-role-management](admin-role-management/) | Admin role management |
| [analytics](analytics/) | Vendor-neutral product analytics pipeline (PostHog backend, pluggable sinks, consent + PII scrubbing) |
| [boss-atlas](boss-atlas/) | Chat with Claude about the adjacent browser page |
| [bookmarks](bookmarks/) | Bookmarks |
| [boss-microkernel-runtime](boss-microkernel-runtime/) | Microkernel runtime (process/gRPC services for plugins) |
| [boss-plugin-api](boss-plugin-api/) | Plugin API |
| [codebase](codebase/) | Codebase viewer |
| [console](console/) | Console |
| [dna-origami](dna-origami/) | DNA-origami design + oxDNA simulation, agentic chat-first UI |
| [downloads](downloads/) | Downloads manager |
| [editor-tab](editor-tab/) | Editor tab |
| [fluck-browser](fluck-browser/) | Fluck browser |
| [fluck-chatgpt](fluck-chatgpt/) | Fluck ChatGPT |
| [git-log](git-log/) | Git log viewer |
| [git-status](git-status/) | Git status |
| [llmrpa](llmrpa/) | LLM RPA |
| [performance](performance/) | Performance monitoring |
| [plugin-manager](plugin-manager/) | Toolbox (plugin store: install, uninstall, update) |
| [risa-pam-button](risa-pam-button/) | Opens risalabs.ai when the hFAM Boss physical button (Arduino) is pressed |
| [role-creation](role-creation/) | Role creation |
| [rpaengine](rpaengine/) | RPA engine |
| [rparecorder](rparecorder/) | RPA recorder |
| [run-configurations](run-configurations/) | Run configurations |
| [secret-manager](secret-manager/) | Secret manager |
| [terminal](terminal/) | Terminal |
| [terminal-tab](terminal-tab/) | Terminal tab |
| [topofmind](topofmind/) | Top of mind |
| [user-secret-list](user-secret-list/) | User secret list |

## Working with Submodules

```bash
# Update all submodules to latest
git submodule update --remote --merge

# Update a specific plugin
git submodule update --remote --merge <plugin-name>

# Check submodule status
git submodule status
```
