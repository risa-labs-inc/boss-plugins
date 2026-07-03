# Themes

BOSS and BossTerm share one visual language — **"Operator's Console"**. Your plugin gets it for
free by using the host-provided theme tokens instead of hardcoded colors. These symbols live in
`plugin-ui-core` (`ai.rever.boss.plugin.ui`) and are provided by the host at runtime.

## Wrap your UI in `BossTheme`

```kotlin
import ai.rever.boss.plugin.ui.BossTheme

@Composable
override fun Content() {
    BossTheme {
        // your panel UI — Material components inherit the BOSS palette here
    }
}
```

## Use theme tokens, not literals

`BossThemeColors` are the semantic tokens to paint with:

| Token | Use for |
|---|---|
| `BackgroundColor` | content-area / panel background (the deepest "ink" surface) |
| `SurfaceColor` | cards / raised surfaces / sidebars |
| `BorderColor` | dividers / hairlines |
| `TextPrimary` / `TextSecondary` / `TextMuted` | text by emphasis |
| `AccentColor` | selection / focus / primary action (the amber "signal") |
| `SecondaryColor` | links / data accents |
| `ErrorColor` / `SuccessColor` / `WarningColor` | status |

```kotlin
Text("Ready", color = BossThemeColors.TextPrimary)
Box(Modifier.background(BossThemeColors.SurfaceColor).border(1.dp, BossThemeColors.BorderColor))
```

Top-level aliases (`BossDarkBackground`, `BossDarkAccent`, … `ContextMenuBackground` …) and the
`BossColors` object map to the same values if you prefer those names.

## These tokens are reactive

The host now ships **three selectable themes** — **Operator** (signature dark), **Daylight**
(light), and **Clean** (neutral charcoal). `BossThemeColors` resolve through the **active** theme,
so when the user switches themes in Settings, **your panel re-skins live** — no work needed beyond
using the tokens. Two consequences:

- Don't capture a token inside `remember { }` (it would freeze at the first value). Read it directly
  in composition so it recomposes on switch.
- Don't assume the theme is dark. Pick tokens by **role** (`TextPrimary`, `SurfaceColor`), not by
  apparent color, so light themes look right too.

## Reusable components

`plugin-ui-core` also exposes ready-made, on-theme building blocks (in `BossComponents`): `BossCard`,
`BossSection`, `BossTextField`, `BossTextArea`, `BossPrimaryButton`, `BossSecondaryButton`,
`BossToggle`, `BossInfoRow`, `BossSearchBar`, `BossBadge`, `BossEmptyState`, … Prefer these for
settings-style UI so your plugin matches the host out of the box.

## ⚠️ Binary-compatibility rule

`plugin-ui-core` symbols are resolved against the **host's** classes at load time. The host's
`BinaryCompatibilityValidator` scans your jar's constant pool and **rejects the plugin** (state
`DISABLED`, "binary incompatibility" in the logs) if any `ai.rever.boss.plugin.*` method/field you
reference is missing in the running host.

What this means for you:
- Only call **documented, host-provided** API/theme symbols. Don't reflect on or depend on internal
  or newer-than-the-host symbols.
- This cuts both ways for host maintainers: a public `@Composable` signature in `plugin-ui-core`
  must never change in place (add overloads instead), or every plugin compiled against the old
  signature fails to load.

See also: [Plugin API](plugin-api.md) · [Versioning & compatibility](versioning-and-compatibility.md).
