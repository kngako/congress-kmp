# Pulling Curated back into Mantra

Curated forked from this repository on 2026-09-09 and has since landed thirty-nine
commits that Mantra has none of: a group's own nostr identity, curated lists over the
kinds 31888–31890, sign-in with a recovery phrase, an nsec or an npub, a back button on
every pushed screen — and two rebrands, 744 and 765 files each, underneath all of it.
This is which of that work Mantra wants, in what order, and how to land it under
Mantra's names without touching seven hundred files by hand.

Read [nsec-sign-in.md](./nsec-sign-in.md) and [npub-sign-in.md](./npub-sign-in.md) for
the identity work's own reasoning once they arrive (Phases 3 and 5 bring them); nothing
here repeats it. [shared-key-derivation.md](./shared-key-derivation.md) states the one
rule this whole exercise is built around — that two strings in this tree are hash inputs
and must never be renamed — and the rebrands upstream were careful about it, which is the
only reason a mechanical pull is possible at all.

**Built, Phases 0–5, on 2026-09-13**, on the branch that also carries `f4434ab1` and
`39fb64b6`: thirty commits pulled from the fork, each with a `Pulled-From` trailer, on
top of Phase 0's two native ones. Phase 6 is not done: its two reverse-pulls land in the
other repository, and its last item is a decision. The plan below is kept as written,
because the code reads better against the argument it came from than against a summary
of itself; where the execution chose differently the [Phase 1 record](#phase-1--the-dry-run)
says so, and it did so in seven places worth reading:

| what the plan said | what it turned out to be |
|---|---|
| each phase is a `rebase --rebase-merges --onto` of a cut | a cherry-pick per commit, in topological order. A recreated merge whose other parent was replayed in an earlier phase would be pointed at the *pre*-replay commit; so the four merges land as nothing and their resolutions are folded in where the conflicts surface. The driver is [`docs/scripts/curated-replay.py`](./scripts/curated-replay.py) |
| the pin stays at `84cc44c` throughout | it follows the commit. The nsec line writes through `NostrKeyManager`, which `84cc44c` renamed away; the compiler produced eight unresolved references at the Phase 3 cut. `366b0177` moves the pin to `59c11ed` and `5efeae76` brings it to `84cc44c`, so every commit builds against the library it names. A populated submodule made git fast-forward the gitlink past the rule once, silently; the rule is now unconditional |
| take the rebrand residue *or* leave it for Phase 2 | Phase 0 took it, natively worded, so the three rewritten brand commits were dropped outright rather than squashed |
| Phase 2, 3 and 5 replay with no conflicts | Phase 3 had two, both foreseen in kind: the import block `39fb64b6` had already moved (theirs), and one word of a class comment the dropped rebrand had capitalised (theirs). Phase 5 had none |
| Phase 4's three conflicts, resolved by hand | the same three, but the join files are taken from upstream's own merges (`c8de3a1f`, `fedbe724`) rather than unioned: a union duplicates shared lines, ignores the other side's deletions, and — the one that hid longest — orders the joined blocks its own way so later commits miss their base. `fcc19f95`'s deletion of two strings survived two attempts before that was understood |
| the exactness diff is the logo plus `808a3459`'s three files | plus the fourteen files Mantra changed on its own side: Phase 0's prose at the protected names, `39fb64b6`'s four, and this document with its two scripts. Seventeen files, every one accounted for, at every cut |
| 1,036 jvm tests at the end | 1,039 — the three from `39fb64b6` — and per phase 736, 864, 905, 1,007, 1,039; `testDebugUnitTest` 403, 486, 495, 530, 530; every phase clean on both compilers with every audit budget met |

**Lines B and D were taken out again the same day**, and H with them, by a product
decision rather than a merge problem: the group's nostr profile, its relay lists, its
posts, the curated schemas and the curated entries are not Mantra's product, and the
[third decision](#3-curated-lists-are-not-mantras-product-and-mantra-should-take-them-anyway)'s
"hide the two rows behind a constant" was not enough once the profile, relays and
posts rows went with them. What went: the five rows and the twelve screens behind
them, `nostr/curated/`, the readings (`GroupNostrProfile`, `GroupRelayList`,
`GroupPost`, `GroupCuratedSchema`, `GroupCuratedEntry`, `CuratedSuggestion`), the
`applyInnerEvent` arms for kinds 0, 1, the four relay-list kinds, 31889 and 31890,
the `ChatRepository` and `NostrRepository` reads that fed them, the pasted-event
proposal (`GroupEventProposal`, whose accepted kinds were exactly those), the
`ProposedEvent` summaries for the same kinds, 226 strings, and every test that came
with them. What stayed from the pull: F (the back button), G and I (sign-in), J (the
library chore), and E (broadcast) -- which nothing opens now, since its button sat on
the five removed screens and its relay seeding read the group's relay lists and
schemas; `defaultRelaysFor` is the app's publish set, and the route and the button
are kept against the day something wires one in. Every pre-existing file the removed
commits touched is back at its pre-pull content plus the kept lines' hunks; the
exactness check for that is `git diff ba0830a3 -- <file>` showing only the back
button, broadcast's `groupSignedEvent`, and the sign-in reads. Tests afterwards: 826
jvm, 413 common, every audit budget met. The seam this leaves for the next pull is the
one the third decision predicted: any upstream commit that touches the identity block
will conflict at `ChatRoomDetailScreen`, `MantraNavHost` and `strings.xml`, and the
resolution is to drop it.

The upstream moved before this landed: `curated/curated` went from `86cb876b` to
`29027f2b`, ten commits for several profiles on one device, one of them the fork's
first schema migration (version 20). They are the next pull, and the schema change is
why they get their own decision rather than a ride on this one — which is
[curated-to-mantra-profiles.md](./curated-to-mantra-profiles.md), planned and dry-run the
same day.

Written against `origin/mantra` at `ba26c0b1` and `curated/curated` at `86cb876b`;
executed on 2026-09-13.

## The shape of the fork

Two remotes: `origin` is `mantra/mantra-kmp`, `curated` is `curated/curated-kmp`, both
on code.sigidli.com. The merge-base of `origin/mantra` and `curated/curated` is
`ba26c0b1` — which *is* `origin/mantra`. Mantra has not moved since the fork, so Curated
is strictly ahead of it, by thirty-nine commits.

That has a consequence worth stating before anything else. **`git merge curated/curated`
into `mantra` is a fast-forward.** It would not merge anything; it would make Mantra
*become* Curare — the package rename, the icon, and the commit that deletes Mantra's own
library, dialects and projects sections included. It is the one thing not to do, and it
is the thing git does by default.

The thirty-nine are thirty-five ordinary commits and four merges, and the branch
structure matters for the phasing later:

```
*   86cb876b  Merge claude/npub-readonly-signin into curated
|\
| * 3116eb8a … 78807fe9   npub sign-in: 12 commits (Phase 5)
* | 55664cc7  rows on the group's screen
* | fedbe724  Merge curated into the suggestion-accept branch   (carries resolutions)
|\|
| *   ccbdbf7a  Merge curated into the back-button branch         (carries resolutions)
| |\
| * | 0634487e  back button on every pushed screen
* | | 732a4527  accepted entries on the group's screen
* | | fcc19f95  accept a suggestion into the list
| |/
|/|
* | 6ae5a967  broadcast button under each signed event
|/
*   c8de3a1f  Merge curated into the nsec branch
|\
| * 1e52fc8f  the queue of a curated list
* | 2326839e … daae63d5   nsec sign-in: 8 commits (Phase 3)
|/
* 392b90b8  an unsigned event, pasted
* 930d37c8  the lists a group curates (kind 31889)
* 808a3459  drop the library, dialects and projects sections     <- Mantra drops this
* cd3108e9  Update logo                                           <- and this
* e6aee792  rebrand curated -> curare                             <- and the brand line
* 334e6dd1  what the group says out loud (kind 1 posts)
* 4c0ed0c1  the group's own nostr identity (kind 0, relay lists)
* d26cf6c7  retire the brand before mantra (Torch)
* 3bc8be53  rebrand mantra -> curated
```

Two brand generations sit under the features. `3bc8be53` made the app Curated —
`press.mantra.*` became `com.it.curated.*`, `MantraDatabase` became `CuratedDatabase`,
and so on — and `e6aee792` made it Curare, `to.curare.*` and `CurareDatabase`. The two
group-identity commits are written in the first vocabulary; everything from `808a3459`
on is written in the second. A pull has to read both.

What the rebrands did **not** touch is the reason a pull is tractable. Their own messages
record it: the twelve `Mantra*` Room entities keep their names because those names are
the SQLite table names; `SharedKeyDerivation.TWEAK_TAG` and
`ChillDkgRitualManager.HOST_KEY_DERIVATION_TAG` keep their `mantra/` prefixes because
they are hash inputs; `Relays.ephemeral` keeps `wss://ephemeral.mantra.press` because it
is a running relay. So on both sides the database is at version 19 with the same nineteen
schema files, every table has the same name, every derived key is the same key, and the
app talks to the same relay. **Nothing in the thirty-nine commits adds a table or a
migration** — three DAO queries and no entity field; after a full build of the pulled tree
Room regenerates nothing and `19.json` is byte-identical to Mantra's. Every feature reads
off `GroupSignedEvent` rows that already existed, or off two new files in the node-data
directory (`nostr-keys.dat`, then `nostr-credentials.dat`). The lightning-kmp-app submodule is pinned to the same commit
on both sides, `84cc44c`, since `f4434ab1` brought Mantra's pin forward.

## The inventory

Every commit, grouped by the line of work it belongs to. Sizes are `git show --shortstat`
sums; the brand line's is what a hand-merge would have had to read.

| line | commits | size | verdict |
|---|---|---|---|
| A. the brand line | `3bc8be53` `d26cf6c7` `e6aee792` `cd3108e9` | 1,593 files, ±10.5k | **not as commits.** Their residue, yes — see [the first decision](#1-the-brand-line-and-what-is-worth-keeping-from-it) |
| B. the group's nostr identity | `4c0ed0c1` `334e6dd1` | 40 files, +5.3k | **pull** |
| C. drop the library, dialects, projects | `808a3459` | 4 files, −228 | **do not pull.** It deletes Mantra's product |
| D. curated lists | `930d37c8` `392b90b8` `1e52fc8f` `fcc19f95` `732a4527` | 86 files, +12.7k | **pull** — the [third decision](#3-curated-lists-are-not-mantras-product-and-mantra-should-take-them-anyway) |
| E. broadcast | `6ae5a967` | 18 files, +2.0k | **pull** |
| F. a back button on every pushed screen | `0634487e` | 50 files, +532 −288 | **pull** — a fix |
| G. nsec sign-in | `daae63d5` `42f3a697` `366b0177` `22061601` `d61d3560` `e3cc23ae` `647ee815` `2326839e` | 84 files, +5.3k | **pull** |
| H. a row per kind of signed work | `55664cc7` | 34 files, +4.1k −1.5k | **pull**, with C dropped underneath it |
| I. npub read-only sign-in | `78807fe9` `dc8a1091` `15766596` `5efeae76` `7db863e3` `bfad1f39` `e31033e8` `a586b7c1` `315e9331` `f866b9d1` | 67 files, +3.4k | **pull** |
| J. library chores | `00c36ec5` `3116eb8a` | 3 files | **pull** `3116eb8a`; `00c36ec5`'s pin move is already Mantra's |
| K. the four merges | `c8de3a1f` `ccbdbf7a` `fedbe724` `86cb876b` | — | replayed *as merges*; two carry hand resolutions |

### B. The group's own nostr identity

A Marmot room's id is the pubkey it signs as, so every group has had a nostr identity
since it had a key and no way to say anything about it. `4c0ed0c1` adds the kind 0 and
the four relay lists (NIP-65, NIP-17, NIP-50, NIP-51), each proposed to the quorum like
anything else the group signs; `334e6dd1` adds kind 1 posts in the group's own name.
Both are *readings* of `GroupSignedEvent` rows gated on `verifies` — the room as author,
the id as the hash, the signature real — and both close a hole in `applyInnerEvent`
where a group-signed kind would otherwise render as raw JSON in the transcript. The
second commit's kind 1 arm is the careful one: it verifies and, on failure, falls through
to `unsupported` rather than dropping the event, because a kind 1 is content somebody
meant.

Mantra wants this without qualification. A translation collective is exactly a group
that needs a public name, a place to be found, and a way to announce a finished chapter
under a key no single member controls. Two commits, no schema, and the tests sign their
fixtures with real FROST quorums.

### C. Dropping the library, dialects and projects

`808a3459` removes the three work sections from the group screen and the two repository
reads that fed them. For Curated that was dead weight; for Mantra it is the product. The
question was never whether to take it but what taking the *later* commits costs when it
is left out, because `55664cc7` restructures the same screen on top of it. That was
measured — see [the second decision](#2-mantra-keeps-its-sections-and-git-does-the-work).

### D. Curated lists

The curated-list NIP from the bitcoin.mov repository: a group publishes a *schema*
(kind 31889) defining a list anyone may suggest entries to; strangers publish
*suggestions* (31888) against it; the group's quorum accepts one by signing a *canonical
entry* (31890) that points back at the suggestion. `930d37c8` is the schema and its
editor, ported tag for tag with the NIP's own example as the fixture; `392b90b8` is the
untyped way in — an unsigned event pasted as JSON and filed by kind once signed, which
exists so that `npm run schema:dry` in the bitcoin.mov repo can be pasted rather than
retyped; `1e52fc8f` is the queue, every suggestion in reply to a schema, read off the
relays the schema named; `fcc19f95` accepts one into the list, seeded into a form the
schema drives; `732a4527` shows what the group has accepted. All of it lives in a new
`nostr/curated/` package plus screens, and all of it reads the same `GroupSignedEvent`
rows.

### E. Broadcast

Until `6ae5a967` nothing a group signed reached a relay: `FrostSigningManager.complete`
filed the rows and the only way out was the copy button on the key-state sheet. This adds
a Broadcast button under every signed event, a screen that shows and edits the relays,
sends, and reports what each answered. It deliberately bypasses the durable
`BroadcastNostrEventRequest` queue, because that queue hangs off a `NostrEvent` row and a
group's event must not become one (the home feed would show it as anybody's, and the
sync loop would offer it to relays the screen never named). Mantra's groups sign
chapters, translations and key states; none of them reach a relay today either.

### F. A back button on every pushed screen

Twenty-two pushed screens had no back affordance. Android's system back and iOS's edge
swipe hid it; the desktop target, which Mantra also ships, has neither, so those screens
were dead ends there. One widget, `NavigateBackButton`, replaces four spellings of the
arrow across forty-one sites; four screens with no app bar got one. Eight of the fifty files
are Mantra's translation screens — `AddArtifactScreen`, `AddChapterScreen`,
`AddDialectScreen`, `ChapterDetailScreen`, `TranslationChapterScreen` and the rest —
which Curated kept and fixed.

### G. nsec sign-in

Phases 1–6 of `docs/nsec-sign-in.md`, which arrives with the commits. `42f3a697` puts an
`Identity` type in front of the wallet — the nostr key, the x-only pubkey, the
per-identity preferences and an *optional* `PhoenixBusiness` — replacing the expression
`activeWallet?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()` at its ten
sites and `StateFlow<ActiveWallet?>` at its twenty-five. `366b0177` lists and starts
identities from both stores. `22061601` turns `SignInToProfileScreen` from the sentence
"Sign in is not yet available while Mantra is in alpha testing" into a screen: one field
that recognises a recovery phrase or an nsec, confirms the npub it will sign as, and
writes the secret before anything else. `d61d3560` fixes something not nsec-specific — the
sign-in sync asked only `ephemeral.mantra.press` for the user's profile, so a key that had
lived on Damus for three years found nothing; it now asks the indexer relays and follows
the user's own kind 10002 one hop. `e3cc23ae` gives an identity with no phrase a recovery
screen for its nsec and the app's first real "forget this key".

The library half — `NostrKeyManager` when this line was written, `NostrCredentialManager`
by the end of line I — is in lightning-kmp-app `84cc44c`, which Mantra's pin already
carries; those library commits exist to serve these two lines.

### H. A row per kind of signed work

`55664cc7` collapses the five identity sections (profile, relays, posts, schemas, entries)
into five rows in the shape of the signing-key row, each opening its own screen, because
five sections of cards had made the group screen as long as everything the group had
ever said. Five routes, screens, view models and states; the broadcast button becomes a
widget. It sits on top of C, and how Mantra takes it without C is the second decision.

### I. npub read-only sign-in

Phases 1–6 of `docs/npub-sign-in.md`. `15766596` makes the identity's private key
nullable, with `canSign` as the one place to ask. `5efeae76` replaces `nostr-keys.dat`
with `nostr-credentials.dat` — one typed entry per public key, secret or public, so a
profile signed in read-only and the same profile with its nsec pasted later are one
entry, upgraded in one write — and brings the matching library commit, which is
`84cc44c`, Mantra's current pin. `7db863e3` lists and starts a keyless identity and
builds quartz's read-only `KeyPair` in exactly one place (the trap it guards is
`KeyPair(privKey = null)`, which is quartz's "make me a new key"). `bfad1f39` recognises
an npub in the sign-in field. `e31033e8` is the product half: a `LocalCanSign`
composition local, provided once above the navigation suite, hides every action that
would need a signature — new chat, follow, send message, key packages — rather than
disabling it, and the empty state beside each says why. `a586b7c1` is sign out, for a
read-only identity only, the first real one in the app.

### J. Library chores

`3116eb8a` adds `branch = master` to `.gitmodules` so `git submodule update --remote`
follows the library's default branch; take it. `00c36ec5` moved Curated's pin to
`84cc44c`; Mantra is already there.

## Three decisions

### 1. The brand line, and what is worth keeping from it

None of the four commits lands as itself. But run through the name-rewrite described
below, the two rebrand commits collapse from 744 and 765 files to sixteen and
twenty-one, and what is left is not brand at all — it is the *reasoning the rebrands
added about the names they refused to change*: a comment on `TWEAK_TAG`, one on
`HOST_KEY_DERIVATION_TAG`, one on `Relays.ephemeral`, a paragraph in
`docs/shared-key-derivation.md`, and the relocation of a stale `press/auxiliary`
template test into the real package. Mantra should have those comments; the next person
to touch a `mantra/` string in Mantra is as likely to "finish the rename" as anyone in
Curated was. Take them as **one squashed commit with a new message** ("docs: say why the
two hash tags and the relay host must never be renamed") and rewrite the sentences that
describe rebrands Mantra never had — they are listed under
[Risks](#risks-and-the-silent-ones-in-particular).

`d26cf6c7`, the Torch retirement, is a different case. Mantra is still Torch in four
places: `TorchTheme` (116 references), `UserAgent.APP_NAME` and `CLIENT_NAME` (the
`client` tag on every relay list this app publishes), two user-facing error strings in
Kotlin ("tell X to use Torch", "finished setting up Torch"), and iOS, where
`Config.xcconfig` still builds `PRODUCT_NAME=Torch` under the bundle id
`ac.aux.compose.Aux` — two brands ago. That is Mantra's own debt and Phase 0 pays it
natively, as `MantraTheme` and "Mantra", so that Curated's `CurareTheme` maps onto a name
that means something rather than onto the brand before last.

The logo commit is Curated's icon. Mantra keeps its own.

### 2. Mantra keeps its sections, and git does the work

Dropping `808a3459` was measured rather than reasoned about. With it removed from the
replay, `930d37c8` and `1e52fc8f` merged into `ChatRoomDetailScreen` without conflict
(the schema section is inserted where the library section is removed, different hunks),
and `55664cc7`'s rewrite of the screen merged without conflict too. The screen that
results reads, top to bottom: signing key, then the five rows, then Propose event, a
divider, **Library, Dialects, Projects**, then Subgroups — `ChatRoomDetailUIState.Loaded`
still carries `artifacts` and `dialects`, and `mantraRepository` is still passed. It
compiles for both targets and passes all 1,036 jvm tests, including
`GroupSignedWorkRowsJvmTest`, which asserts only that Subgroups sits *below* the identity
block and does not mind three sections in between.

The one conflict it produces is a modify/delete on
`GroupNostrProfileSectionJvmTest.kt`: `808a3459` had re-anchored that test from the
"Library" heading to "Subgroups", and `55664cc7` deletes the file in favour of
`GroupSignedWorkFixtures` and six new tests. Resolution: delete, as `55664cc7` does.

A follow-up Mantra may want, and should write natively rather than pull: the three
sections in the same row shape as the five above them, so the screen is one idiom. That
is a small change against a settled layout, not a merge problem.

### 3. Curated lists are not Mantra's product, and Mantra should take them anyway

This is the only real product question in the pull, so the reasoning is set out in full.

*Against:* kinds 31888–31890 are Curated's reason to exist. A translation collective has
no obvious list to curate, and two rows — "Curated schemas", "Curated entries" — appear
on every group screen.

*For, on cost:* excluding the five commits is not deleting five commits. `392b90b8`
(paste) routes kind 31889 alongside 0, 1 and 10002; `6ae5a967` (broadcast) seeds an
entry's relays from its schema; `55664cc7` (rows) builds five rows of which two are
these; `0634487e` (back button) touches the suggestion screens; and 165 of the 306 strings
added since the fork are theirs, interleaved with the rest. Carving them out means editing five
other commits by hand and *then* owning a group-screen layout that differs from
Curated's, so that every future pull conflicts in the same place again. Including them
costs no schema, no migration, no query on a screen that does not open them, and a
self-contained package under `nostr/curated/` whose tests are the NIP's own vectors.

*For, on principle:* the whole approach below rests on Mantra's tree being a superset of
Curated's non-brand tree, because that is what makes the *next* pull a replay rather than
a negotiation. Every feature left out is a permanent conflict seam.

*So:* pull them. If product decides the two rows should not show on Mantra, hide the two
`SignedWorkRow` calls behind a constant — a two-line, reversible change against a tree
that is otherwise identical to upstream — rather than surgery on five commits.

## How it lands: rewrite the names, then let git merge

The principle is that **a Curated commit written in Mantra's vocabulary applies to Mantra
as if it had been written there**, because Mantra has not moved since the fork. So the
work is not merging; it is translating, once, mechanically, and then replaying history
with git's three-way merge doing what it is good at.

Three approaches were considered. Merging `curated/curated` fast-forwards into Curare,
above. Cherry-picking the original commits and fixing up names is hopeless: two 700-file
renames mean every patch's paths and every `import` line in its context are wrong, so
every file of every commit conflicts. The third — rewrite the *history* into Mantra's
names and replay it — is what was measured, and it works.

### The normaliser

[`docs/scripts/curated-unbrand.py`](./scripts/curated-unbrand.py) maps both brand
generations onto Mantra, in a tree (for `filter-branch`) or in a patch file. Its rules
are a table of brand tokens: `to.curare.` and `com.it.curated.` to `press.mantra.`; the
generated-resources package; the app-level identifiers (`CurareDatabase` and
`CuratedDatabase` to `MantraDatabase`, the nav host, the navigation suite, the global,
the application, the converters, the shapes, the desktop dir); the theme; the brand
string key and its value; `UserAgent`; `APP_DIR_NAME`; `rootProject.name`; the Android
`applicationId` and `namespace`; the iOS product name and bundle id; and finally the bare
words `Curare` and `curare` in prose.

Three things it got wrong on the way to being right, each of which will bite anyone who
rewrites it:

- **`\b` does not fire inside snake_case.** `_` is a word character, so
  `\bcurare\b` misses `sign_in_is_not_yet_available_while_curare_is` and
  `curare_will_delete_the_nsec_for_s_from_this`. String keys carry the brand, and need
  look-around rules that treat `_` as a boundary.
- **File names carry identifiers too.** `CurareNavHost.kt` must become `MantraNavHost.kt`,
  not just its contents; a path rule that only moves directories leaves
  `MantraNavHost.kt` and `CurareNavHost.kt` side by side with identical content.
- **"Curated" is two words.** In generation 1 it was the brand; at the tip it is the
  protocol's name — `CuratedSchemaEvent` (158 uses), `CuratedEntryEvent`, the
  `nostr/curated/` package, and a `curated` string that marks a queue row the group has
  taken up. The bare word is therefore *not* a rule. Only the generation-1 *identifiers*
  are mapped, and that is exact because the two commits written in that generation never
  use `Res.string.curated` or the bare word as a brand in code.

What it cannot touch, by construction: every rule matches a brand token and none matches
a Mantra one, so the twelve `Mantra*` entities, the two `mantra/` prefixes and
`ephemeral.mantra.press` pass through untouched. The check is that the count of
`Mantra*` entity tokens does not go down across the rewrite — 433 in Mantra, 435 in the
dry run's tree, two new references and none lost — plus the fact that the rewritten tip
compiles, which no missed identifier would survive.

### The pipeline

```bash
git fetch curated
git branch tmp/curated-src curated/curated
git filter-branch -f -d /tmp/fb --tree-filter 'python3 /abs/path/to/docs/scripts/curated-unbrand.py tree .' \
    -- origin/mantra..tmp/curated-src                                 # 84 s for 39 commits
```

Now `tmp/curated-src` is Curated's history in Mantra's names. Its first commit, the
rebrand, is sixteen files of comments; its features are Mantra commits that never
happened. Replay them, recreating the merges and dropping the two commits Mantra does not
want:

```bash
git branch curated-pull tmp/curated-src          # the branch that gets rewritten; tmp/curated-src stays as the reference
LOGO=$(git log --format=%h origin/mantra..tmp/curated-src --grep='Update logo' | tail -1)
SECTIONS=$(git log --format=%h origin/mantra..tmp/curated-src --grep='drop the library, dialects' | tail -1)
GIT_SEQUENCE_EDITOR="sed -i -e '/^pick ${LOGO:0:7}/d' -e '/^pick ${SECTIONS:0:7}/d'" \
    git rebase -i --rebase-merges --onto origin/mantra origin/mantra curated-pull
```

`--rebase-merges` matters: the two merges that carried hand resolutions are re-performed
rather than flattened, so their resolutions are re-asked at the same points instead of
silently lost.

### What the dry run found

| | drop the logo only | drop the logo and `808a3459` (**recommended**) |
|---|---|---|
| commits replayed | 38 | 37 |
| commits that conflicted | 2 | 3 |
| where | `strings.xml` at the recreated `c8de3a1f`; `CuratedSuggestionListScreen.kt` at the recreated `fedbe724` | the same two, plus `GroupNostrProfileSectionJvmTest.kt` at `55664cc7` (modify/delete) |
| files the original merges edited outside their conflicts | 2 | 2 |
| `git diff tmp/curated-src HEAD` afterwards | exactly the logo's 13 files | the logo, plus the three files `808a3459` touched — the screen, its view model, its UI state |
| `compileDebugKotlinAndroid`, `compileKotlinJvm` | clean | clean |
| `:composeApp:jvmTest` | 1,036 tests, 0 failures | 1,036 tests, 0 failures |
| `:composeApp:testDebugUnitTest` | 530 tests, 0 failures | 530 tests, 0 failures |
| `:composeApp:m3Audit` | all budgets met | all budgets met |

Mantra's own suite is 733 jvm tests (736 with `39fb64b6`); the other three hundred came
with the features.

The three conflicts and their resolutions:

| at | file | why | resolution |
|---|---|---|---|
| `c8de3a1f'` | `strings.xml` | two branches appended strings at the end of the file | keep both blocks |
| `fedbe724'` | `CuratedSuggestionListScreen.kt` | the back-button sweep and the accept flow both edited the sheet | take the original merge's file: `git show <original fedbe724, rewritten>:<path>` |
| `55664cc7'` | `GroupNostrProfileSectionJvmTest.kt` | `808a3459` (dropped) modified it; `55664cc7` deletes it | delete |

And the two files whose changes lived only *inside* the original merge commits — edits a
merge made beyond resolving its conflicts, which a recreated merge that does not conflict
will never reproduce:

```bash
git checkout tmp/curated-src -- \
    composeApp/src/commonMain/kotlin/press/mantra/compose/ui/composable/AcceptCuratedSuggestionScreen.kt \
    composeApp/src/commonMain/kotlin/press/mantra/compose/ui/composable/BroadcastGroupSignedEventScreen.kt
git commit -m 'reconcile: the two files the original merges edited outside their conflicts'
```

Then the check that makes the whole thing honest:

```bash
git diff --stat tmp/curated-src HEAD     # must be exactly: the logo's 13 files + the three files 808a3459 touched
```

Anything else in that diff is a normaliser rule that is wrong or a resolution that is,
and it is found here rather than in production.

## The phases

Each phase ends the same way: `:composeApp:compileDebugKotlinAndroid`,
`:composeApp:jvmTest`, `:composeApp:testDebugUnitTest` and `:composeApp:m3Audit` all
green on the branch, and one review of the diff against the phase's stated contents.
The phases are cuts through the graph above — each one replays every commit reachable
from its cut that the previous phase did not — so the ranges are `--onto <mantra tip>
<previous cut> <this cut>` and no commit is replayed twice.

### Phase 0 — Mantra prepares, natively

Nothing from Curated lands. Three things Mantra does to itself so the pull is clean:

1. **Merge the update branch** (`f4434ab1`, `39fb64b6`): the library pin at `84cc44c`
   that the identity work needs, and the removal of the app's duplicate
   `nostrPublicKey()`.
2. **Retire Torch.** `TorchTheme` → `MantraTheme`; `UserAgent.APP_NAME` and
   `CLIENT_NAME` → `"Mantra"`; the two error strings; `Config.xcconfig` to
   `PRODUCT_NAME=Mantra` and `press.mantra.ios`, with the pbxproj's product references.
   Keep the three Torch *records* the rebrands kept:
   `material-design-conformance.md:278` and `:654`, and `NavigationRoutingTest`'s
   fixture. Then change the normaliser's theme and `UserAgent` rules — the ones marked in
   the script — so `CurareTheme`/`CuratedTheme` map to `MantraTheme` and the agent to
   `"Mantra"`.
3. **Take the protective comments** — or leave this for Phase 2, where the rewritten
   rebrand commits carry them. Either way the commit is squashed and re-messaged, the
   sentences under [Risks](#risks-and-the-silent-ones-in-particular) are rewritten by
   hand, and the rewritten `d26cf6c7` is dropped in Phase 2 because item 2 has done its
   work.

Exit: 736 jvm tests, audit green, iOS config no longer says Torch.

### Phase 1 — The dry run

The pipeline above, end to end, on a scratch worktree, landing nothing. Its output is the
table in the previous section, recomputed against the day's `curated/curated`. If a
number moved, find out why before Phase 2: a new conflict means a new upstream commit
touched a seam; a non-empty exactness diff means a new brand token the normaliser does
not know. Delete the worktree and the `tmp/` branches when done; the script recreates
them in under two minutes.

**Run on 2026-09-13, after Phase 0, and it changed the plan in three places.** The
record, so the next pull starts from what happened rather than from what was planned:

*The upstream moved first.* `curated/curated` was `86cb876b` when the plan was written
and `29027f2b` when Phase 1 ran: ten more commits, a "multiple profiles on a device"
line, one of which (`759199f2`) adds schema version 20 — the fork's first migration.
They are outside this plan's scope and are the next pull; the schema change is why
they get a decision of their own rather than a ride on this one.

*Landing is by cherry-pick in topological order, not by `rebase --rebase-merges`.* The
phased cuts cannot be expressed as rebases: a recreated merge whose other parent was
replayed in an earlier phase would be pointed at the *pre*-replay commit, dragging the
unrebased lineage in beside the rebased one. So the ordinary commits are picked one by
one, the four merge commits land as nothing, and their content — the resolutions — is
folded in where the conflicts actually surface: at each branch join the join files
(`strings.xml`, `MantraNavHost.kt`, `CuratedSuggestionListScreen.kt`) are taken exactly
as upstream's own merge left them, and the two files the merges edited outside their
conflicts are folded into the back-button commit. Every landed commit carries a
`Pulled-From: curated/curated@<sha>` trailer, stamped by the rewrite, and a paragraph
naming any resolution made on the way in. The driver is
[`docs/scripts/curated-replay.py`](./scripts/curated-replay.py).

*The library pin goes backwards for Phases 3 and 4, on purpose.* The nsec line was
written against lightning-kmp-app `59c11ed`, whose `NostrKeyManager` it writes through;
`84cc44c` — Mantra's pin — renamed that class to a read-only `LegacyNostrKeysFile`, and
the compiler says so at the Phase 3 cut (`Unresolved reference 'NostrKeyManager'` in
`IdentityWriter.kt`, eight errors). So `366b0177` moves the pin back to `59c11ed`, whose
nested chain is identical, and `5efeae76` brings it forward again — to `84cc44c` rather
than the `01962f3` it named upstream, which is an unfetchable branch commit whose tree
`84cc44c` reproduces exactly. Every commit on the branch compiles against the pin it
records, which is the property upstream's history had and a fixed pin would have lost.

*Three ways a "keep both sides" resolution is wrong,* each caught by the exactness
check and each now handled: it duplicates lines both sides already hold when a hunk
widens; it never applies the other side's deletions (the copy-suggestion strings
`fcc19f95` removes survived one attempt); and, subtlest, a join resolved in a different
*order* from upstream's merge leaves later commits unable to find the block they edit,
so their deletions silently miss. Hence: conflicts are picked with `diff3` markers and
resolved as a line-set three-way merge for files whose lines are unique by
construction, and never at a join, where upstream's merge is the answer.

The numbers, on the trial worktree: 30 commits replayed (4, 8, 6, 12), 0 stuck, 9
carrying a resolution note; residual diff against the rewritten `86cb876b` exactly
the known set (Phase 0's prose, `39fb64b6`'s files, `808a3459`'s three, this document
and its scripts); `compileDebugKotlinAndroid` and `compileKotlinJvm` clean;
`jvmTest` 1,039 tests, `testDebugUnitTest` 530, both 0 failures; `m3Audit` all budgets
met.

### Phase 2 — The group's identity, and the lists' read side

Cut at `392b90b8'`: `4c0ed0c1`, `334e6dd1`, `930d37c8`, `392b90b8`, with `808a3459`
dropped. The three rewritten brand commits are ancestors of this cut too: if Phase 0 took
the protective comments and retired Torch natively, **drop all three** — their whole
content is already on Mantra and replaying them re-applies it; if Phase 0 skipped item 3,
squash `3bc8be53'` and `e6aee792'` into the one re-messaged comments commit and drop
`d26cf6c7'`. Four feature commits either way, no conflicts in the dry run.

What to look at in review: the `applyInnerEvent` arms (the kind 1 arm must fall through
to `unsupported`, not return null); that `GROUP_IDENTITY_TYPES` renders as a
`RitualNotice` and not a bubble; that `CuratedSchemaEventTest` still parses the NIP's
example verbatim under Mantra's package.

Exit: group screen shows profile, relays, posts and schemas as sections above Library;
tests green.

### Phase 3 — Sign-in with a recovery phrase or an nsec

Cut at `2326839e'`: the eight commits of line G, including the plan and its record. The
`Identity` refactor touches eighteen files' signatures; the dry run applied it without
conflict, but on top of Phase 0's `39fb64b6` expect two trivial ones — `42f3a697` removes
the `press.mantra.compose.extensions.nostrPublicKey` import from `RelaysSocketManager`
and `NavigationViewModel`, and `39fb64b6` had already replaced that line with the
library's import. Resolution: delete the line; the call it served is gone too.

What to look at: `SignInToProfileViewModel.commit` writes the secret before it does
anything else; `SignInSync`'s bootstrap set is the indexer relays plus
`ephemeral.mantra.press`, not `ephemeral` alone; `NsecRestoreRoundTripJvmTest` asserts no
node ran.

Exit: a pasted nsec signs in on desktop and Android; `NostrSecretScreen` reveals and
forgets it.

### Phase 4 — The queue, broadcast, accepting, the back button, the rows

Cut at `55664cc7'`: `1e52fc8f`, the recreated `c8de3a1f`, `6ae5a967`, `fcc19f95`,
`732a4527`, `0634487e`, the recreated `ccbdbf7a` and `fedbe724`, `55664cc7`. All three
conflicts and both reconcile files live here; the table above is the runbook.

What to look at: the group screen's order (signing key, five rows, Propose event,
Library, Dialects, Projects, Subgroups); every screen reached by `navigate(...)` has a
back button on desktop; `BroadcastGroupSignedEventScreen` sends through
`EventPublishTransport` and not the broadcast queue; `defaultRelaysFor` an entry is its
schema's relays.

Exit: the exactness diff is the logo plus the three files `808a3459` touched; audit
budgets met, as in the dry run.

### Phase 5 — Read-only identities

Cut at `86cb876b'`: the ten commits of line I, `3116eb8a`, and the final merge. No
conflicts in the dry run. `00c36ec5`'s pin change is a no-op against Mantra's pin and
can be dropped or left; dropped is cleaner.

What to look at: `Identity.toKeyPair` is the only place a quartz `KeyPair` is built from
an identity; `ProvideSigningCapability` sits once above the navigation suite; the
migration from `nostr-keys.dat` runs before the first listing and leaves an unreadable
old file in place with an error rather than silently losing identities.

Exit: an npub signs in read-only, every signing action is hidden with a reason beside
it, sign out removes it and re-lists.

### Phase 6 — Close the loop

Two things flow the other way, and one decision closes the reason this document exists.

**Reverse-pull `39fb64b6`.** Curated still carries the app-side `WalletManagerExtension.kt`
and one caller (`CreateProfileViewModel`), while its own identity code already uses the
library's `nostrPublicKeyHex()`. The same normaliser runs backwards with its rules
inverted, or — for one small commit — it is a hand cherry-pick.

**Reverse-pull Phase 0's Torch retirement**, so both trees agree that `CurareTheme` and
`MantraTheme` are the same thing under two names. That, too, is one commit.

**Then decide what the fork is.** Every future Curated commit will be written in
`to.curare.*`, and every pull will need this pipeline again. With the tooling in place
the machine work is minutes and the conflicts are the three above; the cost is the dry
run, the review and the prose — call it half a day per pull, an estimate. That is
affordable once and corrosive weekly. The options, in the order they should be taken:

1. *Converge the package name.* A Kotlin package is not a brand — the rebrands' own
   messages say the app's domain is still `Mantra*` everywhere that matters — and if
   Curated moved its source back to `press.mantra.*` while keeping `to.curare` as its
   `applicationId`, `Curare` as its `rootProject.name` and `CurareTheme` as its theme,
   the diff between the two trees would shrink to a handful of files and every pull in
   either direction would be a plain cherry-pick. It costs Curated one more rename, its
   fourth, and this time the last.
2. *Invert the relationship.* With the packages converged, Mantra is the natural
   upstream: Curated becomes Mantra plus a short brand-and-product patch series (the
   applicationId, the theme colours, the icon, the section removal, `APP_DIR_NAME`)
   rebased on top of Mantra's main. Features are written once, on Mantra, and Curated
   takes them by rebasing. This is what the current fork already *is* in content; it is
   only the history that says otherwise.
3. *Keep pulling.* If neither is wanted, keep `curated-unbrand.py` maintained beside
   the code it maps, redo Phase 1 before every pull, and treat a non-empty exactness diff
   as a bug in the script.

## Risks, and the silent ones in particular

**The five names.** `Mantra*` entities, the two `mantra/` prefixes, the relay host, and
— on Mantra's side — nothing more. The normaliser cannot touch them; a hand edit can. The
check is `git grep -c 'mantra/' composeApp/src` and the entity token count before and
after, and `:composeApp:jvmTest`, which is what catches a renamed table.

**Prose that lies after a rename.** A blanket rewrite turns "survived two rebrands —
Mantra to Curated, Curated to Curare" into a sentence about rebrands Mantra never had.
Eight places, ten lines, all in the rewritten brand commits or the docs, all needing a
human sentence:
`Relays.kt:9`, `SharedKeyDerivation.kt:65`, `ChillDkgRitualManager.kt:95`,
`PlatformContext.jvm.kt:48–56` (a whole block about `APP_DIR_NAME` keeping the old
brand, which is false for Mantra and should go), `HomeScreen.kt:167`,
`docs/shared-key-derivation.md:68–69`, `docs/material-design-conformance.md:656–657`,
and the `strings.xml` header comment. Rewrite them in Phase 0 or Phase 2's squash, not
later.

**`strings.xml` grows from 377 to 670 strings** and conflicts by appending at every
branch join. Keep both blocks. Do not rename the `curated` string: it is the queue's
"Curated" mark, product vocabulary, and the normaliser leaves it alone on purpose.

**Merges that did more than resolve.** Two files; the reconcile step; the exactness diff
catches any third.

**Test anchoring.** `GroupSignedWorkRowsJvmTest` asserts Subgroups is below the identity
block. It is, with three sections between. `GroupNostrProfileSectionJvmTest` is deleted
upstream and stays deleted.

**iOS is unverified on every side of this.** The rebrands say so, the dry run ran on
linux, and Phase 0's `Config.xcconfig` change is the first time this repo will have named
itself there. Build it on a Mac before believing it.

**Data on disk moves nowhere.** Desktop `APP_DIR_NAME` stays `mantra`; the new
`nostr-credentials.dat` sits beside `seed.dat` in the node-data directory; the migration
from `nostr-keys.dat` is idempotent and refuses to delete what it could not read. An
existing Mantra install sees new files and no moved ones.

**The upstream moves.** Between this repository's previous fetch of `curated/curated`
(`cd3108e9`, 2026-09-10) and the one this plan was written against (`86cb876b`,
2026-09-12) it gained thirty-three commits. Redo Phase 1 the day you land; the numbers
drift, the method does not.

## Appendix: the dry run, for the record

Run on 2026-09-13 against `curated/curated` at `86cb876b`, on a scratch worktree of
`origin/mantra` at `ba26c0b1`, with `lightning-kmp-app` at `84cc44c` symlinked in from a
populated checkout.

| measurement | value |
|---|---|
| normaliser at the tip | 812 files rewritten, 806 paths moved |
| rewritten `3bc8be53` (rebrand 1) | 16 files, +61 −38 |
| rewritten `d26cf6c7` (Torch retirement) | 5 files, +16 −10 |
| rewritten `e6aee792` (rebrand 2) | 21 files, +85 −63 |
| rebrand 2 after normalising both sides | only sentences that describe the rebrand itself |
| `filter-branch` over 39 commits | 84 s |
| replay, logo dropped | 38 commits, 2 conflicts, 2 reconcile files, exactness = logo |
| replay, logo and `808a3459` dropped | 37 commits, 3 conflicts, 2 reconcile files, exactness = logo + the 3 files `808a3459` touched |
| `compileDebugKotlinAndroid` + `compileKotlinJvm` | clean, both variants |
| `jvmTest` | 1,036 / 0 failures, both variants (Mantra alone: 733) |
| `testDebugUnitTest` | 530 / 0 failures |
| `m3Audit` | all budgets met; 12 adaptive uses, 2 navigation components |
| Room schemas found at `press.mantra.compose.database.MantraDatabase/` | 19 |
| `Mantra*` entity tokens (`MantraArtifact|Chapter|Chunk|Dialect|Translation*|Dao|Repository`), before and after | 433 → 435; none lost |
