#!/usr/bin/env python3
"""Land rewritten Curated commits on a Mantra branch, one cherry-pick at a time.

The companion of curated-unbrand.py, and the second half of docs/curated-to-mantra.md's
pipeline as it was actually run. The rewrite (filter-branch with curated-unbrand.py as the
tree filter and a `Pulled-From: curated/curated@<sha>` trailer stamped by its message
filter) leaves a branch `tmp/curated-src` in Mantra's names; this replays commits off it
by their *original* sha, in the order you give, onto the current branch of <worktree>.

  curated-replay.py pick  <worktree> <orig-sha>...     cherry-pick each, resolving by the rules below
  curated-replay.py check <worktree> <orig-cut-sha>    files differing from the rewritten cut

Resolutions are rules, and anything the rules do not cover stops the run with the commit
aborted, so a new conflict is a decision rather than a guess. What the rules encode:

  * a branch join -- the first commit off one side of an upstream merge landing on top of
    the other side -- is resolved by taking the join files exactly as upstream's own merge
    left them (TAKE_FROM). Not a union: a union orders the two appended blocks its own way,
    and every later commit that edits the block then misses its base and its deletions
    silently fail.
  * files whose lines are unique by construction (string keys, imports, route
    registrations, table rows) get a line-set three-way merge over diff3 hunks (UNION):
    ours, minus what theirs deleted from base, plus what theirs added that the file does
    not already hold anywhere.
  * the library pin follows the commit being replayed (PIN), because each upstream commit
    compiled against the pin it recorded; `5efeae76` named a branch commit that no longer
    exists and is mapped to the master commit with the identical tree.
  * a handful of (commit, file) pairs where one side simply wins (THEIRS, DELETE), each
    with the reason written into the commit.

Every resolution is appended to the landed commit's message as a paragraph, ahead of the
trailers, so the branch explains itself.
"""
import os, re, subprocess, sys

STRINGS = 'composeApp/src/commonMain/composeResources/values/strings.xml'
NAVHOST = 'composeApp/src/commonMain/kotlin/press/mantra/compose/ui/composable/navigation/MantraNavHost.kt'
UI = 'composeApp/src/commonMain/kotlin/press/mantra/compose/ui/composable/'
GITLINK = 'lightning-kmp-app'

UNION = {STRINGS, NAVHOST, 'docs/README.md'}
PIN = {
    '366b0177': '59c11ed926ac05c820b98afb4cc961a5a3506310',   # the nsec line was written against 59c11ed's NostrKeyManager
    '5efeae76': '84cc44c855e3d173e26016b807c449153a3af597',   # upstream: 01962f3, a rebased-away branch commit with this tree
    '00c36ec5': '84cc44c855e3d173e26016b807c449153a3af597',
}
TAKE_FROM = {  # (orig, path) -> the original merge commit whose rewritten tree supplies the file
    ('1e52fc8f', STRINGS): 'c8de3a1f', ('1e52fc8f', NAVHOST): 'c8de3a1f',
    ('0634487e', UI + 'CuratedSuggestionListScreen.kt'): 'fedbe724',
    ('0634487e', STRINGS): 'fedbe724', ('0634487e', NAVHOST): 'fedbe724',
}
AFTER = {  # orig -> files folded in after the pick, from the merge that edited them outside its conflicts
    '0634487e': [(UI + 'AcceptCuratedSuggestionScreen.kt', 'fedbe724'), (UI + 'BroadcastGroupSignedEventScreen.kt', 'fedbe724')],
}
THEIRS = {
    ('42f3a697', 'composeApp/src/commonMain/kotlin/press/mantra/compose/network/relays/RelaysSocketManager.kt'),
    ('42f3a697', 'composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/NavigationViewModel.kt'),
    ('e3cc23ae', UI + 'KeyRecoveryScreen.kt'),
}
THEIRS_WHY = {
    '42f3a697': "this commit's import block taken whole -- Mantra had already moved the nostrPublicKey() import to the library's (39fb64b6), and this commit removes the call it served",
    'e3cc23ae': 'the class comment this commit rewrites is taken whole; the only base difference was the dropped rebrand capitalising the brand in one word of it',
}
DELETE = {('55664cc7', 'composeApp/src/jvmTest/kotlin/press/mantra/compose/ui/composable/GroupNostrProfileSectionJvmTest.kt')}


def main():
    mode, wt, args = sys.argv[1], sys.argv[2], sys.argv[3:]
    def git(*a, check=True, **kw):
        return subprocess.run(['git', *a], cwd=wt, capture_output=True, text=True, check=check, **kw)
    rewritten = {}
    for line in git('log', '--format=%H %(trailers:key=Pulled-From,valueonly)', 'origin/mantra..tmp/curated-src').stdout.splitlines():
        parts = line.split()
        if len(parts) == 2: rewritten[parts[1].split('@')[-1][:8]] = parts[0]
    rw = lambda orig: rewritten[orig[:8]]

    if mode == 'check':
        print(git('diff', '--name-only', rw(args[0]), 'HEAD').stdout, end=''); return

    def union_resolve(path):
        p = os.path.join(wt, path); lines = open(p, encoding='utf-8').read().split('\n')
        state = None; outside = set()
        for l in lines:
            if l.startswith('<<<<<<< '): state = 'in'; continue
            if l.startswith('>>>>>>> '): state = None; continue
            if state is None: outside.add(l)
        out = []; state = None; ours = base = theirs = None
        for l in lines:
            if l.startswith('<<<<<<< '): state = 'ours'; ours, base, theirs = [], [], []; continue
            if l.startswith('||||||| ') and state == 'ours': state = 'base'; continue
            if l == '=======' and state in ('ours', 'base'): state = 'theirs'; continue
            if l.startswith('>>>>>>> ') and state == 'theirs':
                removed = {b for b in base if b not in theirs and b.strip()}
                added = [t for t in theirs if t not in base]
                kept = [o for o in ours if o not in removed]
                have = outside | set(kept)
                out.extend(kept); out.extend(a for a in added if a not in have or a.strip() == ''); state = None; continue
            if state == 'ours': ours.append(l)
            elif state == 'base': base.append(l)
            elif state == 'theirs': theirs.append(l)
            else: out.append(l)
        open(p, 'w', encoding='utf-8').write('\n'.join(out))

    def amend_note(note):
        msg = git('log', '-1', '--format=%B').stdout.rstrip('\n').split('\n'); i = len(msg)
        while i > 0 and re.match(r'^[A-Za-z-]+: ', msg[i - 1]): i -= 1
        body, trailers = msg[:i], msg[i:]
        while body and body[-1] == '': body.pop()
        subprocess.run(['git', 'commit', '-q', '--amend', '-F', '-'], cwd=wt, input='\n'.join(body + ['', note, ''] + trailers) + '\n', text=True, check=True)

    for orig in args:
        o8 = orig[:8]; sha = rw(o8); subject = git('log', '-1', '--format=%s', sha).stdout.strip(); notes = []
        r = git('-c', 'merge.conflictStyle=diff3', 'cherry-pick', '--allow-empty', sha, check=False)
        if r.returncode != 0:
            stuck = []
            for f in git('diff', '--name-only', '--diff-filter=U').stdout.split():
                if f == GITLINK and o8 in PIN:
                    git('update-index', '--cacheinfo', f'160000,{PIN[o8]},{GITLINK}'); notes.append(f'gitlink -> {PIN[o8][:8]}, the pin this commit compiles against')
                elif (o8, f) in TAKE_FROM:
                    src = TAKE_FROM[(o8, f)]; open(os.path.join(wt, f), 'w').write(git('show', f'{rw(src)}:{f}').stdout); git('add', f)
                    notes.append(f'{os.path.basename(f)}: taken as the original merge {src} left it, this being the branch join')
                elif (o8, f) in THEIRS:
                    git('checkout', '--theirs', '--', f); git('add', f); notes.append(f'{os.path.basename(f)}: {THEIRS_WHY[o8]}')
                elif (o8, f) in DELETE:
                    git('rm', '-q', '-f', f); notes.append(f'{os.path.basename(f)}: deleted, as this commit deletes it upstream')
                elif f in UNION:
                    union_resolve(f); git('add', f); notes.append(f'{os.path.basename(f)}: line-set three-way merge, both sides\' additions kept and this commit\'s deletions applied')
                else:
                    stuck.append(f)
            if stuck:
                git('cherry-pick', '--abort', check=False); sys.exit(f'STUCK at {o8} {subject}: no rule for {stuck}')
            r2 = subprocess.run(['git', '-c', 'core.editor=true', 'cherry-pick', '--continue'], cwd=wt, capture_output=True, text=True)
            if r2.returncode != 0: sys.exit(f'STUCK continuing {o8}: {r2.stderr.strip()[:300]}')
        for path, src in AFTER.get(o8, []):
            open(os.path.join(wt, path), 'w').write(git('show', f'{rw(src)}:{path}').stdout); git('add', path)
            notes.append(f'{os.path.basename(path)}: brought to the state the original merge {src} left it in, an edit that merge made outside its conflicts')
        if o8 == '5efeae76':
            git('update-index', '--cacheinfo', f"160000,{PIN[o8]},{GITLINK}")
            notes.append('gitlink -> 84cc44c: upstream pinned 01962f3, a branch commit since rebased onto the library\'s master as 84cc44c with an identical tree')
        if notes:
            git('commit', '-q', '--amend', '--no-edit', check=False)
            amend_note('Replayed onto Mantra by docs/curated-to-mantra.md: ' + '; '.join(notes) + '.')
        print(f"{o8} {'resolved' if notes else 'clean':8s} {subject[:80]}")


if __name__ == '__main__':
    if len(sys.argv) < 4 or sys.argv[1] not in ('pick', 'check'): sys.exit(__doc__)
    main()
