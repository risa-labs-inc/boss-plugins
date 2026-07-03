# Permissions

BOSS plugins can be gated behind **permissions** so only authorized users can install or see them.
Permissions are declared in `plugin.json` and enforced both by the Plugin Store (at install) and by
the host (at visibility/runtime).

## Declaring permissions

Two manifest fields (see [manifest.md](manifest.md)):

```json
{
  "requiredPermissions": ["secret.read"],
  "definedPermissions": []
}
```

- **`requiredPermissions`** — the permissions a user must hold to install/use this plugin. The user
  must hold **all** of them.
- **`definedPermissions`** — *new* permissions this plugin introduces into the system (so admins can
  grant them to roles). Most plugins leave this empty and reuse existing permissions.

**Naming**: `domain.action`, lowercase — e.g. `secret.read`, `role.read`, `role.create`,
`role.assign`. An empty `requiredPermissions` (the default) means the plugin is open to all
authenticated users.

## How it's enforced

**1. Plugin Store install gate.** The store's download endpoint
(`supabase/functions/plugin-store/routes/download.ts`) returns **403** if a non-admin requests a
plugin whose `requiredPermissions` they don't fully hold — with a message naming the missing
permission(s). Empty list ⇒ open to everyone. **Admins bypass.** (Added in PR #806.)

**2. Host visibility/runtime gate.** `DynamicPluginManager.pluginAccessAllowed(...)` decides whether
a user sees/can use a loaded plugin:

```
if (requiresAdmin && !isAdmin) -> false      // legacy admin-only flag
if (isAdmin)                   -> true        // admins bypass all permission checks
else                           -> userPermissions.containsAll(requiredPermissions)
```

**3. Where permissions come from.** The user's effective permissions (including those inherited via
roles) are carried in the JWT `user_permissions` claim; the host observes the signed-in user and
recomputes access on auth changes.

## Example

`secret-manager` reads secrets, so it gates on `secret.read`:

```json
{
  "pluginId": "ai.rever.boss.plugin.dynamic.secretmanager",
  "requiredPermissions": ["secret.read"]
}
```

A user without `secret.read` (and not an admin) can't install it from the store and won't see it in
the host. An admin sees everything.

## Guidelines

- Request the **least** you need; prefer existing permissions over `definedPermissions`.
- Treat permissions as **UI/install gating**, not a security boundary for secrets — the providers
  you call (e.g. `secretDataProvider`) enforce their own server-side authorization too.
- If your plugin should be admin-only regardless of fine-grained permissions, set `requiresAdmin: true`.

See also: [Manifest](manifest.md) · [CI/CD](ci-cd.md) (the store publishes `requiredPermissions`).
