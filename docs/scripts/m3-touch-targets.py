#!/usr/bin/env python3
"""Find `.clickable` chains with no minimum interactive size.

M3 asks for touch targets of at least 48x48dp and pointer targets of at least 44x44dp.
`Modifier.clickable` gives an element no minimum of its own, so a clickable `Text` is a
target the size of the text -- around 20dp here.

`minimumInteractiveComponentSize()` is a no-op on anything already 48dp or larger, so the
rule this checks is simply that every `.clickable` chain has it: correct everywhere, and
textual enough to enforce. It reserves *layout* space; touch expansion happens at the
input layer regardless, and the layout is what stops adjacent targets overlapping and what
a pointer on desktop has to land on.

Components that carry their own minimum -- IconButton, Button, Checkbox, ListItem and the
rest of material3 -- are not `.clickable` call sites and never appear here.

Comments are not code, in either direction. A KDoc line explaining that a row uses a
clickable `Card` *rather than* a `Modifier.clickable` was counted as a call site, which
breaks a zero budget and so breaks `./gradlew check`; and a comment above a real call site
that merely mentions `minimumInteractiveComponentSize` would have excused it. The first is
noise and the second is a miss, so both lines of the window are read as code only. This is
the same thing m3-audit.sh's `NOT_A_COMMENT` exists for, spelled as m3-title-case.py
spells it -- see the note there on a colour comment counting as a colour.

Usage: m3-touch-targets.py [--list]
"""
import os, re, sys

UI = 'composeApp/src/commonMain/kotlin/press/mantra/compose/ui'


def _is_comment(line):
    """Whether `line` is a comment line: `//`, or a `/*` block's opener or body.

    A prefix test rather than block tracking, because that is what the other two checkers
    do and three identical rules spelled three ways is worse than one limitation. The
    limitation: a `/* ... */` body line that does not start with `*` reads as code. There
    are 539 block comments under `ui/` and not one such line, since every KDoc here stars
    its body -- if that ever stops being true, this is the function to make stateful.
    """
    return line.lstrip().startswith(('//', '*', '/*'))


def offenders():
    found = []
    for root, _, files in os.walk(UI):
        for f in sorted(files):
            if not f.endswith('.kt'):
                continue
            path = os.path.join(root, f)
            lines = open(path, encoding='utf-8').read().split('\n')
            for i, line in enumerate(lines):
                if '.clickable' not in line:
                    continue
                if _is_comment(line) or line.startswith('import '):
                    continue
                # The modifier may sit on this line or on the one above, since a chain
                # broken across lines is the common shape. Comment lines are dropped from
                # the window rather than kept: an exemption has to be in the code.
                window = '\n'.join(
                    l for l in lines[max(0, i - 1):i + 1] if not _is_comment(l)
                )
                if 'minimumInteractiveComponentSize' in window:
                    continue
                found.append((path, i + 1, line.strip()[:90]))
    return found


def main():
    found = offenders()
    print(f'   {"clickable chains with no minimum target":42s} {len(found):6d}')
    if '--list' in sys.argv:
        for path, line, ctx in found:
            print(f'     {path.replace(UI + "/", "")}:{line}  {ctx}')
    return 1 if found else 0


if __name__ == '__main__':
    sys.exit(main())
