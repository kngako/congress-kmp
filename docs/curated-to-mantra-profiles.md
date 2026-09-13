# Pulling Curare's several-profiles line back into Mantra

The [first pull](./curated-to-mantra.md) landed on 2026-09-13 and the upstream moved
the same day: `curated/curated` went from `86cb876b` to `29027f2b`, ten commits that
let a device hold several profiles and move between them — and, with them, the fork's
first schema migration, version 20. That plan named them the next pull and said the
schema change earned them a decision of their own rather than a ride. This is that
decision, which of the ten Mantra wants, in what order, and how they land — with the
dry run already done, so the numbers below are measured rather than predicted.

Read [multiple-profiles.md](./multiple-profiles.md) for the line's own reasoning once
Phase 2 brings it; nothing here repeats it. Read the first pull's
[three decisions](./curated-to-mantra.md#three-decisions) and its
[Phase 1 record](./curated-to-mantra.md#phase-1--the-dry-run) for the method and the
lessons; this document assumes both and adds what changed. The one rule everything
still rests on is the derivation note's: two strings in this tree are hash inputs and
are never renamed, and the normaliser cannot touch them by construction.

Written against `mantra` at `776455ec` — `origin/mantra` at `0fa807a6` plus the
removal commit, which is where this plan starts and which `origin` does not yet carry
— and `curated/curated` at `29027f2b`. Dry run executed 2026-09-13.

## The shape of the fork now

Since the first pull, Mantra's tree has stopped being a superset of Curare's non-brand
tree. It took thirty of the fork's thirty-nine commits, then took three lines out again
— the group's nostr identity, the curated lists, and the row-per-kind group screen — by a
product decision recorded in the first plan. So the two trees now differ in 135 files:
83 that Curare has and Mantra does not (the twelve removed screens and their view
models, `nostr/curated/`, the seven group readings, and `WalletManagerExtension.kt`,
which `39fb64b6` deleted on Mantra's side), 4 that Mantra has and Curare does not (the
first plan, its two scripts, and `NostrPublicKeyJvmTest`), and 48 that both have with
different content — `strings.xml`, `MantraNavHost.kt`, `ChatRoomDetailScreen.kt` and
the rest of the removal's footprint. That residual is the seam every future pull has to
cross, and the first plan predicted that anything upstream touching the identity block
would conflict in it.

The ten commits, all of 2026-09-13, all on one line with no merges:

```
* 29027f2b  docs: record what the multiple-profiles plan built           (record)
* 88577bb5  test(identity): several profiles, both ways, and a boot      (Phase 8)
* 9bb34004  feat(ui): leaving one profile among several                  (Phase 7)
* 759199f2  feat(sync): a fetch queue per identity, the inbox reopened   (Phase 6, schema v20)
* 2f420230  feat(identity): adding a profile from inside                 (Phase 5)
* 25464595  feat(ui): the switcher                                       (Phase 4)
* 00fc9969  feat(identity): which profile opens                          (Phase 3)
* 9b3a1927  feat(identity): a switch that tears down what it should      (Phase 2)
* 3008137e  feat(identity): a seed's key is a credential                 (Phase 1)
* a5ff2641  docs: plan several profiles on a device                      (plan)
```

Three facts about them decide most of what follows.

**They touch none of what Mantra removed.** The ten change 69 files. Of those, six
already differ between the trees, and in only four of the six is the difference the
removal's — `strings.xml`, `MantraNavHost.kt`, `DatabaseNostrRepository.kt`,
`NostrRepository.kt` — and in all four the removal's hunks and this line's hunks are far
apart. The seam the first plan predicted is real and this line does not cross it: nine of
the ten replay without a conflict, and the tenth conflicts on a line that is Mantra's own
([the third decision](#3-the-one-conflict-is-mantras-and-it-closes-a-loop)).

**No library change.** The pin is `84cc44c` on both sides, unchanged by the ten;
`BusinessManager.stopBusiness`, `GlobalPrefs.saveDefaultWallet` and the credentials
file's format all existed already. This is the first pull from the fork in which the pin
does not have to follow any commit.

**One schema migration**, `759199f2`: version 20, two nullable columns by
`AutoMigration`, the export committed beside its predecessors. Mantra has no version 20
of its own on any branch — measured: `curated/curated` is the only ref in this
repository carrying a `20.json` — so the number is free, and after the pull Room
regenerates nothing: the built tree's `20.json` is byte-identical to the fork's. The
[second decision](#2-schema-version-20-and-who-allocates-the-number) is about the
number, not the columns.

## The inventory

Every commit, what it is, what it does to Mantra *as it is today*, and the verdict.
Sizes are `git show --shortstat`; `759199f2`'s is 5,698 lines of generated schema plus
395 of code.

| commit | phase | size | what Mantra gets | verdict |
|---|---|---|---|---|
| `a5ff2641` | plan | 2 files, +1,192 | `docs/multiple-profiles.md` and its README row | **pull** |
| `3008137e` | 1 | 16 files, +649 −111 | a seed's nostr key written to `nostr-credentials.dat` as a `Secret`, repaired in for every seed already on the device; `StoredIdentity.merge` inverted so the credentials file is the list of profiles; the identity's key read from the credential rather than from the running node; `forgetNostrCredential` refusing a key a seed derives | **pull** — the on-disk change; see [Phase 2](#phase-2--the-credential-alone) |
| `9b3a1927` | 2 | 9 files, +490 −31 | `switchToWallet` becomes `switchToIdentity` and stops the node it leaves; `RelaysSocketManager.observeActiveUserId` restructured under `collectLatest` so a switch cancels the previous identity's relay observer; `stopPlatformBusiness` as three one-line actuals | **pull** — a fix Mantra has today, see below |
| `00fc9969` | 3 | 7 files, +233 −22 | `StartupChoice.resolve`, the startup precedence lifted out of the screen and reordered; `setActiveIdentity` saves the default, the forget tails clear it; the startup screen's six literals into the catalogue as *profile* words | **pull** |
| `25464595` | 4 | 13 files, +547 −24 | `ProfilesRoute`/`ProfilesScreen`, reached from the profile tab's row that today goes to `ImplementationPendingRoute`; `WalletsSelector` rows showing the nostr profile instead of "Default name" over a random emoji, and "Wallet" beside a seed | **pull** |
| `2f420230` | 5 | 24 files, +624 −201 | *sign in with a key* and *create a new profile* under both lists; a profile created from inside is a bare key through `NewProfileWriter`, a seed only from Landing; *end this* and its `wipeDatabase` gone from the create screen; a pasted key already on the device offers *switch to it*; `JvmGlobalPrefs` closing a DataStore race on desktop | **pull** — two fixes Mantra has today, see below |
| `759199f2` | 6 | 17 files, +6,093 −23 | `ownerPublicKey` on the two fetch queues, stamped by the repository, drained by the owner's pump; `NostrDao.reopenInbox` opening every wrap addressed to the active key that no seal names, once per activation; `GiftWrapSeal.giftWrapMessageId` written on the path that never wrote it; **schema v20** | **pull** — a fix Mantra has today, and the decision |
| `9bb34004` | 7 | 3 files, +61 −1 | *switch profile* on the not-found screen when another profile exists | **pull** |
| `88577bb5` | 8 | 1 file, +282 | `MultipleProfilesRoundTripJvmTest`: two profiles, both ways, and a cold boot | **pull** |
| `29027f2b` | record | 2 files, +46 −7 | the built-record table in the plan; the README's reading order | **pull** |

Nothing is dropped. The line is one feature and seven fixes braided together — four of
them live on Mantra today — and the
[first decision](#1-take-the-whole-line-and-what-taking-less-would-cost) is why the
braid is not unpicked.

### What is a fix for Mantra today, before any second profile exists

Four of the ten close bugs a Mantra install has now, with one profile, and each is
verifiable against the current tree:

- **The relay observer leaks across sign-outs** (`9b3a1927`).
  [RelaysSocketManager.kt:65](../composeApp/src/commonMain/kotlin/press/mantra/compose/network/relays/RelaysSocketManager.kt)
  still reads `// TODO: Cancel all pending jobs?` above a map of one relay-list job per
  pubkey, of which only the job for the pubkey being *started* is ever cancelled. Sign out
  of a read-only identity, sign in as another, and two observers feed `updateRelayPools`
  with the one pool following whichever list emitted last. The sign-out exits that make
  this reachable arrived in the first pull.
- **A read-only identity's inbox stays closed after its nsec is pasted** (`759199f2`).
  [NostrDao.kt:451](../composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrDao.kt)
  says of a wrap it cannot open that "the key that opens this one may be signed in later",
  and nothing runs when it is: `storeNostrEvent` never re-indexes an id it holds, so the
  wraps an npub preview fetched are never opened by the upgrade the first pull built. And
  `GiftWrapSeal.giftWrapMessageId` is written on the NIP-17 DAO's path and not on the one
  `indexNostrEvent` takes, so nothing in the database can say which wraps were opened —
  the question `reopenInbox` asks.
- **Desktop can throw "multiple DataStores active for the same file"** (`2f420230`).
  `Phoenix.jvm.kt` builds a `DataStoreManager` per call, and the library caches one
  `GlobalPrefs` per process behind an unsynchronised check-then-set; the listing on IO and
  a sign-in's write racing at first use both build one, and the loser throws. Upstream's
  tests hit it one run in three. `JvmGlobalPrefs` is the one way the jvm target reaches
  the prefs, under a lock; Android goes through the `Application`'s single instance and
  never raced.
- **The startup screen's words** (`00fc9969`). `SovereignWalletStartupScreen` carries six
  string literals — "Initializing...", "Preparing wallet...", "Opening wallet", "Starting
  wallet" — outside the catalogue and in the wallet's vocabulary; the M3 audit counts them
  among the 40 literals it reports without a budget, and after the pull it reports 30.
  "Starting wallet" survives, in the one branch where a wallet is what is starting.

The other three fixes are latent until a device holds two profiles — the precedence
`when` that puts "show me the list" above "open this one", the default that is read at
every boot and saved by nothing, *end this* wiping every profile's rooms from the screen
that adds one — and the line makes two profiles possible, so they ship with it.

### What is a feature, and whether Mantra wants it

The profile tab has had a *Change account* row since the first pull, and it routes to
`ImplementationPendingRoute`
([ActiveProfileScreen.kt:324](../composeApp/src/commonMain/kotlin/press/mantra/compose/ui/composable/ActiveProfileScreen.kt)).
The switch behind it — `switchToWallet`, `resetToSelector`, the null identity sent to
startup with `popUpTo(0)` — has been in the tree since Phoenix and is reachable from
nowhere; Landing shows only when the device holds nothing, so a second profile cannot be
added. Mantra took the nsec and npub sign-in lines without qualification; this is the
line that answers the question both of them deferred, and it is wanted for the same
reason they were: a translator who reviews as one key and signs a chapter as another, or
who holds a collective's read-only preview beside their own profile, is the ordinary
case, and the row that promises it is already on the screen.

The one feature choice inside the line that is a Mantra question rather than a Curare
one is that **a profile created from inside is a key, not a second wallet**. Mantra's
wallet is the Phoenix fork's; nothing in the translation product asks for a second
Lightning node, and a user who wants another profile has not asked for a second phrase
with funds behind it. The seed stays the first profile's, from Landing. That is the
right answer here too.

## Four decisions

### 1. Take the whole line, and what taking less would cost

The alternative is to take the four fixes and leave the feature: `9b3a1927`'s observer,
`759199f2`'s inbox, `2f420230`'s `JvmGlobalPrefs`, `00fc9969`'s words. Measured
against the commits, that is not four cherry-picks; it is editing five commits by hand.
`9b3a1927` renames `switchToWallet` in the same hunk that stops the node; `759199f2`'s
owner column exists so that a *switch* cannot lose a message, and its `reopenInbox`
runs from the same activation collector; `2f420230` fixes the DataStore race in the
jvm actuals it rewrites for the bare-key writer; `00fc9969`'s strings are the
selector's. Every carve-out leaves a Mantra `SovereignWalletViewModel`, nav host and
startup screen that differ from upstream's in the same places, and the first plan's
principle stands: every feature left out is a permanent conflict seam, paid for at every
later pull. The removal of lines B, D and H was that principle's one exception, made on
product grounds and measured at 135 files of residual; this line touches none of it,
and adding a second exception for a feature the product wants would be paying the price
twice for nothing.

So: all ten, in upstream's order, with the phases cut where upstream's own rollout
section says the line can ship in pieces.

### 2. Schema version 20, and who allocates the number

Two apps, two `applicationId`s, two databases that never meet on one device: nothing
stops Mantra from having a version 20 of its own shape and Curare another. What it would
cost is the method. The first plan's pull was mechanical because the nineteen schema
files were byte-identical on both sides; a Mantra 20 that is not Curare's 20 makes every
later migration a hand-merge of `MantraDatabase.kt`'s `autoMigrations` list and a renumbering
of every schema file after it, in both directions, forever.

So the rule, stated once: **a schema version is allocated by whichever tree migrates
first, and the other tree pulls that migration before it adds its own.** Never two
different 20s. Today that is trivially satisfiable — Mantra has none pending; the check
that it stays satisfiable is a `git ls-tree` over every ref for the next number before
a migration is written on either side.

Taking upstream's 20 verbatim is safe on the evidence: the columns are nullable
additions to two request-queue tables, `AutoMigration` with no data rewrite, and the
built tree regenerates nothing (`git status` clean under `composeApp/schemas/` after a
full compile; identity hash `4414c373…`). The downgrade story is the one every
migration in this database has had — an older build cannot open the file — and
`PlatformDatabaseBuilder` has the same answer it always had. The two-column change
carries no risk of its own; what it carries is the number, and the rule above is what
makes the number cheap.

### 3. The one conflict is Mantra's, and it closes a loop

The dry run's single conflict is `CreateProfileViewModel.kt` at `2f420230`, and it is
not the removal's seam. Mantra's `39fb64b6` moved the `nostrPublicKey()` import from the
app's `WalletManagerExtension` to the library's; upstream never took that change and
still imported the app's — one line of residual, the last of the first plan's Phase 6
reverse-pulls that was never done. `2f420230` rewrites the import block wholesale, and
removes the seed generation the import served: the new view model asks a
`NewProfileWriter` for a key and derives nothing itself.

Resolution: take the file as upstream leaves it. Not a union — the previous run's lesson
about joins holds for import blocks too — but *theirs*, because the base difference is
one import this commit deletes the use of. Afterwards the file is identical on both
sides, and the residual between the trees goes from 135 files to 134. The
[replay driver](./scripts/curated-replay.py) carries the rule as a `THEIRS` entry with
that reason, beside the one `42f3a697` needed for the same import in two other files.

What is left of `39fb64b6` upstream after this: `WalletManagerExtension.kt` itself,
now with zero callers on either side. The reverse-pull the first plan owed shrinks to
deleting a dead file; see [Phase 6](#phase-6--close-the-loop).

### 4. Two of the device's own profiles in one group

Upstream's out-of-scope list names it and Mantra should name it louder, because groups
are Mantra's product. `ChatRoom`'s primary key is the group id and `userPublicKey` says
whose the row is; `DkgSession`, `GroupKeyState` and every FROST share hang off the room.
Today one device holds one profile and the collision cannot happen. After this line a
user can invite their second profile into their own collective, and the second member's
MLS state overwrites the first's — silently, in the shape of "the other device never got
it". The library, dialects and projects are keyed by `chatRoomId` and reached through
the room, so they scope per profile for free; the room itself does not.

Decision: **not part of the pull, and a Mantra-native follow-up before the line ships
to users.** The cheap fix upstream names — refuse a Marmot welcome for a room the device
already holds under another profile, with a notice saying which — is one branch in the
inbound manager and a string. The real fix, keying the room by `(id, userPublicKey)`,
is a migration of every table that references a room, and it is not this plan's. Write
the cheap one natively, under Mantra's names, and offer it upstream by cherry-pick; it
is the kind of commit the fork should take.

## How it lands

The first plan's pipeline, unchanged in method, with three things that differ.

**Rewrite from the fork point, not from the last pull.** The rewritten history is
rebuilt from `ba26c0b1` every time — 49 commits now, 103 seconds — because a
`filter-branch` over only `86cb876b..curated/curated` would leave the first new commit's
parent in Curare's names, and the cherry-pick of that commit would be a seven-hundred-file
rename. The normaliser has no new rules to learn: the rewritten tip holds no `curare`,
`Curare` or `com.it.curated` token outside the first plan's own prose, checked by
`git grep`.

```bash
git fetch curated
git worktree add -b tmp/pull2 <scratch> mantra              # from 776455ec, the removal included
cd <scratch>
git branch -f tmp/curated-src curated/curated
FILTER_BRANCH_SQUELCH_WARNING=1 git filter-branch -f -d <tmpdir> \
    --tree-filter 'python3 /abs/path/docs/scripts/curated-unbrand.py tree .' \
    --msg-filter 'cat; echo "Pulled-From: curated/curated@$GIT_COMMIT"' \
    -- ba26c0b1..tmp/curated-src
python3 docs/scripts/curated-replay.py pick <scratch> \
    a5ff2641 3008137e 9b3a1927 00fc9969 25464595 2f420230 759199f2 9bb34004 88577bb5 29027f2b
```

A clean worktree is required — `filter-branch` refuses unstaged changes, and a
populated `lightning-kmp-app` at a pin other than `84cc44c` counts. A fresh worktree
has no submodule and is clean; for the build afterwards, symlink a populated checkout
at `84cc44c` in (`git status` then shows ` T lightning-kmp-app`, which the ten never
touch) and pass `ANDROID_HOME` on the command line, as the first plan's appendix did.

**The exactness check is restated**, because `git diff tmp/curated-src HEAD` is no
longer "the commits you dropped" — it is 135 files of the removal. The property that
holds instead: **the pull moves Mantra by exactly the ten commits, so the residual
between the trees is the same before and after**, file for file and hunk for hunk,
except where a resolution deliberately changed it.

```bash
U0=<rewritten 86cb876b>; U1=<rewritten 29027f2b>; M0=776455ec; M1=HEAD
diff <(git diff --name-only $U0 $M0 | sort) <(git diff --name-only $U1 $M1 | sort)
#   expected: exactly CreateProfileViewModel.kt, gone from the residual
for f in $(comm -12 <(git diff --name-only $U0 $M0 | sort) <(git diff --name-only $U1 $M1 | sort)); do
  cmp -s <(git diff $U0 $M0 -- $f | grep '^[-+]' | grep -v '^[-+][-+]') \
         <(git diff $U1 $M1 -- $f | grep '^[-+]' | grep -v '^[-+][-+]') || echo "residual moved: $f"
done
#   expected: nothing
```

Anything else in either list is a normaliser rule that is wrong, a resolution that is,
or an upstream commit that crossed the seam — and it is found here.

**The rules the driver needs**: one. `2f420230` × `CreateProfileViewModel.kt` → theirs,
for the reason in the third decision. The `UNION` rule for `strings.xml`,
`MantraNavHost.kt` and `docs/README.md` stays in place and did not fire — git merged all
three cleanly at every one of the ten — but a future upstream commit that appends
strings where the removal deleted them will need it, and the first plan's lesson about
deletions is already encoded in it.

Then the four checks, per phase, as before: `:composeApp:compileDebugKotlinAndroid`,
`:composeApp:compileKotlinJvm`, `:composeApp:jvmTest`, `:composeApp:testDebugUnitTest`,
`:composeApp:m3Audit`, and — new for this pull — `git status` under `composeApp/schemas/`
after the compile, which is the check that Room agrees with the committed `20.json`.

## The phases

Each phase ends the same way: both compilers, both test suites and the audit green on
the branch, the exactness check as above against the phase's cut, and one review of the
diff against the phase's stated contents. Upstream's rollout section says which pieces
can ship apart — Phase 1 alone, 2 with 3, 4 with 5, 6 whenever, 7 after — and the cuts
below follow it, so that each phase is also a shippable state.

### Phase 0 — the base

Nothing from Curare lands. Two things to settle first:

1. **The removal is on `mantra` and not on `origin/mantra`.** `776455ec` is the local
   `mantra` tip and `origin` is at `0fa807a6`. Every measurement in this document is
   against `776455ec`; a pull onto `0fa807a6` would land the ten on a tree that still
   holds lines B, D and H, and would then have to be removed from under them. Push the
   removal, or merge it, before anything else.
2. **A worktree with the library at `84cc44c`.** The pin does not move in this line, so
   there is no `PIN` entry to add; but a worktree whose submodule sits at another commit
   (this session's was at `01489b8`, an older master) makes `filter-branch` refuse and
   the compile fail on `NostrCredentialManager`. `git submodule update --init` fixes it
   online; offline, fetch the commit from a populated sibling checkout —
   `git -C lightning-kmp-app fetch <checkout>/lightning-kmp-app 84cc44c && git -C lightning-kmp-app checkout --detach 84cc44c`
   — since worktrees keep separate submodule git directories.

Exit: `git status` clean in the scratch worktree; `origin/mantra` at `776455ec` or later.

**Done 2026-09-13**, on the branch that carries this plan (`5f2414dc`, itself on
`776455ec`): the worktree's library was at `01489b8` and its nested chain already at the
pinned four, so one fetch from the main checkout's submodule and a detached checkout of
`84cc44c` made the tree clean. The rewrite was rebuilt from `ba26c0b1` in 104 s and
produced the same forty-nine rewritten commits as the dry run, hash for hash — the
normaliser and `filter-branch` are deterministic given the same input, which makes the
dry run's numbers the run's numbers. Item 1 is still the user's: `origin/mantra` was at
`0fa807a6` when this landed, and the removal goes up with the pull.

### Phase 1 — the dry run

**Run on 2026-09-13, against `curated/curated` at `29027f2b`, on a scratch worktree of
`776455ec`.** The record, so the next reader starts from what happened:

| measurement | value |
|---|---|
| `filter-branch` over `ba26c0b1..curated/curated` | 49 commits, 103 s; 834 files rewritten, 828 paths moved at the tip; 0 brand tokens left in code |
| files the ten touch | 69 (+10,184 −387; without `20.json`, 68 files +4,486 −387) |
| of those, already differing between the trees | 6: `strings.xml` (−234), `MantraNavHost.kt` (−264), `DatabaseNostrRepository.kt` (−6), `NostrRepository.kt` (−14), `CreateProfileViewModel.kt` (±1), `docs/README.md` (+7) |
| replay | 10 picked: 9 clean, 1 resolved by rule, 0 stuck |
| the resolution | `2f420230` × `CreateProfileViewModel.kt`: theirs |
| exactness | residual 135 → 134 files; the one gone is `CreateProfileViewModel.kt`; no other file's residual moved |
| `compileDebugKotlinAndroid` + `compileKotlinJvm` | clean |
| `:composeApp:jvmTest` | 868 tests, 0 failures (Mantra before: 826) |
| `:composeApp:testDebugUnitTest` | 420 tests, 0 failures (before: 413; the seven are `StartupChoiceTest`) |
| `:composeApp:m3Audit` | all budgets met; 12 adaptive uses, 2 navigation components; composable literals 40 → 30 |
| Room after the compile | `git status` clean under `composeApp/schemas/`; `20.json` byte-identical to the fork's |
| `strings.xml` | 444 → 455 strings; 14 added, 3 removed (`change_account`, `end_this`, `select_a_wallet`) |
| library pin | `84cc44c` before and after; the ten do not touch the gitlink or `.gitmodules` |

If the upstream moves before Phases 2–5 land, redo this on the day: a number that
drifts is an upstream commit that crossed the seam, and the driver stops on it rather
than guessing.

### Phase 2 — the credential, alone

`a5ff2641`, `3008137e`. The plan and its first phase, on their own, because this is the
one commit in the line that **changes what is on disk**: the first launch after it writes
a `Secret` credential into `nostr-credentials.dat` for every seed the device holds, and a
build from before it, reached by a downgrade after that write, lists each seed-backed
profile twice — once as a wallet with a derived key, once as a bare key under a different
id. Nothing is lost, both rows sign as the same key, and the next upgrade lists them as
one; but it is the one line of this pull worth a release note, and shipping it first
means that a rollback of any later phase rolls back to a tree that already knows the new
file.

What to look at in review: `writeMnemonic` writes the credential *before* the seed, so a
crash between the two leaves a profile the phrase completes rather than a seed with no
listing; `SeedCredentials.reconcile` runs after both files are read and hands the listing
what it returns, so a repair that could not write still lists the seed by derivation — a
failed write must never hide a wallet; `setActiveWallet` reads the key from the
credential and `check()`s it against the node's, since a node disagreeing with the
credentials file is the one corruption worth refusing to run under;
`forgetNostrCredential` answers `WalletAttached` for a key a seed derives, because the
next repair would write it back.

Exit: a device with a seed lists once, from the credential, with the node's key as a
cross-check; `SeedCredentialsJvmTest` and the rewritten halves of `IdentityWriterJvmTest`
and `StoredIdentityJvmTest` green; release note drafted.

**Built 2026-09-13**, `44324c90` and `d957ee8d`, both clean picks. Exactness: the
residual is 136 files before and after (the dry run's 135 plus this document), nothing
moved. Both compilers clean; `jvmTest` 826 → 837 (the eleven are `SeedCredentialsJvmTest`
and the rewritten writer and listing tests); `testDebugUnitTest` 413; audit all budgets
met; nothing under `composeApp/schemas/` changed, as expected of a phase with no
migration. The release note this phase owes, in one line: *the first launch after this
build writes a credential for every recovery phrase on the device; going back to an
older build afterwards lists each such profile twice until the next upgrade, and loses
nothing.*

### Phase 3 — the switch, the precedence, the switcher, the entrances

`9b3a1927`, `00fc9969`, `25464595`, `2f420230`. Upstream ships 2 with 3 and 4 with 5;
they go together here because a switch nobody can reach and a switcher nobody can add to
are each half a feature, and the four are 53 files of which the one resolution above is
the whole of the conflict.

What to look at: `observeActiveUserId` is a child of `collectLatest` and the per-pubkey
map is gone — the test that fails against the old observer is
`RelaysSocketManagerSwitchJvmTest`'s re-emission of A's list changing nothing;
`switchToIdentity` clears the identity *before* stopping the node, so every collector
that could reach the business is cancelled first; `StartupChoice.resolve` puts what a
switch or sign-in named above the user's earlier "show me the list"; `ProfilesScreen`
has a back button and one column at every width; `CreateProfileRoute(withWallet = true)`
only from Landing; the create view model writes the secret, then the six bootstrap
events, then calls the tail — the order the sign-in screen documents as load-bearing;
`JvmGlobalPrefs` is the only place the jvm target reaches `loadGlobalPrefsForWallet`.

Exit: on desktop and Android, a second profile can be signed in or created from the
switcher and switched to; the previous node is stopped once; a cold boot opens the
last-used profile without asking; `IdentitySwitchJvmTest`, `ProfilesScreenJvmTest`,
`AddProfileFromInsideJvmTest`, `CreateProfileScreenJvmTest` green.

**Built 2026-09-13**, `84ac84cc`, `73a0e5b9`, `5654e462`, `ccb9942a`: three clean picks
and the one the rule was written for — `2f420230` × `CreateProfileViewModel.kt`, taken
theirs, with the reason in its message. Exactness: 136 → 135 residual files, the one
gone being that file, as the third decision said; nothing else moved. Both compilers
clean; `jvmTest` 837 → 858 (`IdentitySwitchJvmTest`, `RelaysSocketManagerSwitchJvmTest`,
`ProfilesScreenJvmTest`, `AddProfileFromInsideJvmTest`, `CreateProfileScreenJvmTest`, and
the three added to `ReadOnlyEntrancesJvmTest`); `testDebugUnitTest` 413 → 420, the seven
being `StartupChoiceTest`; audit all budgets met, and the composable-literal count the
audit reports without a budget went 40 → 30 as the startup screen's words moved to the
catalogue; `strings.xml` 444 → 455. Read in review, as the phase asked: the observer is
one `collectLatest` child with no map; `switchToIdentity` sets the desired id, sets
`startWalletImmediately` back, clears the identity, and only then stops a node, and only
if there was one; `StartupChoice.resolve` reads *force, desired, "show me the list",
the only one, the default*; `CreateProfileRoute(withWallet = true)` appears once, under
Landing; the jvm target reaches `loadGlobalPrefsForWallet` through `JvmGlobalPrefs`
alone; `ProfilesScreen` puts `NavigateBackButton` in its bar.

### Phase 4 — the queue owner and the inbox, with the migration

`759199f2`, alone, because it is the schema change and upstream's rollout says it can
ship early or late independently of everything else. It fixes a case Mantra has today —
the closed inbox of an upgraded read-only identity — and the case the switch would
create: a wrap fetched under the other profile's key, stored, and never opened again.

What to look at: the `AutoMigration(from = 19, to = 20)` comment says what the columns
mean and that the broadcast queue deliberately has none; `DatabaseNostrRepository`
stamps where the caller stamped nothing and keeps an owner a caller named; the DAO's
own requests — placeholder-profile syncs, a Marmot join's participant syncs — stay
unowned on purpose, since they fetch public kinds; `reopenInbox` runs from the sync
pumps' collector once per activation, for an identity that can sign, *before* the live
inbox and the broadcast pump are launched; one transaction per wrap, so one that
cannot be opened rolls back its own changes and the next is still tried;
`GiftWrapMessage.toGiftWrapSeal` sets `giftWrapMessageId`. And the schema check: a
full compile, then `git status` — nothing under `composeApp/schemas/`.

Exit: `OwnedRequestQueueJvmTest` and `ReadOnlyGiftWrapDaoJvmTest` green; an npub
signed in, sent a DM by someone else, then upgraded with its nsec reads the DM on the
next activation; schema files 1–20 committed and regenerated identically.

**Built 2026-09-13**, `72afe8aa`, a clean pick. Exactness unchanged at 135, nothing
moved. Both compilers clean; `jvmTest` 858 → 864 (`OwnedRequestQueueJvmTest` and the
sweep cases added to `ReadOnlyGiftWrapDaoJvmTest`); `testDebugUnitTest` 420; audit all
budgets met. The schema check, which is what this phase is for: after a full build of
both targets `git status` shows nothing under `composeApp/schemas/`, and the committed
`20.json` is byte-identical to the rewritten fork's, identity hash `4414c373…` — Room
computed the same twenty tables from Mantra's entities as from Curare's, which is the
second decision's rule holding on its first use. Read in review: the sweep runs inside
`identity.canSign`, before the broadcast pump and the live subscriptions are launched;
it returns 0 without a query for a key pair with no private key; each wrap is re-indexed
in its own transaction and a failure is logged and skipped rather than thrown; the DAO's
own requests carry no owner; `GiftWrapMessage.toGiftWrapSeal` sets `giftWrapMessageId`;
the migration's comment says why the broadcast queue has no owner column.

### Phase 5 — the exits, the round trip, the record

`9bb34004`, `88577bb5`, `29027f2b`. Small, and the last: the not-found screen's third
action when another profile exists, the eight-phase round trip through the real view
models, and the built-record table at the top of `multiple-profiles.md` with the
README's reading-order sentence.

What to look at: `MultipleProfilesRoundTripJvmTest` constructs no `GlobalPrefs` of its
own — the library's per-process cache means a test that builds a
`SovereignWalletViewModel` must read the default through the view model's instance, the
trap `00fc9969` recorded — and Mantra's own future tests inherit that rule.

Exit: the full suite at the dry run's numbers or above; the audit green; the README's
table has the new row between the npub note and the jvm-target note.

### Phase 6 — close the loop

Five things, none of them a merge.

1. **The follow-up in the fourth decision**, natively: refuse a welcome for a room the
   device already holds under another profile. Before the line reaches users, not
   after the first report.
2. **Prose that is now off by a removal.** `multiple-profiles.md` anchors three links to
   line numbers — `MantraNavHost.kt:418` and `:746`, `DatabaseNostrRepository.kt:111` —
   that are upstream's, in a file Mantra's removal shortened by 264 lines. The anchors
   describe the tree *before* the line was built and were already approximate upstream;
   either drop the numbers or re-point them, in the same commit as item 1 or alone.
3. **Reverse-pull what Mantra owes the fork**, shrunk by the third decision:
   `WalletManagerExtension.kt` is dead on both sides and Curare can delete it; the Torch
   retirement is unchanged from the first plan's list. Two small commits, both
   hand-cherry-picks in the other repository.
4. **Offer item 1 upstream.** It is written under Mantra's names against a file both
   trees hold identically, so it applies to the fork as an ordinary cherry-pick — the
   direction this whole exercise exists to make cheap.
5. **The fork decision, restated with a second data point.** The first plan estimated
   half a day per pull; this one cost the machine about ten minutes and the reading about an hour,
   because the tooling was in place and the line crossed no seam. That is the good case.
   The bad case is any upstream commit that edits `ChatRoomDetailScreen.kt` or the nav
   host's identity block, and the driver will stop on it rather than resolve it. The
   three options the first plan listed stand in the same order — converge the package
   name, invert the relationship, or keep pulling — and nothing about this pull changes
   which is best; it only shows that the third is affordable for as long as the fork's
   work stays clear of what Mantra took out.

## Risks, and the silent ones in particular

**The write on first launch.** Phase 2's repair is the first time a pull has changed a
file in the node-data directory of an existing install. It is idempotent, one write for
however many seeds are missing, nothing at all on a device with none or one already
repaired, and a failed write is a result rather than a throw; the listing goes on with
what it read. Test it on a real install with a seed from before the first pull, not
only on the in-memory harness.

**The downgrade double-listing** is a release note, not a bug, and Phase 2 shipping
alone is what keeps it to one line.

**Room 20.** An older build cannot open the file; the same story as versions 2–19.
Anyone carrying a Mantra branch that adds a table must rebase onto this before numbering
it 20 — the second decision's rule, and the `ls-tree` check that enforces it.

**iOS is unverified**, again. `stopPlatformBusiness` has an ios actual that calls
`BusinessManager.stopBusiness`, one line, compiled by nobody on linux; the ios target is
gated behind macOS. Build it on a Mac before believing it — and before it, the
first plan's `Config.xcconfig` change, which has still not been.

**Every switch passes the social precondition gate.** Upstream names it as out of
scope: every `ProfileLoaded` routes through the onboarding gate, on every launch and so
on every switch. For Mantra, whose users will switch between a collective's preview and
their own profile more often than Curare's, this is the first thing a user will complain
about; the fix is one branch in `processLocalAccount` for an account the machine has
seen before, and it is Mantra's to write.

**The per-process `GlobalPrefs` cache.** A test that constructs `SovereignWalletViewModel`
must not build a `GlobalPrefs` of its own, or the second instance over the same file
throws — recorded upstream, and true of every Mantra test written after this.

**`strings.xml` loses three keys.** `change_account`, `end_this` and `select_a_wallet`
go; nothing on Mantra references them outside the files the same commits rewrite, and
the compile is the check. The `UNION` rule applies a deletion correctly since the first
plan's fix; it did not need to.

**The upstream moves.** Between this document being written and the pull landing,
`curated/curated` may gain commits. Redo Phase 1; the driver stops on anything new.

## Appendix — the residual, for the record

The 135 files by which Mantra at `776455ec` differs from the rewritten `86cb876b`, and
which this pull leaves alone, so that the next reader can tell a seam from a surprise:

| kind | count | what |
|---|---|---|
| Curare has, Mantra does not | 83 | 35 under `ui/composable`, 28 under `ui/view`, `nostr/curated/` (5), the seven `nostr/Group*` readings and `CuratedSuggestion` with their tests, `WalletManagerExtension.kt` |
| Mantra has, Curare does not | 4 | `docs/curated-to-mantra.md`, its two scripts, `NostrPublicKeyJvmTest.kt` |
| both have, differently | 48 | the logo's thirteen files and one `build.gradle.kts` comment; the seven places where Phase 0's prose about the protected names is worded for a tree that had no rebrand (`Relays.kt`, `SharedKeyDerivation.kt`, `ChillDkgRitualManager.kt`, `PlatformContext.jvm.kt`, `HomeScreen.kt`, `shared-key-derivation.md`, `material-design-conformance.md`); the removal's footprint of twenty-six — `m3-title-case.py`'s two sample titles, `strings.xml`, `MantraNavHost.kt`, `ChatRoomDetailScreen.kt` with its view model and state, `ChatMessage.kt`'s `applyInnerEvent` arms, `ProposedEvent` and its test, the four repositories, `ChatTranscript`, `ProfileAvatar`, `Member.kt`, `NostrEvent.kt`, `NostrEventDao`, `LocalChatRoom`, the broadcast screen, button, view model, state and two tests, `docs/README.md`; and, until this pull, `CreateProfileViewModel.kt` |

After the pull: 134, the same list less one.
