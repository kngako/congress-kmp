# The third pull: the profile preview, the extended FAB, and half of a commit

Eleven commits reached `curated/curated` on 2026-09-13, after the
[second pull](./curated-to-mantra-profiles.md) had landed — ten ordinary and one merge,
`29027f2b..272b6316` — and they sat unpushed in a local clone long enough to be asked
about. This is what they are, which of them Mantra took, and the three places this pull
differed from the two before it. It assumes the [first plan](./curated-to-mantra.md)'s
method and the second's exactness check, and says only what changed.

**Built 2026-09-13**, the day they were pushed: eight commits landed whole, one in part,
one merge as nothing, in two phases, at `jvmTest` 871 → 914 and `testDebugUnitTest`
420 → 424 with every audit budget met.

## The inventory

| line | commits | what | verdict |
|---|---|---|---|
| **A profile preview before a chat** — [npub-profile-preview.md](./npub-profile-preview.md) | `db268a53` plan · `37c6f7ea` `8973c005` `923b59ec` `d6490985` · `b3bcc060` record · `80735df8` · `272b6316` | `ProfilePreviewScreen` and its view model, with `DirectMessagePeerSync` behind the button's readiness; the new-chat dialog opens the person instead of starting the chat; the button knows whether a room with them already exists; the preview's action is the app's extended FAB; a member row on the group's screen opens the preview | **pulled**, all eight |
| **One extended FAB for every primary action** | `ca6c16be` | `ExtendedFab`, and twenty screens moved onto it — thirteen of them Mantra's, including `AddArtifact`, `AddChapter`, `AddDialect`, `AddTranslationArtifactVersion`, `TranslateChunk`, `DkgRitual` | **pulled**; the seven screens Mantra does not have resolve as *still deleted* |
| **Broadcast every accepted entry** | `d2a4298d` | a *Broadcast all* on the curated-entries screen, and — to make it — `sendToRelays` lifted into `BroadcastGroupSignedEventViewModel`'s companion, a `RelayOutcomeLabel` widget, and the broadcast screen reading it | **pulled in part**: the broadcast half; the entries half is line D |
| the merge | `f4b0347e` | the preview branch joining `curated` | lands as nothing; its two join files carried only line D's side, see below |

## Three things that differed

### 1. A brand token the normaliser did not know

The preview's view model names a readiness state `NotYetOnCurare`. `\bCurare\b` cannot
see a brand with no word boundary in front of it, and the first rewrite left seventeen
of them across the code, the tests and the note. The fix is a rule, not an edit —
`(?<=[a-z0-9])Curare(?=[A-Z]|\b)` → `Mantra`, for the Curare generation only, since a
CamelCase-embedded *Curated* is product vocabulary at the tip — and a second rewrite:
zero tokens left, and the earlier forty-nine rewritten commits came out hash for hash
the same, so nothing already landed was disturbed. `0728f3f9`.

### 2. Half of a commit, taken as a partial pick

`d2a4298d` is one commit in two lines. Its button, view model, state, tests, five strings
and nav-host wiring belong to the curated-entries screen, which Mantra took out; but the
send it lifted out of the broadcast screen into `sendToRelays`, the widget that turns a
relay's answer into words, and the screen reading it are the broadcast line Mantra kept,
and they touch only files both trees hold. Dropping the commit whole would have left four
shared files differing from the fork's and put the FAB commit, which edits the same
screen, on a base the fork's never had. The driver gained a `PARTIAL` rule: the diff
restricted to the kept paths, applied and committed under the original message and
trailer, with what was left out written into the note. `b3379f81`, and the FAB commit
then applied to the screen cleanly.

What this does to the merge: `f4b0347e`'s two join files, `strings.xml` and the nav
host, carried the preview side's additions — which the preview commits had already
brought — plus `d2a4298d`'s entries strings and wiring. So the merge was not a source of
anything Mantra wanted, and for the first time a join's files were *not* taken from the
merge. The residual records the difference exactly: five strings and their comment, and
two nav-host hunks, one of which is a comment that says "as the entries screen is for
its Broadcast all" where Mantra's still says "the one screen", which on Mantra is true.

### 3. Two conflicts the compiler found, not git

Both in `272b6316`, which applied without a textual conflict.

- Upstream's `ChatRoomDetailScreen` had no use left for `ImplementationPendingRoute`
  once `808a3459` dropped the library, dialects and projects sections, so the commit
  removes the import. Mantra kept the sections, and its projects section still routes
  *Add new project* through that import. Kept, with the reason in the commit.
- The test it adds, `ChatRoomMemberRowJvmTest`, composes the screen against
  `GroupSignedWorkFixtures`, a jvmTest fixture that went with lines B, D and H. The
  test uses three of its members — the room id, the active key, `localChatRoom()` — and
  none of the signed work. The fixture is recreated on Mantra reduced to the room alone,
  under the same names, so the test lands byte for byte and the next upstream test
  written against the room compiles here too. It is the one file this pull turned from
  *upstream only* into *both have, differently*.

The seven modify/delete conflicts at `ca6c16be` were the expected kind and resolved by
rule: `AcceptCuratedSuggestion`, `AddGroupPost`, `EditGroupCuratedSchema`,
`EditGroupNostrProfile`, `EditGroupRelays`, `GroupCuratedEntries`,
`ProposeGroupEvent` — all still deleted.

## The record

| | Phase A — the preview line to its record | Phase B — the broadcast half, the FAB, the two on top |
|---|---|---|
| commits | `69aa43a1` `f84ae133` `39c056a2` `5ba888d9` `2adc5036` `a8ba589e` | `b3379f81` (partial) `883bf1c8` (seven deletes) `e66c174b` `4899dcbc` (two hand fixes) |
| picks | 6 clean | 1 partial, 1 by rule, 2 clean |
| exactness | 139 residual files before and after; nothing moved | 140; moved: `strings.xml` (the five strings), `MantraNavHost.kt` (the two hunks), `ChatRoomDetailScreen.kt` (one import), `GroupSignedWorkFixtures.kt` (recreated); twelve upstream-only files changed upstream, all of them line D's or the removed screens' |
| compile | both targets clean | both targets clean |
| `jvmTest` | 871 → 909 | 909 → 914 |
| `testDebugUnitTest` | 420 → 424 (`DirectMessagePeerSyncTest`) | 424 |
| audit | all budgets met; composable literals 30 | all budgets met; 30 |
| `strings.xml` | 455 → 460 | 460 |
| schema | unchanged, v20 | unchanged, v20 |

The rewrite: 60 commits in 128 s, twice. The library pin: `84cc44c` throughout.

## What the user gets

A member's row on a group's screen, and the person a new chat is started with, open a
profile preview: the cached profile at once while the relays refresh it, and one
button that says whether a chat with them exists, can be started, or cannot yet because
they have not set up messaging on Mantra. And one extended FAB, the same shape and the
same disabled state, on every screen whose primary action used to spell it its own way
— including the translation screens Curare kept and fixed.

## What is left

- **The other half of `d2a4298d`** is line D and stays out; the residual names its lines.
- **The reverse direction.** `ExtendedFab` and the preview are now identical on both
  trees; the only Mantra-side edits in this pull are the kept import and the reduced
  fixture, neither of which the fork wants. Nothing new to offer upstream from here.
- **The fork decision** stands as the first plan left it. Three pulls in one day with
  the tooling in place cost minutes each of machine time; each also found one thing —
  a pin, a CamelCase brand, a commit in two lines — that a rebrand-free fork would not
  have had to find.
