#!/usr/bin/env bash
#
# Material Design 3 conformance audit.
#
# Regenerates every count quoted in docs/material-design-conformance.md. The plan
# in that document has acceptance criteria per phase; this is what checks them.
#
# Usage:
#   docs/scripts/m3-audit.sh            report, always exit 0
#   docs/scripts/m3-audit.sh --check    report, exit 1 if any budget is exceeded
#
# The budgets at the top are the state of the tree at the phase named beside each
# one. They ratchet down as phases land: lower the number in the same commit that
# earns it, never raise one. Phase 8 wires --check into CI, at which point raising
# a budget is what a reviewer looks for.

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

UI=composeApp/src/commonMain/kotlin/press/mantra/compose/ui
THEME="$UI/theme"

# ---------------------------------------------------------------------------
# Budgets. "-1" means not yet budgeted -- reported, but never fails --check.
# ---------------------------------------------------------------------------
BUDGET_HARDCODED_COLOR=11      # phase 3 drives to 0 outside theme/
BUDGET_DP_LITERALS=-1          # phase 2 drives to ~0 outside theme/
BUDGET_OFF_SCALE_DP=-1         # phase 2 drives to 0
BUDGET_BARE_CLICKABLE=33       # phase 3 drives to 0
BUDGET_NULL_DESCRIPTION=18     # phase 3 triages each one
BUDGET_STRING_LITERALS=-1      # phase 4 drives to <10
BUDGET_TITLE_CASE=-1           # phase 4 drives to 0
BUDGET_UNSET_COLOR_ROLES=0     # phase 1: reached 2026-09-07

# The M3 spacing scale: docs/material-design-conformance.md, "The numbers".
# space0..space900. Anything outside this set is off-scale.
ON_SCALE=(0 2 4 6 8 10 12 14 16 20 24 32 36 40 48 56 64 72)

# Dimensions rather than spacing -- an avatar, an image height, a hairline
# border. These are exempt from the off-scale count; keep the list short and
# justify additions in the commit that makes them.
DIMENSION_EXEMPT=(1 80 128 180 200 500)

fail_count=0

hdr()  { printf '\n\033[1m== %s\033[0m\n' "$1"; }
note() { printf '   %s\n' "$1"; }

# report <label> <value> <budget>
report() {
  local label=$1 value=$2 budget=$3
  if [[ $budget == "-1" ]]; then
    printf '   %-42s %6s   (no budget)\n' "$label" "$value"
  elif (( value > budget )); then
    printf '   %-42s %6s   \033[31mover budget %s\033[0m\n' "$label" "$value" "$budget"
    fail_count=$((fail_count + 1))
  else
    printf '   %-42s %6s   (budget %s)\n' "$label" "$value" "$budget"
  fi
}

# Count matches across the UI tree, optionally excluding the theme package.
# $1 pattern, $2 "exclude-theme" | "all"
count() {
  local pattern=$1 scope=${2:-all}
  if [[ $scope == exclude-theme ]]; then
    grep -rE "$pattern" "$UI" --include=*.kt 2>/dev/null \
      | grep -v "^$THEME/" | wc -l | tr -d ' '
  else
    grep -rE "$pattern" "$UI" --include=*.kt 2>/dev/null | wc -l | tr -d ' '
  fi
}

printf '\033[1mMaterial Design 3 conformance audit\033[0m\n'
printf 'tree: %s\n' "$(git rev-parse --short HEAD 2>/dev/null || echo 'not a git checkout')"
printf 'over: %s\n' "$UI"

# ---------------------------------------------------------------------------
hdr 'Colour  (phases 1, 3)'

# Roles ColorScheme declares that Theme.kt never assigns. An unassigned role
# falls through to the Material baseline palette -- lavender, in a monochrome
# app -- so this is a defect count, not a style count.
declared=$(grep -oE '^\s{4}[a-zA-Z]+ = ' "$THEME/Theme.kt" 2>/dev/null \
  | tr -d ' =' | sort -u)
# The 49 roles of androidx.compose.material3.ColorScheme, as of material3
# 1.10.0-alpha05. Hardcoded because the artifact is not on this script's path.
all_roles="primary onPrimary primaryContainer onPrimaryContainer inversePrimary
secondary onSecondary secondaryContainer onSecondaryContainer
tertiary onTertiary tertiaryContainer onTertiaryContainer
background onBackground surface onSurface surfaceVariant onSurfaceVariant
surfaceTint inverseSurface inverseOnSurface error onError errorContainer
onErrorContainer outline outlineVariant scrim surfaceBright surfaceDim
surfaceContainer surfaceContainerHigh surfaceContainerHighest
surfaceContainerLow surfaceContainerLowest
primaryFixed primaryFixedDim onPrimaryFixed onPrimaryFixedVariant
secondaryFixed secondaryFixedDim onSecondaryFixed onSecondaryFixedVariant
tertiaryFixed tertiaryFixedDim onTertiaryFixed onTertiaryFixedVariant"
# A role left unassigned takes lightColorScheme()'s default. For the twelve
# *Fixed* roles that default is ColorLightTokens.PrimaryFixed and friends --
# PaletteTokens.Primary90, #EADDFF -- so a monochrome app renders Material
# baseline lavender. For surfaceTint the default is `primary`, which is right.
# Only the first kind is a defect, so they are counted apart.
unset_baseline=0; unset_derived=0
baseline_list=""; derived_list=""
for role in $all_roles; do
  echo "$declared" | grep -qx "$role" && continue
  case $role in
    *Fixed|*FixedDim|*FixedVariant)
      unset_baseline=$((unset_baseline + 1)); baseline_list="$baseline_list $role" ;;
    *)
      unset_derived=$((unset_derived + 1)); derived_list="$derived_list $role" ;;
  esac
done
report 'roles falling to the baseline palette' "$unset_baseline" "$BUDGET_UNSET_COLOR_ROLES"
[[ -n $baseline_list ]] && note "lavender:$baseline_list"
[[ -n $derived_list ]] && note "derived (not a defect):$derived_list"

hardcoded=$(count 'Color\(0x|Color\.(Red|Blue|Green|Gray|LightGray|DarkGray|White|Black|Yellow|Magenta|Cyan)' exclude-theme)
report 'hardcoded Color outside theme/' "$hardcoded" "$BUDGET_HARDCODED_COLOR"
[[ $hardcoded -gt 0 ]] && grep -rEln 'Color\(0x|Color\.(Red|Blue|Green|Gray|LightGray|DarkGray|White|Black|Yellow|Magenta|Cyan)' \
  "$UI" --include=*.kt | grep -v "^$THEME/" | sed "s|$UI/|     |"

alpha=$(count '\.copy\(alpha')
report 'colours derived with .copy(alpha =)' "$alpha" -1

# ---------------------------------------------------------------------------
hdr 'Spacing  (phase 2)'

dp_all=$(grep -rhoE '\b[0-9]+\.dp' "$UI" --include=*.kt 2>/dev/null \
  | grep -v "^$THEME/" | wc -l | tr -d ' ')
dp_outside_theme=$(grep -rhoE '\b[0-9]+\.dp' \
  $(grep -rl '\.dp' "$UI" --include=*.kt 2>/dev/null | grep -v "^$THEME/") \
  2>/dev/null | wc -l | tr -d ' ')
report '.dp literals outside theme/' "$dp_outside_theme" "$BUDGET_DP_LITERALS"

# Split the histogram into on-scale, exempt dimensions, and off-scale.
declare -A hist
while read -r n; do
  hist[$n]=$(( ${hist[$n]:-0} + 1 ))
done < <(grep -rhoE '\b[0-9]+\.dp' \
  $(grep -rl '\.dp' "$UI" --include=*.kt 2>/dev/null | grep -v "^$THEME/") \
  2>/dev/null | sed 's/\.dp//')

on_scale_total=0; off_scale_total=0; exempt_total=0; off_scale_detail=""
for n in "${!hist[@]}"; do
  c=${hist[$n]}
  if printf '%s\n' "${ON_SCALE[@]}" | grep -qx "$n"; then
    on_scale_total=$((on_scale_total + c))
  elif printf '%s\n' "${DIMENSION_EXEMPT[@]}" | grep -qx "$n"; then
    exempt_total=$((exempt_total + c))
  else
    off_scale_total=$((off_scale_total + c))
    off_scale_detail="$off_scale_detail ${n}dp:${c}"
  fi
done
note "on the M3 scale:        $on_scale_total"
note "exempt dimensions:      $exempt_total"
report 'off the M3 spacing scale' "$off_scale_total" "$BUDGET_OFF_SCALE_DP"
[[ -n $off_scale_detail ]] && note "off-scale:$off_scale_detail"

spacer_idiom=$(count 'height\(50\.dp\)')
note "Spacer(height(50.dp)) idiom:            $spacer_idiom"

# ---------------------------------------------------------------------------
hdr 'Typography  (phase 1)'

typo_total=$(count 'MaterialTheme\.typography\.')
note "MaterialTheme.typography reads:         $typo_total"
grep -rhoE 'MaterialTheme\.typography\.[a-zA-Z]+' "$UI" --include=*.kt 2>/dev/null \
  | sed 's/.*typography\.//' | sort | uniq -c | sort -rn \
  | awk '{printf "     %-26s %s\n", $2, $1}'
label_uses=$(grep -rhoE 'MaterialTheme\.typography\.label[A-Za-z]*' "$UI" --include=*.kt 2>/dev/null | wc -l | tr -d ' ')
note "of which label* roles:                  $label_uses"
fontsize=$(count 'fontSize = [0-9]')
note "hardcoded fontSize:                     $fontsize"

# ---------------------------------------------------------------------------
hdr 'Targets and labels  (phase 3)'

clickable=$(count '\.clickable')
report 'bare Modifier.clickable' "$clickable" "$BUDGET_BARE_CLICKABLE"
null_desc=$(count 'contentDescription = null')
report 'contentDescription = null' "$null_desc" "$BUDGET_NULL_DESCRIPTION"
icons=$(count 'Icon\(')
note "Icon( call sites:                       $icons"
min_size=$(count 'minimumInteractiveComponentSize')
note "minimumInteractiveComponentSize:        $min_size"
centred=$(count 'TextAlign\.Center')
note "TextAlign.Center:                       $centred"

# ---------------------------------------------------------------------------
hdr 'Content  (phase 4)'

literals=$(( $(count 'text = "') + $(count 'Text\("') ))
report 'string literals in composables' "$literals" "$BUDGET_STRING_LITERALS"
res=$(count 'stringResource|Res\.string')
note "stringResource / Res.string:            $res"
title_case=$(grep -rhoE '"[A-Z][a-z]+( [A-Z][a-z]+)+"' "$UI" --include=*.kt 2>/dev/null | sort -u | wc -l | tr -d ' ')
report 'distinct Title Case strings' "$title_case" "$BUDGET_TITLE_CASE"
note 'includes preview sample data (person names); phase 4 triages'

# ---------------------------------------------------------------------------
hdr 'States and feedback  (phase 5)'

scaffolds=$(count '(^|[^A-Za-z])Scaffold\(')
snackbars=$(count 'Snackbar|SnackbarHost')
note "Scaffold( call sites:                   $scaffolds"
note "Snackbar / SnackbarHost:                $snackbars"
went_wrong=$(count '"Something went wrong"')
note '"Something went wrong" sites:           '"$went_wrong"
for c in FilledTonalButton OutlinedButton ElevatedButton Button TextButton; do
  n=$(grep -rhoE "\b$c\(" "$UI" --include=*.kt 2>/dev/null | wc -l | tr -d ' ')
  note "$(printf '%-38s' "$c:")$n"
done

# ---------------------------------------------------------------------------
hdr 'Adaptive and motion  (phases 6, 7)'

adaptive=$(count 'WindowSizeClass|currentWindowAdaptiveInfo|NavigationSuiteScaffold|ListDetailPaneScaffold|SupportingPaneScaffold|BoxWithConstraints')
note "adaptive APIs in use:                   $adaptive"
nav=$(count 'NavigationBar\(|NavigationRail\(|WideNavigationRail\(|ShortNavigationBar\(')
note "navigation components:                  $nav"
motion=$(count 'AnimatedVisibility|AnimatedContent|Crossfade|MotionScheme|updateTransition')
note "motion APIs in use:                     $motion"
transitions=$(count 'enterTransition|exitTransition|popEnterTransition')
note "navigation transitions:                 $transitions"

# ---------------------------------------------------------------------------
printf '\n'
if [[ ${1:-} == --check ]]; then
  if (( fail_count > 0 )); then
    printf '\033[31m%s budget(s) exceeded.\033[0m See docs/material-design-conformance.md.\n' "$fail_count"
    exit 1
  fi
  printf '\033[32mAll budgets met.\033[0m\n'
fi
exit 0
