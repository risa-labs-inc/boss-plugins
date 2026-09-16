# BOSS Connections & Skills

A unified, governed integration layer for external service connections and version-pinned AI skills in BOSS.

## Why this plugin?

BOSS agents can already work with browsers, terminals, editors, MCP tools, and plugins. However, external services often require separate tooling, authentication, and skill instructions.

Connections & Skills brings these pieces together through one discoverable plugin.

The initial implementation supports:

- GitHub
- Google Sheets
- Version-pinned skills
- Connection health and authentication status
- Governed MCP operations
- Explicit connect/disconnect lifecycle
- CLI-backed integrations

## Features

### GitHub

Uses the official `gh` CLI.

Supported operations:

- Check GitHub CLI installation
- Check authentication status
- List repositories
- Create issues

Example workflow:

1. Install and authenticate `gh`
2. Install Connections & Skills
3. Open the Connections & Skills panel
4. Connect GitHub
5. Use the GitHub tools from a BOSS agent

### Google Sheets

Uses the Google Workspace CLI (`gws`).

Supported operations:

- Check CLI installation
- Check authentication status
- Read spreadsheet values
- Write spreadsheet values

The integration keeps account authentication outside skill files and prompts.

### Skills

Skills are packaged as version-pinned `SKILL.md` resources and loaded on demand.

Included skills:

- `github-issues` — v1.0.0
- `google-sheets` — v1.0.0

Each skill describes the supported capabilities and expected tool usage.

## MCP Tools

The plugin exposes governed MCP tools for:

- Listing available connections
- Checking GitHub status
- Listing GitHub repositories
- Creating GitHub issues
- Checking Google Sheets status
- Reading Google Sheets
- Writing Google Sheets
- Connecting providers
- Disconnecting providers

All tools use BOSS MCP governance and RBAC permissions.

### Permissions

Read operations require:

`connections.read`

Write, connect, and disconnect operations require:

`connections.write`

External operations are only permitted after the corresponding connection has been established.

## Architecture

```text
                    BOSS
                      |
             Connections & Skills
                      |
          +-----------+-----------+
          |                       |
     ConnectionRegistry       SkillLoader
          |                       |
     +----+----+             Versioned
     |         |              SKILL.md
   GitHub   Google
     |       Sheets
     |         |
    gh        gws
     |         |
     +----+----+
          |
    External Services