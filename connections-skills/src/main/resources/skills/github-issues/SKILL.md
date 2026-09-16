# GitHub Issues Skill

- Skill ID: github-issues
- Version: 1.0.0
- Provider: github
- Runtime: GitHub CLI (`gh`)

## Purpose

Work with GitHub repositories and issues through the authenticated GitHub CLI.

## Capabilities

- Read repositories available to the authenticated account.
- Create issues in a specified `owner/name` repository.

## Requirements

- `gh` must be installed.
- The GitHub CLI must be authenticated.
- BOSS connection state must be `connected`.
- Write operations require the `connections.write` permission.

## Safety

- Never place GitHub credentials or tokens in skill files.
- Repository names must use the `owner/name` form.
- Issue titles and bodies are passed directly to the GitHub CLI as arguments.
