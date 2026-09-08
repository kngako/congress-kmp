# Bringing the UI in line with Material Design 3

What the M3 foundations actually require, where this app's 43 screens stand
against them today, and a phased order of work that makes each phase mechanical
by the time it starts.

The app already builds on Material 3 — `compose-material3:1.10.0-alpha05`, a
generated `ColorScheme`, `Scaffold`/`TopAppBar`/`ListItem` throughout. What is
missing is not the library. It is that **the design decisions live at the call
site rather than in the theme**, so there is no single place to change and no
way to check whether a screen conforms. Every phase below moves one class of
decision out of 18,000 lines of screen code and into something a test can read.

Sources are the current foundations pages on <https://m3.material.io/foundations>,
read September 2026 — after the May 2026 revision that renamed window size
classes to breakpoints and published the spacing system as tokens. Numbers
quoted here are from those pages, not from memory of older M3.

## What the spec asks for

### The seven foundations

`/foundations` lists seven areas. Each maps onto something concrete in this
codebase:

| foundation | what it governs | where it lands here |
|---|---|---|
| Accessibility | contrast, target size, labels, focus order, structure | `Icon` labels, `.clickable` targets, colour pairings, keyboard flow on desktop |
| Content design | UX writing, sentence case, alt text, global writing | 334 string literals in composables |
| Customizing Material | brand colour through the role system, dynamic colour | `Color.kt`, `Theme.kt`, `BluePill`/`RedPill` |
| Design tokens | style values named by role, never hardcoded | `10.dp` × 132, `20.dp` × 115 |
| Interaction states | enabled/disabled/hover/focus/press/drag, state layers | `Clickable.kt`, custom `Card` colours |
| Layout | breakpoints, panes, grids, spacing, bidirectionality | one phone layout on three platforms |
| Material A-Z | shared vocabulary | naming in this document |

### The numbers

**Breakpoints** (renamed from window size classes; apply to Android and web,
and are the right vocabulary for the desktop target too):

| breakpoint | width | panes | navigation |
|---|---|---|---|
| Compact | under 600dp | 1 | navigation bar, modal expanded rail |
| Medium | 600–839dp | 1 recommended, 2 possible | collapsed navigation rail |
| Expanded | 840–1199dp | 2 recommended | collapsed or standard expanded rail |
| Large | 1200–1599dp | 2 recommended | standard expanded rail |
| Extra-large | 1600dp+ | up to 3 | standard expanded rail |

Two rules from the same page are easy to miss and both bite here: *"across all
breakpoints, adjust margins and type styles to keep text between 40–60
characters per line"*, and *"don't use two panes in medium layouts with high
information density"*.

**Spacing.** The system is an 8dp scale where `space100 = 8dp`. Material defines
only the recommended stops, including sub-8 nested units:

| token | dp | | token | dp |
|---|---|---|---|---|
| space0 | 0 | | space250 | 20 |
| space25 | 2 | | space300 | 24 |
| space50 | 4 | | space400 | 32 |
| space75 | 6 | | space450 | 36 |
| space100 | 8 | | space500 | 40 |
| space125 | 10 | | space600 | 48 |
| space150 | 12 | | space700 | 56 |
| space175 | 14 | | space800 | 64 |
| space200 | 16 | | space900 | 72 |

Spacing has three categories with different rules: **padding** (inside an
element), **gap** (between elements in a container), **margin** (outside an
element). The spec is explicit that margins are a last resort — *"define padding
and gaps on the parent container"*, *"avoid defining margins on child
elements"*. The note that these tokens are Compose-only is in our favour: this
is a Compose app.

**Targets.** Touch targets at least 48×48dp; pointer targets at least 44×44dp;
targets separated by 8dp or more. An icon may be 24dp while its target is 48dp —
the padding is part of the target, not decoration.

**Contrast.** Small text at least 4.5:1 against its background; large text
(14pt bold / 18pt regular and up) and graphics at least 3:1. Disabled states are
exempt. Clustered non-text elements — a group of buttons — need 3:1 between
container and background; a standalone element such as a FAB does not.

**State layers.** A fixed overlay in the content colour: hover 8%, focus 10%,
press 10%, drag 16%, disabled 38%. State layer 40dp, interactive target 48dp.

### What the May 2026 revision changed

Worth knowing before reading older guidance or older code:

- window size class → **breakpoint**, and there are now five, not three;
- responsive design → **adaptive design**;
- the spacing system is published as tokens, on an 8dp scale, Compose-first;
- the **layout scaffold** (bars, rails, panes, rulers) is the recommended
  structure, replacing hand-rolled adaptive branches;
- there are now expressive spacing guidelines, and a stated position that
  spacing carries product personality rather than being neutral.

## Where this app stands

Counts below are over `composeApp/src/commonMain/kotlin/press/mantra/compose/ui`
unless stated. Phase 0 turns them into a script so they can be re-run.

### The theme is incomplete, and two entry points bypass it

`Color.kt` defines six schemes — light, dark, and medium/high contrast variants
of each — and `Theme.kt` wires four of them into `lightColorScheme`/
`darkColorScheme`. That is more than most apps do, and the generated pairs hold
up: every `onX`-on-`X` pair in every scheme clears 4.5:1, the tightest being
`onPrimaryContainer` on `primaryContainer` at 4.61:1 (light) and 4.56:1 (dark).

Three gaps:

1. **Twelve roles are never set.** `primaryFixed`, `primaryFixedDim`,
   `onPrimaryFixed`, `onPrimaryFixedVariant` and the secondary/tertiary
   equivalents are absent from both schemes, so they fall through to
   `ColorLightTokens.PrimaryFixed` — `PaletteTokens.Primary90`, which is
   `#EADDFF`. Any component reaching for a fixed role paints **Material baseline
   lavender** into a monochrome app, in light and dark alike. Nothing uses them
   today; the trap springs the first time an expressive component does.

2. **The medium and high contrast schemes are dead code.** All four are declared
   `private val`; `TorchTheme` only ever selects `darkScheme` or `lightScheme`.
   There is no plumbing to the platform's contrast setting, so the two schemes
   that would honour it are unreachable. The accessibility foundation's first
   principle is *honour individuals* — *"supporting varying preferences and
   choices"* — and the values to do it are already sitting in the file.

3. **`AuxTypography` is `Typography()`** — the baseline, in package
   `com.example.ui.theme`, in a file otherwise unused. No shapes and no motion
   scheme are passed to `MaterialTheme` at all.

Two entry points render outside the theme:

- `composeApp/src/jvmMain/kotlin/press/mantra/desktop/Main.kt:126` —
  `PassphraseGate` sits in the `else` branch beside `MantraApp`, so it composes
  under the **default** `MaterialTheme`. Its `MaterialTheme.colorScheme.error`
  and `typography.headlineSmall` are baseline M3, not this app's. It is the
  first screen a desktop user sees.
- `Profile.kt:183` and 50 other `@Preview` bodies wrap in `TorchTheme` by hand,
  which is correct, but there is no preview that exercises dark, high-contrast
  or a wide window — so nothing catches the two problems below.

### Colour is decided at the call site, and one pairing is invisible

Eleven sites hardcode a `Color`, and 14 more derive one with `.copy(alpha = …)`.
Measured against the light scheme:

| site | pairing | ratio | needs |
|---|---|---|---|
| `ProposalListScreen.kt:228` | `ListItem` headline (`onSurface`) on a `Card` of `primaryContainer` | **1.00:1** | 4.5:1 |
| `HomeScreen.kt:113` | `titleContentColor = primary` on `containerColor = primaryContainer` | **1.22:1** | 4.5:1 |
| `ArticleCard.kt:67`, `QuotedNote.kt:101`, `QuotedAddressableNote.kt:62`, `LiveStreamCardContent.kt:42` | `onSurfaceVariant.copy(alpha = 0.5f)` on surface — these four are `CircularProgressIndicator` colours, not text, so the threshold is 3:1 rather than 4.5:1. Still under it. | 2.49:1 | 3:1 |
| `CreateProfileScreen.kt:329` | `Color.DarkGray` on `BluePill` | 2.90:1 | 4.5:1 |
| `ArticleCard.kt:154` | `onSurfaceVariant.copy(alpha = 0.7f)` on surface | 3.96:1 | 4.5:1 |
| `CreateProfileScreen.kt:311` | `Color.White` on `RedPill` (75% alpha over surface) | 3.50:1 | 4.5:1 |
| `LiveStreamCardContent.kt:86` | `Color.White` on `#E53935` | 4.23:1 | 4.5:1 |

The first is the worst and is worth spelling out, because it is not obvious from
reading the call:

```kotlin
Card(
    onClick = onClick,
    colors = if (proposal.awaitsYou) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
```

`cardColors(containerColor = …)` does derive `contentColor = contentColorFor(…)`,
so `LocalContentColor` inside the card is right. But `ListItem` does not read
`LocalContentColor` — its headline colour comes from `ListTokens.ItemLabelTextColor`,
which is `onSurface`, and the call site overrides only `containerColor`. In the
light scheme `onSurface` and `primaryContainer` are both `#1B1B1B`. **The
proposals that await your signature are the ones rendered invisible.**

`BluePill` and `RedPill` are brand colours held as raw `Color` values outside the
role system. The customization foundation's whole argument is that a brand colour
becomes a *source colour* generating a role family — `container`/`on`/
`onContainer` — so contrast is handled and dynamic colour still works.

Note what is *not* a finding: `outlineVariant` at 1.61:1 against surface, and
`secondaryContainer` at 1.65:1. M3's own baseline is in the same range, and the
3:1 rule is for clustered interactive containers, not dividers or tonal
surfaces. `onSurface.copy(alpha = 0.38f)` in `TranslationChapterScreen.kt:203`
is the specified disabled opacity and is exempt.

### Spacing is a habit, not a system

527 `.dp` literals. 419 land on a defined spacing stop, 19 are dimensions rather
than spacing (a hairline border, an avatar, an image height), and 89 are off-scale:

```
  5dp × 10   15dp × 14   18dp × 1   22dp × 1   30dp × 2
 35dp ×  3   50dp × 53   55dp ×  2   70dp × 2   75dp × 1
```

The off-scale values are the smaller half of the problem. `10.dp` (132 uses) and
`20.dp` (115) — the two dominant values — *are* on the scale, as `space125` and
`space250`, so a sweep for off-grid numbers would change almost nothing. What is
wrong is that **nothing records which of padding, gap or margin any of these
is**, so there is no way to adapt them per breakpoint or density later, and no
way to tell a deliberate 15dp from a typo.

`Modifier.height(50.dp)` appears 49 times, almost always as a `Spacer` pushing
an empty or error message down the screen. It is the same three lines copied
into 16 files.

### Typography uses half the scale, weighted small

240 `MaterialTheme.typography.*` reads, which is good — only two `fontSize`
literals in the whole tree. The distribution is the problem:

```
labelMedium 49   bodySmall 46   bodyLarge 41   bodyMedium 30   labelSmall 28
labelLarge  13   titleSmall 12  titleMedium 6  titleLarge 4    headlineSmall 4
headlineMedium 2 headlineLarge 2 displayMedium 1
```

`label*` roles are 92 of 240 uses. Labels are for component text — buttons,
tabs, chips — not for body copy or list content, and they are the smallest and
tightest roles in the scale. Reading a screen where `labelMedium` carries the
prose is the visual equivalent of everything being at the same pitch. Meanwhile
the three `display*` and three `headline*` roles carry 9 uses between them
across 43 screens, so almost nothing establishes hierarchy.

The pinned material3 ships **30** type roles, not 15 — every role has an
`…Emphasized` variant. Two are used (`labelSmallEmphasized`,
`labelLargeEmphasized`).

### Targets and labels are mostly right, with a countable set of exceptions

- 153 `Icon(` calls; 18 pass `contentDescription = null`. Some of those are
  correct (a decorative icon beside its own label should be null), but they have
  not been triaged.
- 45 `IconButton` and 5 `FilledIconButton` — these enforce 48dp themselves.
- 33 bare `Modifier.clickable`, which does not. Two are text-sized:
  `ArticleCard.kt:143` makes an author name clickable, and `LinkPreview.kt:122`
  a `Text` with 2dp vertical padding. Both are around 20dp tall.
- `Clickable.kt` — a vendored ACINQ helper, still in package
  `com.machankura.compose.ui.composable.widgets.buttons` — defaults to
  `internalPadding = PaddingValues(0.dp)` and `RectangleShape`, so every use
  starts below the minimum.
- Zero uses of `minimumInteractiveComponentSize()`.

91 `TextAlign.Center` and 128 `Alignment.CenterHorizontally`. Centring is the
default posture of this UI. The grids-and-spacing page asks the opposite for
lists and content: *"leading elements like thumbnails, avatars, or icons should
always be aligned"*, and the rulers section builds hierarchy from a shared
leading edge. Centred body text also loses the 40–60 character line the
breakpoints page asks for, because there is no ruler to hold it to.

Bidirectionality is, unexpectedly, in decent shape: 23 uses of
`padding(horizontal =`/`vertical =`, 6 of `start`/`end`, and no `left`/`right`
anywhere.

### Text cannot be translated

Two `stringResource` calls. 334 literal strings inside composables — 247
`text = "…"` and 87 `Text("…")`. `strings.xml` exists but holds Phoenix wallet
strings inherited from the fork, under `app_name` = "Machankura".

Capitalisation is title case throughout — roughly 45 distinct strings including
`"Edit Profile"`, `"Create Profile"`, `"New Chat"`, `"Sign In"`, `"Leave Group"`,
`"Key Package Management"`, `"Publish New Key Package"`. The style guide is
unambiguous: *"All text, including titles, headings, labels, menu items,
navigation components, app bars, and buttons should use sentence-style
capitalization."*

The app's top bar reads `"Torch"` while the window title reads `"Mantra"` and
`app_name` reads `"Machankura"`. Three names for one product.

### There is no feedback surface, and empty states are bare

Zero `Snackbar`, zero `SnackbarHost`, zero `rememberSnackbarHostState` — across
26 `Scaffold`s. There is one `AlertDialog` in the whole app. Every transient
outcome — a message sent, an invite failing, a key package published — has
nowhere to be reported.

The error state is `Text("Something went wrong")`, duplicated at 16 sites, and
`Text("No events were found")` at 5. **None of the 16 offers a retry.** No icon,
no explanation, no action. The content design foundation asks for the opposite:
*"emphasize the results of the user's potential action"*, *"tell users what will
happen … and how they can undo it"*.

Interaction states are the library's defaults, which is mostly correct — ripple,
hover and focus come free with `Button`, `Card`, `ListItem`. But the 33 bare
`.clickable` sites and `Clickable.kt` opt out of the shape and padding that make
a state layer legible, and nothing anywhere handles keyboard focus explicitly,
which the desktop target needs.

### One layout, three platforms

Two `BoxWithConstraints` in the entire tree, both inside
`ChatMessageListViewModel.kt`. No `WindowSizeClass`, no
`currentWindowAdaptiveInfo`, no `NavigationSuiteScaffold`, no
`ListDetailPaneScaffold`, no `NavigationBar`, no `NavigationRail`. The desktop
entry point says so itself:

```kotlin
// The layouts have only ever been exercised at phone widths. This is a starting size
// that does not immediately misrepresent them, not a considered desktop layout.
state = rememberWindowState(width = 480.dp, height = 900.dp),
```

Every one of the components needed is present in the pinned material3 —
`NavigationRailKt`, `ShortNavigationBarKt`, `WideNavigationRail`,
`AppBarRowKt`/`AppBarColumnKt`, `FloatingToolbarKt`, `ButtonGroupKt`,
`SplitButtonKt`, `LoadingIndicatorKt`, `MaterialShapesKt`, `MotionSchemeKt`,
`MaterialExpressiveTheme`. What is missing is the adaptive layer
(`material3-adaptive`), which is not declared in `libs.versions.toml`.

Insets are thin but not absent: `enableEdgeToEdge()` is called in
`MainActivity.kt:22`, and `Scaffold` consumes `ScaffoldDefaults.contentWindowInsets`
by default, so most screens are covered. Nine explicit inset uses exist —
`imePadding` appears once, in `SovereignWalletStartupScreen.kt`, though nine
screens and a dialog carry text fields.

### Nothing moves

One animation API in use, `animateScrollToPage`, twice. No `AnimatedVisibility`,
no `AnimatedContent`, no `Crossfade`, no `MotionScheme`, and no
`enterTransition`/`exitTransition` on any of the 43 navigation routes. Every
state change in the app is a hard cut.

### Summary

| area | state | phase |
|---|---|---|
| Theme completeness | 12 roles unset, 4 schemes unreachable, no shapes/motion | 1 |
| Colour at call sites | 7 pairings under threshold, one at 1.00:1 | 1, 3 |
| Spacing | 527 literals, no role recorded | 2 |
| Typography | 90/240 uses on `label*`, 2/30 roles emphasized | 1 |
| Targets & labels | 33 unguarded `.clickable`, 18 untriaged nulls | 3 |
| Content | 334 literals, title case throughout | 4 |
| Feedback | 0 snackbars, 16 duplicated error states, 0 retries | 5 |
| Adaptive | 1 layout, 5 breakpoints | 6 |
| Motion | 1 API, 0 transitions | 7 |

## The phases

Each phase lands on its own and leaves the app shippable. The order is chosen so
that each phase makes the next one mechanical rather than judgemental: tokens
before the call sites that consume them, the accessibility floor before the
adaptive work that would otherwise double the surface to fix, guard rails last
so they lock in real state rather than aspiration.

---

### Phase 0 — a baseline that can be re-measured

**Why first.** Every count in this document was produced by hand. If they cannot
be regenerated, the phases below have no acceptance criteria — only opinions.

**Built.**

1. **`docs/scripts/m3-audit.sh`** regenerates every count in "Where this app
   stands": the dp histogram split three ways, the typography role
   distribution, hardcoded colour sites, `.clickable` sites,
   `contentDescription = null`, string literals, snackbars, adaptive APIs. Each
   number that a phase is meant to move carries a **budget** at the top of the
   file, and `--check` exits 1 when one is exceeded. Budgets ratchet down in the
   same commit that earns the reduction; Phase 8 wires `--check` into CI, at
   which point raising one is the thing a reviewer looks for.

   Two counts it reports separately, because conflating them would overstate the
   problem: the twelve `*Fixed*` roles that fall through to Material baseline
   lavender, and `surfaceTint`, which is also unassigned but defaults to
   `primary` and is therefore correct. The `.dp` histogram likewise splits
   dimensions (a hairline border, an avatar) out of the off-scale count.

2. **`ColorSchemeContrastTest`** in `commonTest` — WCAG relative luminance from
   first principles, no Compose runtime, four assertions over all six schemes:
   every content role on its container at 4.5:1, `onSurface` on each of the
   seven tonal surfaces at 4.5:1, `outline` against every surface it is drawn on
   at 3:1, and `primary`/`error` containers against `surface` at 3:1. 4 tests,
   green.

   It walks the real `ColorScheme` objects, which is why `Theme.kt`'s six
   schemes moved from `private` to `internal`: rebuilding them in the test from
   `Color.kt` would assert the palette and miss the wiring, and a role pointed at
   its neighbour's value is exactly the slip that reads fine in review.

   Verified to bite. Nudging `onSurfaceVariantLight` from `#4C4546` to `#9C9496`
   — a plausible "soften the secondary text" edit — fails it with
   `light: onSurfaceVariant on surfaceVariant is 2.29:1`.

**Deliberately not asserted:** monotonicity across the contrast ladder. The
obvious invariant — high-contrast beats medium beats default, for every pair —
is false, and correctly so: ten pairs move the other way because a high-contrast
scheme darkens `surfaceContainerHighest` to separate it from `surface`, trading
ratio against `onSurface` for the separation that matters. The floor is the
invariant; the ladder is not.

**Risk:** none to the app. The only production change is a visibility keyword.

---

### Phase 1 — one theme, complete

**Why here.** Six of the nine problem areas are call sites reaching past a theme
that has nothing to offer them. Fill the theme and most later phases become
find-and-replace.

**Work.**

1. **Regenerate the scheme with all 49 roles.** Feed the existing source colours
   through Material Theme Builder and take the full export — the twelve
   `*Fixed*` roles and `surfaceTint` included. This removes the latent lavender.
   Keep the existing hex values for the roles already defined so nothing shifts
   visually; this phase adds, it does not restyle.

2. **Reach the contrast schemes.** *Built.* `TorchTheme` takes a `ThemeContrast`
   defaulted from a new `platformThemeContrast()` expect/actual, and the six-way
   selection table lives in common code as `appColorScheme`. The old
   `themeColorScheme` expect took four arguments and did both jobs; it splits into
   `platformThemeContrast()` and `dynamicColorScheme()`, each answering one narrow
   question, so the scheme table is in one place rather than three.

   Android reads `UiModeManager.getContrast()` (API 34+) and registers a
   `ContrastChangeListener`, because a contrast change does not restart the
   activity or arrive as a `Configuration` update — without the listener the new
   setting would wait for the next cold start, which is the case the setting
   exists for. iOS reads `UIAccessibilityDarkerSystemColorsEnabled` and observes
   `…StatusDidChangeNotification`; it is a boolean, so iOS never reports Medium.
   Desktop reads Windows' `win.highContrast.on` AWT desktop property and answers
   Standard on linux and macos, which is honest rather than complete — see
   "What this plan does not cover".

   Verified on an API 36 emulator with dynamic colour off: the `onPrimaryContainer`
   pixel of the "Skip for now" label reads `#848484` → `#A7A7A7` → `#D0D0D0` as the
   setting moves, the three declared values exactly, **without the app restarting**.

3. **Give the theme its other three slots.** *Built.* The expressive-vs-standard
   question was put to the product owner on 2026-09-08 and answered
   **expressive**, so `TorchTheme` calls `MaterialExpressiveTheme` with all four
   slots passed explicitly — colour, `MotionScheme.expressive()`, `MantraShapes`,
   `AuxTypography`. Explicitly, because `MaterialExpressiveTheme` would otherwise
   default them to `expressiveLightColorScheme()` and friends, which is Material's
   palette rather than this app's: the same class of accident as the twelve
   unassigned fixed roles.

   No visual change on the screens checked. The "Invite a Friend" button measures
   byte-identical before and after — same fill, same 357×… box — because the
   expressive default for a `Button` at default size matches the baseline in this
   version. What expressive buys is elsewhere: `LocalUsingExpressiveTheme`, the
   three increased shape steps, the fifteen `…Emphasized` type roles, and the
   components later phases need (button groups, split buttons, floating toolbars,
   wide navigation rails, `LoadingIndicator`).

   `MantraShapes` is baseline `Shapes()`, and `Shape.kt` records why: the corners
   hand-written across the tree already land on the scale — 4dp ×3 = `extraSmall`,
   12dp ×11 = `medium`, 16dp ×2 = `large`, and one 30dp that drifted two units off
   `extraLarge`. Nothing needs restyling; seventeen literals need migrating, later.

4. **Move and fill typography.** `Type.kt` moves from `com.example.ui.theme` to
   `press.mantra.compose.ui.theme`. It stays baseline-derived, but it becomes a
   real file with a stated font stack and a comment recording which roles carry
   what: `display*`/`headline*` for screen identity, `title*` for section and
   card headers, `body*` for prose, `label*` for component text only.

5. **Brand colours become roles.** `BluePill` and `RedPill` leave `Color.kt` as
   raw values and enter the scheme as extended colour families with their own
   `container`/`on`/`onContainer`. `CreateProfileScreen.kt:311` and `:329` then
   stop pairing them with `Color.White`/`Color.DarkGray` by eye.

6. **Wrap the desktop gate.** *Built.* `Main.kt` moves `TorchTheme` outside the
   `unlocked` branch, so `PassphraseGate` — the first screen a desktop user sees —
   stops composing under the default `MaterialTheme`. `MantraApp` keeps its own
   `TorchTheme`, so the unlocked branch is wrapped twice: android and ios enter
   through `MantraApp` and would lose the theme entirely if it moved out, and a
   second application of the same values costs one `CompositionLocalProvider`.

   `Type.kt` also left `com.example.ui.theme` for `press.mantra.compose.ui.theme`,
   which removes one of the three package namespaces the UI was living across.

**Done when** every role in `ColorScheme` is explicitly assigned in both
schemes; the contrast test still passes and now covers six schemes; `Type.kt`
lives under `press.mantra`; no composable in the tree renders under a default
`MaterialTheme`.

**Risk:** medium. Adding the fixed roles cannot regress anything (nothing reads
them), but switching to `MaterialExpressiveTheme` changes default component
shapes and sizes app-wide. Land it as its own commit so it can be reverted
alone.

---

### Phase 2 — spacing becomes a token

**Why here.** Phase 6 has to adapt spacing per breakpoint. It cannot adapt 527 literals.

**Built.** Three commits.

1. **The scale**, `Spacing.kt` — M3's eighteen stops on a composition local, with eight
   semantic names over them (`screenMargin`, `containerPadding`, `compactPadding`,
   `relatedGap`, `itemGap`, `sectionGap`, `emphasisGap`, `targetGap`) split along the
   spec's padding / gap / margin distinction, with exactly one margin. A `data class`
   rather than constants so that a wider instance at a larger breakpoint moves every
   value, including the semantic ones — asserted, because a semantic name holding a
   literal would stay behind and the layout would half-adapt.

   Reached through `MaterialTheme.spacing.containerPadding`, matching
   `MaterialTheme.colorScheme.primary`. `LocalSpacing.current` would have to be read into
   a local first and so could not appear inline in a `Modifier` chain; over 500 call sites
   that difference is what decides whether the scale gets used.

2. **The 89 off-grid values**, to their nearest stop — 5→4, 15→16, 30→32, 50→48. Largest
   move 2dp. Plus the one hand-written corner that was off the *shape* scale, 30dp against
   `extraLarge`'s 28, which is the drift a scale exists to stop.

3. **The remaining 353**, shape-aware: `padding + 8dp → compactPadding`,
   `padding + 16dp → containerPadding`, `gap + 4dp → relatedGap`, `gap + 8dp → itemGap`,
   everything else to the raw stop. 38 of 353 take a semantic name; the rest do not,
   because assigning one needs somebody to have read what the container is, and a name
   asserting a meaning the code lacks is worse than a stop asserting none.

**The audit was measuring the wrong thing, and this is where it showed.** It split
literals by *value* against an exemption list, and the split is not a property of the
value: 16dp is a spacing stop and a plausible icon size, and 50dp was a `Spacer` height in
52 places and a divider width in one. `docs/scripts/m3-spacing-positions.py` classifies by
**call shape** instead — brace-matching `padding(…)`, `PaddingValues(…)`,
`Arrangement.spacedBy(…)`, and a `.height()`/`.width()` whose enclosing call is `Spacer(`
— and `docs/scripts/m3-migrate-spacing.py` rewrites using the same parse, so the audit and
the migration agree by construction.

One value is exempt and says so at the site, through an inline `// m3-spacing-exempt:
<reason>` comment the classifier honours: a 128dp spacer reserving room to scroll the last
wallet clear of the window. Exemptions belong at the call site rather than in a list of
numbers in the tool.

**Done:** spacing positions 527 → **0**, 431 token reads, one reasoned exemption.
Verified on-device that nothing moved — the landing screen differs in 47 of 162,000
sampled pixels, 0.03%, all of them the status bar clock.

**Left for later:** 76 `.dp` in dimension positions — avatar sizes at 35/55/70/75dp, icon
sizes at 18/22dp. Sizing is a per-component question, and the plan puts component specs
after the adaptive phase. They are reported rather than exempted so the number stays
visible.

---

### Phase 3 — the accessibility floor

**Why here.** These are defects, not polish, and one of them hides the app's most
important rows. It goes before the adaptive work so the fixes are made once rather than
per breakpoint.

**Built.** Four commits.

1. **Nine contrast failures**, all of them colour reached for at the call site rather
   than derived from what it sits on. The worst was `ProposalListScreen`: a `ListItem`
   inside a `Card` with only the container overridden, so the headline took
   `ListTokens.ItemLabelTextColor` (`onSurface`) against `primaryContainer` — both
   `#1B1B1B` in the light scheme, **1.00:1**, on exactly the proposals awaiting your
   signature. Four of five colours on that card were under the floor.

   Also `HomeScreen`'s top bar at 1.22:1 (override removed), five
   `onSurfaceVariant.copy(alpha = …)` sites, the LIVE badge's hand-mixed red, and the
   avatar picker's selection tint. Three colours stay hardcoded behind a
   `// m3-color-exempt:` marker with a reason at the site: a QR code's modules, a
   control over an arbitrary photograph, a spinner over a blurhash.

2. **Every `.clickable` chain got `minimumInteractiveComponentSize()`** — a no-op above
   48dp, so applying it everywhere makes the rule checkable rather than measurable. Three
   of the nineteen were text-sized. `Clickable.kt` got it built in and left
   `com.machankura`.

3. **Eighteen `contentDescription = null` became a decision.** Fifteen say `Decorative`
   — same null, but recording that somebody looked. Three carried state the text did not
   repeat and got real descriptions, including `ProposalListScreen`'s stage icon, which
   the previous commit had made the *only* cue for a failed proposal on the highlighted
   card.

4. **Eight text-field screens lift above the keyboard.** `Scaffold`'s
   `contentWindowInsets` is `systemBars` and excludes the ime, so a Scaffold alone does
   nothing about it. `ChatRoomMessagingScreen` is deliberately excluded — its composer
   already reserves the bottom inset by hand, and combining the two needs a device.

5. **Initial focus** on the npub dialog's field and the desktop passphrase field — M3's
   flow rule that a dialog takes focus on open, and that every screen defines one. Those
   are the two places where a single field *is* the screen; auto-focusing elsewhere would
   pop the keyboard over content somebody wants to read first.

**Font scale verified, not assumed.** A static pass found no fixed-height container
holding text — all 23 fixed vertical dimensions are icons, images and progress
indicators. Then at 200% text size on an API 36 emulator: onboarding, the message list
and a chat room all reflow without clipping, system messages wrapping to two lines with
their timestamps and chevrons still aligned.

**Left undone, and why.** Full keyboard traversal on desktop — tab order across the four
`ModalBottomSheet`s and the `AlertDialog`, and focus returning to what opened them — is
not verified. Compose restores focus on dismissal by default, so the gap is evidence
rather than known breakage, and checking it means driving the desktop build by keyboard.
The avatar picker's selected state is `secondaryContainer` at 1.65:1 against the surface,
which M3 accepts only because its own selected states carry a second cue; this grid has
neither an outline nor a checkmark, and adding one is component work.

---

### Phase 4 — text the system can translate

**Why here.** It is the last phase that touches every file, and doing it after phase 2
means one pass over each file instead of two. It must precede phase 6: RTL is a breakpoint
concern too, and there is no point testing a mirrored layout against 334 English literals.

**Built.** Three commits.

1. **Sentence case, 100 occurrences across 60 strings.** Two passes, and the second is
   the instructive one: the first pattern required every word after the first to be
   capitalised, so anything with an article survived — "Invite a Friend" was still on the
   app's first screen after the audit reported zero — and it read one line at a time, so a
   `Text(` whose literal sat on the next line was invisible.

   Sample data is deliberately left in title case: "Steve Biko", "To Kill a Mockingbird".
   Those are a person and a book.

2. **251 plain strings into the catalogue**, 315 call sites. The extractor took four
   attempts and each failure is recorded in `docs/scripts/m3-extract-strings.py`: a bare
   `text = "…"` is not a Compose string (it rewrote a data class), a regex over quote
   pairs is not a Kotlin lexer (it lifted `"chunk"` out of `"${if (n == 1) "chunk" …}"`),
   and a fragment of a `+` concatenation is not a translatable unit.

3. **40 interpolated strings as format strings**, 49 call sites — `${expr}` to `%1$s`,
   passed as arguments.

**Compose Resources is not aapt, and a test caught that before a device did.** The first
extraction escaped apostrophes as `\'` and doubled `%`, which is what android's resource
compiler requires. Compose Resources does neither: `getString` returned `Don\'t sign`,
backslash included, across 30-odd strings. Escape handling is *partial* rather than
absent — `\n` **is** processed — so there is no family rule to lean on.
`StringCatalogueJvmTest` asserts each escape it depends on.

**Done:** literals in composables 334 → 39, `stringResource` 0 → 424, title case 0. Plus
1101 inherited phoenix strings deleted (nothing referenced them), the product settled on
one name, and the two destructive actions now state their consequences — read out of the
repository rather than guessed, because "Delete group" with no qualifier invites the
belief that the messages are gone from the relays, which is the opposite of true.

**Left for later, and why it is not a script's job.** 83 literals that are terms of a `+`
concatenation. Reassembling `"a " + x + " b"` into one format string means deciding what
the whole sentence is, and several of them are pluralisations —
`(if (n == 2) "event" else "events")` — which want a real plural resource rather than a
format argument. `m3-extract-formatted.py --remaining` lists them.

`UserAgent.APP_NAME` still says "Torch". It goes on the wire to relay operators, so it is
a network identity question rather than a content one.

---

### Phase 5 — every screen has four states

**Why here.** It needs the tokens from phase 2 and the strings from phase 4, and it
produces the components phase 6 will lay out.

**Built.** Two commits.

1. **`ErrorState` and `EmptyState`** replace 21 hand-copied blocks — 16 saying "Something
   went wrong", five saying "No events were found", **none of the sixteen with a retry**.
   `EmptyState`'s message is required with no default, because that one sentence was shown
   for five different absences and a shared default would have preserved exactly that.

2. **A snackbar host**, where there had been none across 43 `Scaffold`s. On a composition
   local rather than a parameter, because a view model coroutine reporting an outcome sits
   several composables below the `Scaffold` that owns the host. It throws rather than
   defaulting to a detached state: a default would make `notify(…)` a silent no-op on any
   screen that forgot the host, which is the failure the file exists to end. Wired to
   `publishNewKeyPackage` and `rotateKeyPackage`, both of which were fire-and-forget, and
   verified on a device.

3. **`LoadingDataIndicator` draws `LoadingIndicator`** — the expressive component for an
   indeterminate wait — instead of a `CircularProgressIndicator` hardcoded to 80dp in the
   brand gold, which read as a warning rather than as a wait. One wrapper, 41 call sites.

4. **The profile screen's hierarchy.** Seven actions in one list, five of them
   `TextButton`s and two filled `Button`s — M3's highest emphasis, meant for one action per
   screen. One of the two was **Sign out**: the most prominent control on the screen given
   to its most destructive action. Sharing is now `FilledTonalButton`; signing out is a
   `TextButton` in the error colour, matching how leaving and deleting a group are already
   treated elsewhere in this app.

**The plan was wrong about disabled FABs.** It said five screens should pass `enabled` and
let the component apply the 38% state layer. No `FloatingActionButton` overload in
material3 1.10 takes `enabled` — the spec's position is that an unavailable FAB should not
appear at all — so hand-computing the colours is the only way to show one, and the existing
code already pairs it with `Modifier.semantics { disabled() }` so a screen reader does not
announce a button it is happy to press. Left alone.

**Left for a person.** Eight more screens have two or more filled buttons competing:
LandingScreen's "Sign in" beside "Create profile", SocialPreconditionScreen's "Invite a
friend" beside "View invites", and six others. Which of a pair is primary is a product
decision about what the screen is for, not something to infer from the source, and getting
it wrong quietly weights a choice the user is supposed to make freely.

---

### Phase 6 — layouts that survive a wide window

**Why here.** It is the largest phase and the only one that cannot be done
mechanically. Everything above reduces its surface: tokenised spacing can be
swapped per breakpoint, and the states from Phase 5 are what fills a second
pane.

**Work.**

1. **Declare the adaptive dependency.** `material3-adaptive` is not in
   `libs.versions.toml`. Confirm which artifact publishes multiplatform for the
   pinned Compose Multiplatform 1.11.1 before planning around it — the desktop
   target makes this a real question, not a formality, and the answer decides
   whether steps 3–4 use the library scaffolds or a hand-rolled equivalent over
   `BoxWithConstraints`.

2. **Introduce the breakpoints** — compact / medium / expanded / large /
   extra-large, at 600 / 840 / 1200 / 1600dp. Wire the spacing scale from Phase
   2 to widen with them.

3. **Swap navigation.** Today there is no navigation component at all; screens
   are reached by route. Compact gets a navigation bar, medium and expanded a
   collapsed rail, large and extra-large an expanded rail. The spec's caution
   applies: swap only functionally equivalent components.

4. **Two panes where the content is list-and-detail.** The obvious candidates
   are chat rooms → messages, proposals → proposal detail, and artifacts →
   chapters. Chat is the one to do first and the one to be careful with: a
   message list is high-density content, and the breakpoints page says not to
   put two dense panes in a medium window.

5. **Hold text to 40–60 characters** by giving content a max width rather than
   letting it stretch, and revisit the 91 `TextAlign.Center` uses — start
   alignment is what gives the rulers something to align to.

6. **Give the desktop entry a real window size** and delete the comment that
   apologises for the current one.

7. Split the four composables out of `ChatMessageListViewModel.kt` — a 1,000+
   line view model holding UI is where the only two `BoxWithConstraints` in the
   app ended up, and it will not survive a pane split.

**Done when** every screen renders correctly at 400dp, 700dp, 1000dp, 1400dp and
1800dp; navigation swaps at the right thresholds; and the desktop build opens at
a size that reflects a considered layout.

**Risk:** high. This is the phase that changes what the app looks like. Take it
screen family by screen family — chat first, then proposals, then translation —
and keep each behind its own commit.

---

### Phase 7 — motion

**Why last of the build phases.** Motion describes relationships between
layouts. Animating the current layouts and then changing them in Phase 6 is
work done twice.

**Work.**

1. **Use the `MotionScheme` wired in Phase 1.** Every spec comes from
   `MotionSchemeKeyTokens` rather than a literal `tween`, so the whole app's
   feel is one decision.
2. **Navigation transitions.** All 43 routes use the default; the container
   transform between a list item and its detail screen is the one that carries
   the most meaning, and pairs naturally with the pane work from Phase 6.
3. **State transitions.** `AnimatedContent` between the loading, empty, error
   and loaded states that Phase 5 standardises — currently a hard cut in every
   case.
4. **Respect the reduced-motion preference** on every platform, and hold to the
   spec's own caution that the dragged state is deliberately low-emphasis.

**Done when** no state change in the app is an unannounced cut, and every
animation spec comes from the scheme.

**Risk:** low. Visible, easily tuned, easily reverted.

---

### Phase 8 — guard rails

**Why last.** These lock in what the previous phases achieved. Written earlier,
they would only fail.

**Work.**

1. **CI runs the Phase 0 audit** and fails on regression: no new `.dp` literals
   outside the theme, no new hardcoded `Color`, no new string literal in a
   composable, no new bare `.clickable`.
2. **The contrast test covers every scheme and every call-site pairing**, and is
   part of the normal test run. It is the only one of these that catches a bug a
   human reviewer reliably misses.
3. **Preview coverage per screen**: light, dark, high-contrast, 200% font scale,
   compact and expanded. There are already 51 previews to build on.
4. **A short conventions note in `CLAUDE.md`** — spacing comes from the scale,
   colour from the role, text from resources, targets are 48dp — so the rules
   are visible at the point of writing new code rather than at review.

**Done when** the audit and the contrast test both run in CI, and a change
violating any of the four rules fails.

**Risk:** none to the app; some friction for contributors, which is the point.

## Order and dependencies

| phase | depends on | touches | reversible alone |
|---|---|---|---|
| 0 — baseline | — | new files only | n/a |
| 1 — theme | 0 | `ui/theme/`, 2 entry points | yes |
| 2 — spacing | 1 | ~every file | yes, mechanically |
| 3 — a11y floor | 1 | ~25 sites | yes |
| 4 — content | 2 | ~every file | yes, mechanically |
| 5 — states | 2, 4 | 26 scaffolds, 21 sites | yes |
| 6 — adaptive | 2, 3, 5 | navigation, ~15 screens | no — plan per family |
| 7 — motion | 1, 5, 6 | navigation, state switches | yes |
| 8 — guard rails | all | CI, `CLAUDE.md` | yes |

Phases 1–5 can be worked in parallel by different people if 1 lands first;
6 cannot start until 3 and 5 are done, or the same screens get touched twice.

## What this plan does not cover

- **Which M3 is the target.** Phase 1 asks the expressive-vs-standard question
  and this document does not answer it. The 66 existing
  `ExperimentalMaterial3ExpressiveApi` opt-ins and the pinned alpha both point
  expressive, but it is a product decision with a visible outcome, and it should
  be made deliberately rather than inherited from an import.
- **Whether dynamic colour should keep overriding the brand.** `TorchTheme` defaults
  `dynamicColor = true`, and on Android 12+ that wins unconditionally — so on
  essentially every current Android device, none of the six schemes below is used
  and the app renders in whatever the user's wallpaper produced. Verified on an API
  36 emulator: the app paints Material lavender until dynamic colour is switched
  off, at which point the black-and-gold brand appears. M3's customization
  foundation treats this as a developer choice, and applying it selectively — a
  profile screen, say — is the usual answer for an app with a brand. Deciding it is
  a product call, not a conformance one, and it is why phase 1's contrast work is
  reachable today only on Android below 12, on iOS and on desktop. On Android 14+
  with dynamic colour on, the platform honours contrast itself.
- **Whether the monochrome palette is right.** The scheme's `primary` is pure
  black in light and near-white in dark, with the entire tertiary family a copy
  of primary. That is a defensible choice for this product and it passes
  contrast. It is also why `HomeScreen`'s app bar override collapsed to 1.22:1 —
  a monochrome scheme has no slack. This plan fixes the call sites; it does not
  propose a repalette.
- **Component-level specs.** The foundations are the scope. The per-component
  pages — button sizes, card variants, list item densities — are a second pass,
  best taken after Phase 6 settles which components are used where.
- **iOS.** The ios targets only build on a mac (see `jvm-target.md`), so nothing
  here has been verified on the platform whose HIG asks for 44dp targets rather
  than 48dp. M3 notes the difference; this plan assumes 48dp everywhere.
- **The three package namespaces.** `press.mantra`, `com.example.ui.theme` and
  `com.machankura.compose` all hold live UI code. Phase 1 moves `Type.kt` and
  Phase 3 moves `Clickable.kt` because both are in the way; the rest of the
  `com.machankura` tree — NFC widgets among it — is out of scope.
