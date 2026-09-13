#!/usr/bin/env python3
"""Rewrite a tree, or a patch, from Curated's names into Mantra's.

Curated (curated/curated-kmp, brand "Curare" since e6aee792, "Curated" before it) forked from
Mantra at ba26c0b1 and renamed the app twice: `press.mantra.*` -> `com.it.curated.*` ->
`to.curare.*`, with the app-level identifiers (`MantraDatabase`, `MantraNavHost`, ...) and the
generated-resources package following. Every feature commit on the fork is therefore written in
one of those two vocabularies. This script maps both back to Mantra's, so a Curated commit can be
replayed onto Mantra as an ordinary cherry-pick. docs/curated-to-mantra.md is the plan it serves.

What it must never touch, and by construction cannot: every rule below matches a *brand* token
(`Curare`, `curare`, `com.it.curated`, a `Curated*` app identifier) and none matches a Mantra one.
The twelve `Mantra*` Room entities, the two `mantra/` hash-tag prefixes, `wss://ephemeral.mantra.press`
and the `curated` product vocabulary (`CuratedSchemaEvent`, `nostr/curated/`, the "Curated" queue
mark) pass through unchanged. The bare word "Curated" is deliberately NOT a rule: in generation 1
it was the brand, at the tip it is the protocol's name, and the two generation-1 feature commits
never use it as a brand in code -- so mapping only the gen-1 *identifiers* is exact.

Modes
  tree  <dir>          rewrite a checked-out tree in place: contents, then paths (for filter-branch)
  patch <file>...      rewrite git format-patch files in place: headers, paths and body alike

Check your work the way the plan does: the rewritten tip must compile and pass jvmTest, and the
diff between a replay and the rewritten tip must be exactly the commits you chose to drop.
"""
import os, re, shutil, sys

# ---- content rules, applied in order. \b keeps `CurareApp` off `CurareApplication`. ----
_RULES = [
    # generation 2: to.curare / Curare*
    (r'to\.curare\.', 'press.mantra.'),
    (r'to/curare/', 'press/mantra/'),
    (r'curare\.composeapp\.generated\.resources', 'mantra.composeapp.generated.resources'),
    (r'\bCurareDatabaseConstructor\b', 'MantraDatabaseConstructor'),
    (r'\bCurareDatabaseJvmTest\b', 'MantraDatabaseJvmTest'),
    (r'\bCurareDatabase\b', 'MantraDatabase'),
    (r'\bCurareApplication\b', 'MantraApplication'),
    (r'\bCurareApp\b', 'MantraApp'),
    (r'\bCurareGlobal\b', 'MantraGlobal'),
    (r'\bcurareGlobal\b', 'mantraGlobal'),
    (r'\bCurareNavHost\b', 'MantraNavHost'),
    (r'\bCurareNavigationSuite\b', 'MantraNavigationSuite'),
    (r'\bCurareShapes\b', 'MantraShapes'),
    (r'\bCurareConverters\b', 'MantraConverters'),
    (r'\bdefaultCurareDir\b', 'defaultMantraDir'),
    (r'\bCurareTheme\b', 'MantraTheme'),
    (r'Res\.string\.curare\b', 'Res.string.mantra'),
    (r'<string name="curare">Curare</string>', '<string name="mantra">Mantra</string>'),
    (r'const val APP_NAME = "Curare"', 'const val APP_NAME = "Mantra"'),
    (r'const val CLIENT_NAME = "Curare"', 'const val CLIENT_NAME = "Mantra"'),
    (r'APP_DIR_NAME = "curated"', 'APP_DIR_NAME = "mantra"'),
    (r'rootProject\.name = "Curare"', 'rootProject.name = "Mantra"'),
    (r'namespace = "to\.curare\.android"', 'namespace = "press.mantra.android"'),
    (r'applicationId = "to\.curare\.android"', 'applicationId = "press.mantra.android"'),
    # generation 1: com.it.curated / Curated* app identifiers (the two group-identity commits)
    (r'com\.it\.curated\.', 'press.mantra.'),
    (r'com/it/curated/', 'press/mantra/'),
    (r'curated\.composeapp\.generated\.resources', 'mantra.composeapp.generated.resources'),
    (r'\bCuratedDatabaseConstructor\b', 'MantraDatabaseConstructor'),
    (r'\bCuratedDatabaseJvmTest\b', 'MantraDatabaseJvmTest'),
    (r'\bCuratedDatabase\b', 'MantraDatabase'),
    (r'\bCuratedApplication\b', 'MantraApplication'),
    (r'\bCuratedApp\b', 'MantraApp'),
    (r'\bCuratedGlobal\b', 'MantraGlobal'),
    (r'\bcuratedGlobal\b', 'mantraGlobal'),
    (r'\bCuratedNavHost\b', 'MantraNavHost'),
    (r'\bCuratedNavigationSuite\b', 'MantraNavigationSuite'),
    (r'\bCuratedShapes\b', 'MantraShapes'),
    (r'\bCuratedConverters\b', 'MantraConverters'),
    (r'\bdefaultCuratedDir\b', 'defaultMantraDir'),
    (r'\bCuratedTheme\b', 'MantraTheme'),
    (r'const val APP_NAME = "Curated"', 'const val APP_NAME = "Mantra"'),
    (r'const val CLIENT_NAME = "Curated"', 'const val CLIENT_NAME = "Mantra"'),
    (r'rootProject\.name = "Curated"', 'rootProject.name = "Mantra"'),
    (r'namespace = "com\.it\.curated\.android"', 'namespace = "press.mantra.android"'),
    (r'applicationId = "com\.it\.curated"', 'applicationId = "press.mantra.android"'),
    # ios: Mantra is `Mantra` / press.mantra.ios since the plan's Phase 0
    (r'PRODUCT_NAME=Curare\b', 'PRODUCT_NAME=Mantra'), (r'PRODUCT_NAME=Curated\b', 'PRODUCT_NAME=Mantra'),
    (r'PRODUCT_BUNDLE_IDENTIFIER=to\.curare\.ios', 'PRODUCT_BUNDLE_IDENTIFIER=press.mantra.ios'),
    (r'PRODUCT_BUNDLE_IDENTIFIER=com\.it\.curated\b', 'PRODUCT_BUNDLE_IDENTIFIER=press.mantra.ios'),
    (r'\bCurare\.app\b', 'Mantra.app'), (r'\bCurated\.app\b', 'Mantra.app'),
    # string keys carrying the brand: `_` is a word character, so \b never fires inside them
    (r'sign_in_is_not_yet_available_while_curare_is', 'sign_in_is_not_yet_available_while_mantra_is'),
    (r'sign_in_is_not_yet_available_while_curated_is', 'sign_in_is_not_yet_available_while_mantra_is'),
    (r'(?<![A-Za-z0-9])curare(?=_)', 'mantra'), (r'(?<=_)curare(?![A-Za-z0-9])', 'mantra'),
    # a package name in prose, without its trailing dot
    (r'to\.curare\b', 'press.mantra'), (r'com\.it\.curated\b', 'press.mantra'),
    # the brand word itself, in comments, string values and docs
    (r'\bCurare\b', 'Mantra'),
    (r'\bcurare\b', 'mantra'),
]
RULES = [(re.compile(p), r) for p, r in _RULES]

TEXT_EXT = {'.kt', '.kts', '.xml', '.md', '.py', '.sh', '.toml', '.properties', '.json', '.yml', '.yaml',
            '.txt', '.xcconfig', '.pbxproj', '.plist', '.swift', '.gitignore', '.gitmodules'}


def normalise_text(s: str) -> str:
    for rx, rep in RULES:
        s = rx.sub(rep, s)
    return s


def normalise_path(rel: str) -> str:
    """Directories first, then the identifier rules over the whole path (file names carry them too)."""
    rel = (rel.replace('/to/curare/', '/press/mantra/').replace('/com/it/curated/', '/press/mantra/')
              .replace('to.curare.compose.database.CurareDatabase', 'press.mantra.compose.database.MantraDatabase')
              .replace('com.it.curated.compose.database.CuratedDatabase', 'press.mantra.compose.database.MantraDatabase'))
    return normalise_text(rel)


def _is_text(path: str) -> bool:
    return os.path.splitext(path)[1] in TEXT_EXT or os.path.basename(path) in ('.gitmodules', 'gradlew')


def rewrite_tree(root: str) -> None:
    rewritten = 0
    for dirpath, _, filenames in os.walk(root):
        if '/.git' in dirpath or dirpath.endswith('/.git'):
            continue
        for fn in filenames:
            p = os.path.join(dirpath, fn)
            if not _is_text(p) or os.path.islink(p):
                continue
            try:
                s = open(p, encoding='utf-8').read()
            except UnicodeDecodeError:
                continue
            t = normalise_text(s)
            if t != s:
                open(p, 'w', encoding='utf-8').write(t)
                rewritten += 1
    moves = []
    for dirpath, _, filenames in os.walk(root):
        if '/.git' in dirpath or dirpath.endswith('/.git'):
            continue
        for fn in filenames:
            rel = os.path.relpath(os.path.join(dirpath, fn), root)
            new = normalise_path(rel)
            if new != rel:
                moves.append((rel, new))
    for rel, new in moves:
        dst = os.path.join(root, new)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(os.path.join(root, rel), dst)
    for dirpath, _, _ in os.walk(root, topdown=False):
        if dirpath != root and '/.git' not in dirpath and not os.listdir(dirpath):
            os.rmdir(dirpath)
    print(f'rewritten {rewritten} files, moved {len(moves)} paths', file=sys.stderr)


def rewrite_patches(paths) -> None:
    for p in paths:
        s = open(p, encoding='utf-8').read()
        open(p, 'w', encoding='utf-8').write(normalise_text(s))


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'tree':
        rewrite_tree(os.path.abspath(sys.argv[2]))
    elif len(sys.argv) >= 3 and sys.argv[1] == 'patch':
        rewrite_patches(sys.argv[2:])
    else:
        sys.exit(__doc__)
