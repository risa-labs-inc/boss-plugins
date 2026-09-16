# Google Sheets Skill

- Skill ID: google-sheets
- Version: 1.0.0
- Provider: google-sheets
- Runtime: Google Workspace CLI (`gws`)

## Purpose

Read and update Google Sheets through the authenticated Google Workspace CLI.

## Capabilities

- Read spreadsheet ranges using A1 notation.
- Write a JSON ValueRange to spreadsheet ranges.

## Requirements

- `gws` must be installed.
- Google authentication must be configured.
- BOSS connection state must be `connected`.
- Write operations require the `connections.write` permission.

## Safety

- Never place Google credentials, tokens, or secrets in skill files.
- Spreadsheet IDs and ranges must be supplied by the caller.
- Validate connection state before executing spreadsheet operations.
