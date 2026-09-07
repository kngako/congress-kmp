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
| `ArticleCard.kt:67`, `QuotedNote.kt:101`, `QuotedAddressableNote.kt:62`, `LiveStreamCardContent.kt:42` | `onSurfaceVariant.copy(alpha = 0.5f)` on surface | 2.49:1 | 4.5:1 |
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

2. **Reach the contrast schemes.** `TorchTheme` grows a contrast parameter and
   the platform actuals report it — Android from `UiModeManager.getContrast()`
   (API 34+, a float where `0f`/`0.33f`/`0.66f` map onto the three schemes),
   falling back to the default scheme on the app's minSdk of 26; iOS from
   `UIAccessibilityDarkerSystemColorsEnabled`; desktop from a preference. The
   four schemes already written stop being dead code.

3. **Give `MaterialTheme` its other three slots.** `Shapes`, `Typography` and a
   `MotionScheme` are all parameters of the overload the app already calls:

   ```kotlin
   MaterialTheme(
       colorScheme = colorScheme,
       motionScheme = MotionScheme.expressive(),   // or .standard()
       shapes = MantraShapes,
       typography = MantraTypography,
       content = content,
   )
   ```

   Decide `MaterialExpressiveTheme` vs `MaterialTheme` here and once. The app
   already opts into `ExperimentalMaterial3ExpressiveApi` in 66 places, so the
   expressive default is the honest choice; it is also what makes the
   `…Emphasized` type roles and the increased shape sizes meaningful.

4. **Move and fill typography.** `Type.kt` moves from `com.example.ui.theme` to
   `press.mantra.compose.ui.theme`. It stays baseline-derived, but it becomes a
   real file with a stated font stack and a comment recording which roles carry
   what: `display*`/`headline*` for screen identity, `title*` for section and
   card headers, `body*` for prose, `label*` for component text only.

5. **Brand colours become roles.** `BluePill` and `RedPill` leave `Color.kt` as
   raw values and enter the scheme as extended colour families with their own
   `container`/`on`/`onContainer`. `CreateProfileScreen.kt:311` and `:329` then
   stop pairing them with `Color.White`/`Color.DarkGray` by eye.

6. **Wrap the desktop gate.** `Main.kt` moves `TorchTheme` outside the
   `unlocked` branch so `PassphraseGate` composes inside it.

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

**Why here.** Phase 6 has to adapt spacing per breakpoint. It cannot adapt 527
literals.

**Work.**

1. **A spacing scale, named as the spec names it.** `MaterialTheme` has no
   spacing slot, so this is a `CompositionLocal`:

   ```kotlin
   @Immutable
   data class Spacing(
       val space0: Dp = 0.dp,    val space25: Dp = 2.dp,   val space50: Dp = 4.dp,
       val space75: Dp = 6.dp,   val space100: Dp = 8.dp,  val space125: Dp = 10.dp,
       val space150: Dp = 12.dp, val space175: Dp = 14.dp, val space200: Dp = 16.dp,
       val space250: Dp = 20.dp, val space300: Dp = 24.dp, val space400: Dp = 32.dp,
       val space450: Dp = 36.dp, val space500: Dp = 40.dp, val space600: Dp = 48.dp,
       val space700: Dp = 56.dp, val space800: Dp = 64.dp, val space900: Dp = 72.dp,
   )

   val LocalSpacing = staticCompositionLocalOf { Spacing() }
   ```

   Defaulted to the M3 values so the migration is a rename, not a restyle. It is
   a `data class` so Phase 6 can supply a wider instance at larger breakpoints
   without touching a call site.

2. **A semantic layer over it**, because `space125` at a call site is no more
   readable than `10.dp`. Screen margin, list gap, card padding, section gap —
   named for what they are, each pointing at a stop. This is the layer that lets
   the audit distinguish padding from gap from margin, which the raw scale
   cannot.

3. **Migrate, in the order the audit reports.** The 89 off-scale values are the
   interesting ones and go first: each is either a typo (round to the nearest
   stop) or deliberate (say why, in a comment, and pick the nearest stop
   anyway). Then `10.dp` and `20.dp` en masse.

4. **Retire the 49 spacer idioms** into the empty-state composable Phase 5
   builds. They disappear rather than being migrated.

5. Adopt the parent-container rule while passing through: prefer
   `Arrangement.spacedBy` on the parent (119 uses already) and padding on the
   container over per-child padding. The 119 existing `spacedBy` calls suggest
   this is already the instinct.

**Done when** the audit script reports zero `.dp` literals outside
`ui/theme/Spacing.kt` and a short allowlist of genuine dimensions (avatar sizes,
image heights, hairline borders).

**Risk:** low but wide — it touches nearly every file. Best done as one
mechanical commit per directory with previews checked between.

---

### Phase 3 — the accessibility floor

**Why here.** These are defects, not polish, and one of them hides the app's
most important rows. It goes before the adaptive work so the fixes are made once
rather than per breakpoint.

**Work.**

1. **Fix the seven measured pairings.** Take them in the order of the table.
   `ProposalListScreen.kt:228` needs the `ListItem` colours derived from the
   card's container, not left at `onSurface`:

   ```kotlin
   ListItemDefaults.colors(
       containerColor = Color.Transparent,
       headlineColor = contentColorFor(cardContainer),
       supportingColor = contentColorFor(cardContainer),
       leadingIconColor = contentColorFor(cardContainer),
   )
   ```

   `HomeScreen.kt:113` should drop its `topAppBarColors` override entirely —
   the default is `surface`/`onSurface` and is correct. The four
   `onSurfaceVariant.copy(alpha = 0.5f)` sites should use `onSurfaceVariant` at
   full opacity, which is already the role for secondary text.

2. **Extend the contrast test to call sites.** Every non-default
   `containerColor`/`contentColor` pairing in the tree gets a row in a table the
   test walks. This is what stops the class of bug rather than the instance —
   the `ListItem`-inside-`Card` case is invisible to a reviewer and obvious to
   an assertion.

3. **Guarantee the 48dp minimum.** The 33 bare `.clickable` sites either become
   a real component (`ArticleCard.kt:143`'s author name is a `TextButton`) or
   gain `Modifier.minimumInteractiveComponentSize()`. `Clickable.kt` gets it
   built in, moves into `press.mantra`, and its `RectangleShape` default is
   reconsidered so state layers read.

4. **Triage the 18 null content descriptions.** Each is either genuinely
   decorative — and then says so with `Modifier.clearAndSetSemantics {}` or a
   comment — or gets a label. The spec's rule for the labels themselves: name
   the purpose, not the picture, and never include the role ("Search", not
   "magnifying glass", never "Search button").

5. **Survive a large font scale.** Test every screen at 200% text size. The
   pattern to look for is a fixed `height` on a container of text; the 49
   `height(50.dp)` spacers are safe, but `Modifier.height(…)` around a `Text`
   is not.

6. **Keyboard flow for the desktop target.** Initial focus per screen, focus
   into and back out of the four `ModalBottomSheet`s and the `AlertDialog`, and
   `Tab` order verified on the nine screens with text fields. The foundations
   page is explicit that when a dialog opens, focus moves into it, and when it
   closes, focus returns to what opened it.

7. `imePadding()` on all nine text-field screens and the npub dialog, not one.

**Done when** the extended contrast test passes over both scheme families; the
audit reports zero unguarded `.clickable`; every `Icon` either has a description
or a recorded reason; and every screen is legible at 200% text scale.

**Risk:** low. Each fix is local and independently verifiable.

---

### Phase 4 — text the system can translate

**Why here.** It is the last phase that touches every file, and doing it after
Phase 2 means one pass over each file instead of two. It must precede Phase 6:
RTL is a breakpoint concern too, and there is no point testing a mirrored layout
against 334 English literals.

**Work.**

1. **Externalise all 334 strings** into `composeResources/values/strings.xml`,
   organised by screen. Clear out the inherited Phoenix wallet strings that
   nothing references, and settle `app_name`.
2. **Sentence case, everywhere.** "Edit profile", "Create profile", "New chat",
   "Sign in", "Leave group", "Key package management", "Publish new key
   package". Roughly 45 strings. Product names stay capitalised — which requires
   settling on one: Torch, Mantra or Machankura.
3. **Rewrite the destructive confirmations** to state consequences plainly.
   "Delete group" and "Leave group" currently offer a label and nothing else;
   the style guide wants the outcome and whether it can be undone.
4. **Alt text for meaningful images** — profile avatars, QR codes, artifact
   images — following the Phase 3 triage rule.
5. **Spell out abbreviations** in user-facing text. Protocol terms that are
   genuinely the domain (npub, NIP-05, relay) stay; incidental shortenings go.
6. Verify RTL by mirroring: the codebase has no `left`/`right` modifiers, so
   this should be confirmation rather than repair.

**Done when** the audit reports fewer than ten string literals in composables
(test data and previews), and no user-facing string uses title case.

**Risk:** low, high volume. Sentence-casing is the part most likely to draw
disagreement — settle the product name first, in one decision.

---

### Phase 5 — every screen has four states

**Why here.** It needs the tokens from Phase 2 and the strings from Phase 4, and
it produces the components Phase 6 will lay out.

**Work.**

1. **A `SnackbarHost` on every `Scaffold`**, and a single place to send messages
   to it. 26 scaffolds, zero hosts, is why there is nowhere to report an invite
   failing.
2. **One empty-state composable, one error-state composable.** Icon, message,
   and — for errors — a retry action. This replaces 16 copies of
   `Text("Something went wrong")` and 5 of `Text("No events were found")`, and
   absorbs the 49 spacer idioms. **None of the 16 sites offers a retry today**;
   each one is a dead end for the user.
3. **Loading gets the M3 component.** `LoadingDataIndicator` hardcodes an 80dp
   `CircularProgressIndicator` in `colorScheme.secondary`. The pinned material3
   ships `LoadingIndicator`, which is the expressive equivalent and themed.
4. **Audit the disabled states.** Five screens compute a FAB container colour
   by hand from a `can…` flag (`AddArtifactScreen.kt:156`,
   `AddChapterScreen.kt:149`, `AddDialectScreen.kt:128`,
   `TranslateChunkScreen.kt:131`, `AddTranslationArtifactVersionScreen.kt:155`).
   Passing `enabled` and letting the component apply the 38% state layer is both
   less code and the specified behaviour.
5. **Give buttons a hierarchy.** 31 `Button` and 27 `TextButton`, and nothing in
   between — no `FilledTonalButton`, `OutlinedButton` or `ElevatedButton`
   anywhere. Every screen therefore reads as either maximum or minimum emphasis.
   Assign one filled button per screen and demote the rest.

**Done when** every `Scaffold` has a host, every list has an empty state, every
error offers a retry, and no screen computes a disabled colour by hand.

**Risk:** low. Additive.

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
