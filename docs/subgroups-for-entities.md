# A subgroup for a dialect, an artifact, a translation or a project

A group's room holds a library, a set of dialects and a list of projects. Making
a room for one of them — a differently-keyed group whose work *is* that artifact,
that dialect, that translation, that project — takes seven screens today: back
out to the room, find "Add subgroup", retype the name, pick the admins. Every
part of that except the admins is already known from the thing the user was
looking at.

This is the plan for one call that answers it, what the call hands back, the one
place the answer has to be written down so the room is still connected to the
entity tomorrow, and the screen a dialect turns out to need before it can be
asked at all.

Read [subgroups.md](./subgroups.md) first — the four steps, what the birth
certificate covers, and why the child's key is fresh. Nothing here changes any of
that. Written against the code as it stood at `5875655a`.

**Built**, eight phases, one commit each. The phases below are kept as the
reasoning they were, because the code reads better against the argument it came
from than against a summary of itself. Where the implementation chose
differently the table says so, and it did so seven times worth reading:

| what the plan said | what it turned out to be |
|---|---|
| schema 23 is the next free number | 23, 24 and 25 are all taken on an unmerged branch. Taken anyway, and Phase 0.2 says why: a number is free relative to the merge base, and pre-allocating 26 moves the renumbering rather than avoiding it |
| `proposeBirthCertificate` takes an `origin` parameter, passed down from the view model | it already takes the whole `DkgSession`, so it reads `key.origin()`. A parameter would have been a second source for one fact, and the proposer's copy of it at that. Nothing in the repository or the ritual view model changed |
| Phase 3 puts a button on each of four screens | Phase 7 replaced all four with one `LazyListScope` extension. The buttons were written twice, and the section should have been planned in Phase 3 — the giveaway was there in Phase 0.3, which already argued the entry point had to *grow* into a list |
| `ArtifactDetailUIState` gains a capability flag | that, and a `ChatRepository` in the screen *and* the view model. `ProjectDetailViewModel` needed one too, which was not anticipated: `canSign` alone cannot answer whether a room is a Marmot one |
| Phase 8 raises the M3 adaptive floor | it cannot. The floors count `MaterialTheme.breakpoint`, `BoxWithConstraints`, the pane scaffolds and the navigation components; `readableContent()` and `NavigateBackButton` are neither. A new screen conforming to the rules adds nothing to either count, and raising a floor the work did not earn would make it regress for an unrelated reason |
| the card says what a subgroup is for in its supporting line | an overline. The supporting line was already carrying whether this device can open the room, which is a different question and both are worth a line |
| the empty-dialect test deletes the rows | neither DAO has a `@Delete`, and adding one for a test is a schema surface nobody asked for. It seeds a third dialect with nothing in it, which is also the truer fixture — a room defines a dialect before it has anything in it |

One more worth stating because it was a *drafting* error rather than a design
one, and because it resolved itself. The first draft cited `DialectScaffold`,
`AfricanLanguages` and [african-dialect-catalogue.md](./african-dialect-catalogue.md)
as precedent; at the time none of them were in the tree, so the citations were
replaced with the reasoning stated directly. The dialect catalogue landed on
`mantra` while this was being built, so all three are here now and the
cross-references below are real again — but the restated reasoning is kept
rather than turned back into links, because a plan that explains its own rule
survives the file it borrowed it from being moved.

Two of them are worth following. `DialectScaffold` is the closest thing in the
tree to Phase 1's `SubgroupOrigin`: pure, in `managers/`, derived from the rows
rather than beside them. And `DialectScaffold.pendingIn` is Phase 7's
`ceremoniesFor` solving the same problem one layer up — it reads the proposals
already in flight so two admins looking at the same list do not propose the same
forty dialects twice. The subgroup version of that race is two rooms for one
project instead of eighty dialects, which is rarer and more expensive.

## What the four have in common

| entity | kind | name | room |
|---|---|---|---|
| `MantraDialect` | 30304 | `name` | `chatRoomId` |
| `MantraArtifact` | 30300 | `name` | `chatRoomId` |
| `MantraTranslationArtifactVersion` | 30306 | `name` | `chatRoomId` |
| `MantraProject` | 30330 | `name` | `chatRoomId` |

Three fields, the same three on all four, plus a kind that is a per-type
constant. That is the whole of what a subgroup needs to be started: the parent is
`chatRoomId`, the proposed name is `name`, and the thing it is for is
`(kind, id)`.

It is worth saying plainly how little of this feature is the convenience
function, because it decides where the effort goes. Deriving `(parent, name)`
from an entity is four field reads. What is actually missing is the other
direction: **nothing in the app can say which subgroup a project's room is**, and
without that the convenience function produces rooms that are related to their
entity by a string somebody typed once.

## The fork: a name, or a signed link

This decision changes the shape of the plan and should be made now rather than
discovered in Phase 6.

**Option A — pre-fill only.** `SubgroupOrigin.of(project)` gives
`(parentChatRoomId, name)`, the picker opens with the name filled in, and the
ceremony runs exactly as it does today. One pure file, four buttons, no schema
change, no new tag. A room called `#Setswana Luke` and a translation called
`Setswana Luke` are then connected by nothing a reader can check, and
`ChatRoomDetailScreen` lists the subgroup under "subgroups" with no idea what it
is for. Two subgroups for one project are indistinguishable from two subgroups
with similar names.

**Option B — the certificate says what it is for.** The birth certificate grows
one optional tag naming `(kind, id)`, and that is the link: signed by the
parent's quorum, carried on the child's key state, checkable by every member, and
already read by every screen that reads subgroups.

**Recommended: B, phased so that A ships inside it.** Phases 1–3 are the pure
function, a dialect's screen and the entry points, which are useful on their own
and are exactly Option A; Phases 4–7 add the tag, the column that carries it and
the reverse view. Stopping after Phase 3 is a legitimate release, and nothing in
Phases 1–3 has to be redone to continue.

The argument for putting the link on the certificate rather than anywhere else is
the argument the certificate was designed around, and it is in
[subgroups.md](./subgroups.md#informed-consent-and-what-the-parents-admins-are-actually-signing):
the tags carry what makes the claim checkable, and the signature covers them
because an event id hashes over its tags. A parent admin is already being asked
to sign a room id produced by a ceremony most of them were not in. "This is the
room for the Setswana Luke translation" is the single most useful sentence that
can be put in front of them, and it costs one tag.

The alternatives were considered and are worse:

- **A column on the entity.** `MantraProject.subgroupChatRoomId` would be a local
  column on a quorum-signed, content-hashed row. Either it goes into the event —
  and then the id changes, and the project cannot name a room that does not exist
  until after the project is signed — or it does not, and it is a fact one device
  holds and no other member can see.
- **A new table.** A join table from entity to room, written locally. Same
  problem one layer out: nothing signs it, so it is this device's note.
- **The room's subject.** Already taken by the name, and a name is not an id. A
  dialect is the clearest case: nothing in the schema makes one unique — the id
  is a content hash including `createdAt`, there is no index, and the rows are
  quorum-signed so they cannot be rewritten — so a dialect's identity is its
  `(language, country)` pair and never its name. A room that typed "Southern
  Sotho" where another typed "Sesotho" holds the same dialect, and two rooms
  named for it are two rooms with no checkable relation to it.

The certificate is the only artefact in the relationship that is signed, travels,
and is already read on both sides.

## What the tag is, and what it is not

```
["subgroupFor", "30330", "<64-hex event id>"]
```

Kind first, id second, both required for the tag to parse. The kind is not
redundant: an id alone would have to be looked up in four tables to be
understood, and a reader that does not know the kind cannot tell "a project this
device does not hold" from "not a project at all". The precedent is
`PayloadKindTag`/`PayloadIdTag`, which carry the same pair for the same reason —
so a client can decide what it is looking at without parsing the thing itself.

**It authenticates nothing about the entity.** The parent's quorum is signing that
*it intends* this room to be for that entity; it is not vouching that the entity
exists, that this device can resolve it, or that the entity's own signature is
good. That is the same limit `docs/project-feature.md` states for a cross-room
project membership — "the group asserts the membership; it does not vouch for the
thing named, which no amount of signing can" — and it is the correct limit here
too, because a subgroup's members need never have been in the parent and so need
never hold the parent's rows.

**It is never checked by `certifies`.** The founding-roster rule in
`SubgroupBirthCertificateEvent` says the name and the `p` tags are history and
are not compared against anything current, and the origin joins them. A
certificate with no origin tag is valid — every certificate signed before this
feature has none — and a certificate naming an entity this device cannot resolve
is valid. There is a test whose job is to fail the day somebody adds the
comparison that looks obviously missing, and this adds a second case to it.

## Phase 0 — the decisions, written down

Before any code:

1. **The fork above.** B, phased. Record it here if it changes.
2. **Schema number. Checked, and it collided: 23, 24 and 25 are all taken on
   `claude/focused-brattain-433b3b`**, which is unmerged — it keys a `DkgSession`
   by profile (23), splits `ChatRoomMembership` off `ChatRoom` (24) and replaces
   `ChatMessage.isUserMessage` with `senderIdentity` (25). `mantra` is at 22.

   **Taken anyway: 23**, with `AutoMigration(from = 22, to = 23)`, because the
   merge base is what a number is free *relative to* and pre-allocating 26 does
   not avoid the work. Room needs a continuous path from the version on a user's
   device: if that branch lands first the edge here has to become 25 → 26
   regardless, and if it is itself renumbered or abandoned a pre-allocated 26
   leaves three dead numbers behind. Whichever tree merges second renumbers, one
   commit, which is the bargain `MantraDatabase`'s v22 comment already records.

   The check is worth doing even though it did not change the answer, which is
   the point `docs/project-feature.md` was making: it went wrong by checking the
   wrong tree, not by failing to check. The sweep that found this:

   ```bash
   for b in $(git for-each-ref --format='%(refname:short)' refs/heads/); do
       git show "$b:composeApp/src/commonMain/kotlin/press/mantra/compose/database/MantraDatabase.kt" 2>/dev/null |
           grep -m1 "^    version = " | sed "s|$| <- $b|"
   done | sort -u
   ```
3. **The dialect entry point. Settled: a dialect gets its own screen**, which is
   Phase 2. The first draft of this plan recommended hanging the action off the
   dialect card in `ChatRoomDetailScreen` instead, on the grounds that a whole
   screen to hold one button is a screen somebody has to keep. That was wrong on
   its own terms twice over, and the reversal is recorded rather than quietly
   edited out because the argument against it is the argument for Phase 7.

   A dialect is the one of the four with no screen, so the card would have been
   the only entry point that *cannot grow*: Phase 7 gives every entity a list of
   the subgroups made for it and the ceremonies in flight for it, and a
   `ListItem` in a `LazyColumn` of dialects has nowhere to put either. The
   asymmetry would land exactly where it hurts, since a dialect is the entity a
   room is most likely to want several rooms for. And the card is already the
   thinnest surface in that screen — it is the only one of the three lists whose
   `Card` has no `onClick` at all, so a dialect is the one thing in a room a
   member cannot open and look at.
4. **Whether a second subgroup for one entity is refused.** **Recommended: no.**
   Two teams working on one artifact in two rooms is a real thing and refusing it
   would be this device deciding a group's structure. What Phase 7 does instead is
   show what already exists, which is what prevents the *accidental* second one.

## Phase 1 — `SubgroupOrigin`, and the trait that feeds it

One commit. Pure, `commonMain`, no database and no Compose.

A trait in `database/model/traits/`, implemented by the four entities:

```kotlin
/**
 * An entity a subgroup can be made for.
 *
 * Four rows in three tables have the same three fields and a per-type kind, and
 * this is what makes "which things can a subgroup be made for" a list the
 * compiler holds rather than four overloads somebody has to remember to extend.
 */
interface SubgroupOriginEntity {
    val id: HexKey
    val name: String
    val chatRoomId: String
    val subgroupOriginKind: Kind
}
```

`MantraDialect`, `MantraArtifact`, `MantraTranslationArtifactVersion` and
`MantraProject` each gain `override` on the three fields they already have and
one line for the kind. **No `toXEvent` or tag order changes** — the trait is
about naming a room, touches no event, and so cannot move an event id.

Then the function the whole plan is named after:

```kotlin
object SubgroupOrigin {
    /** What a certificate names: the kind and the id, together or not at all. */
    data class Ref(val kind: Kind, val id: HexKey)

    /** Everything a picker needs to open a subgroup for [entity]. */
    data class Candidate(
        val parentChatRoomId: String,
        val name: String,
        val origin: Ref,
    )

    fun of(entity: SubgroupOriginEntity): Candidate
}
```

Parallel to `DialectScaffold`, `TranslationScaffold` and `ChapterScaffolding`:
`managers/`, pure, tested in `commonTest`. `name` is trimmed and handed over bare — the `#` is added
by `MarmotGroupName.of` where the name is used, twice, idempotently, exactly as
the typed name is today.

Tests (`commonTest`, `SubgroupOriginTest`):

- one case per entity type, asserting parent, name and ref;
- a blank-named entity yields a blank candidate name rather than inventing one,
  so the picker's "Give the subgroup a name." refusal still fires;
- `Ref` holds a kind and an id and there is no constructor that holds one.

## Phase 2 — a dialect gets a screen

One commit. No new machinery, two new queries, and the only phase here that is
not about subgroups at all.

Artifacts, translations and projects each have a route, a UI state, a view model
and a screen. A dialect has a `ListItem` inside `ChatRoomDetailScreen`'s
`LazyColumn` with no `onClick`, so it is the one entity in a room that cannot be
opened. Phase 3 needs somewhere to put an action and Phase 7 needs somewhere to
put two lists, and this is that place.

Follow `ArtifactDetailViewModel` exactly — it is the closest sibling and the
shortest, which is the point of copying it rather than the richer ones:

| piece | shape |
|---|---|
| `DialectDetailRoute` | `activeUserPublicKey`, `dialectId`, `chatRoomId`, `relayHint` |
| `DialectDetailUIState` | sealed `Loading` / `Error` / `Loaded` |
| `DialectDetailViewModel` | `initiateDialectDetail()`, a `factory`, `MantraRepository` |
| `DialectDetailScreen` | registered in `MantraNavHost` beside `ArtifactDetailRoute` |

`Loaded` carries the dialect, what is written *in* it, and the capability flag:

```kotlin
data class Loaded(
    val dialect: MantraDialect,
    val artifacts: List<MantraArtifact> = emptyList(),
    val translations: List<MantraTranslationArtifactVersion> = emptyList(),
    val canAddSubgroup: Boolean = false,
) : DialectDetailUIState
```

Those two lists are what a dialect screen is *for* — a dialect on its own is
three strings and a screen showing only them has no reason to exist. They are
also the two tables that carry a `dialectId`:
`MantraArtifact.dialectId` is the dialect an artifact is written in, and
`MantraTranslationArtifactVersion.dialectId` is the dialect a translation is
into. Both are plain columns and neither has a query yet:

- `MantraDialectDao` keeps its two queries unchanged.
- `MantraArtifactDao` gains `getArtifactsByDialectId` and
  `MantraTranslationArtifactVersionDao` gains `getTranslationsByDialectId`. Both
  `WHERE dialectId = :dialectId ORDER BY name`, matching
  `getDialectsByChatRoomId`'s shape.
- `MantraRepository` gains `getArtifactsForDialect` and
  `getTranslationsForDialect`, and `NO_OP` gains both returning `emptyList()`.

**Scope it to the dialect, not to the room.** A `dialectId` is a foreign key
inside one room today — see the one-room scope decision in
`docs/project-feature.md` — so filtering by the dialect alone cannot reach
another room's rows, and adding `AND chatRoomId = :chatRoomId` would be a second
spelling of a constraint the key already enforces. The day projects go
cross-room that stops being true, and the query is then one of the places that
has to learn about it.

The screen itself: `TopAppBar` with `NavigateBackButton(onNavigateBack)`,
because this is a pushed screen and the desktop target has no system back —
twenty-two screens were dead ends before that rule. `ScreenStateTransition` over
the state `when`, as the whole composable's body. Four states, with `EmptyState`
given a real message for each of the two lists, since one shared default is how
five different absences all came to say the same thing. Language and country
through the shared `DetailRow` widget. `readableContent()` on the content root.

Then the dialect card in `ChatRoomDetailScreen` gets the `onClick` it never had,
pointing at the new route — which is a fix independent of everything else here
and is the reason this phase stands alone.

Tests (`jvmTest`, `DialectDetailJvmTest`): a dialect with two artifacts and one
translation in it loads all three; an artifact in a *different* dialect in the
same room is not listed; a missing dialect id yields `Error` rather than an empty
`Loaded`.

## Phase 3 — four entry points

One commit. This is where Option A is complete and shippable.

`SelectSubgroupAdminsRoute` grows three nullable fields:

```kotlin
val name: String? = null,
val originKind: Int? = null,
val originId: String? = null,
```

Flat strings because a route is `@Serializable` and carries no domain types.
Nullable because the room's own "Add subgroup" button passes none and must keep
working unchanged.

`SelectSubgroupAdminsViewModel` takes them, seeds `name.value` from `name` on
construction rather than in `initiate()` — a field the user may already be typing
into must not be overwritten by a load completing — and holds the `Ref` for
Phase 5. The name stays editable: a pre-fill is a suggestion, and a group
renaming its own room is not this plan's business to prevent.

The four buttons, each `enabled = canAddSubgroup` beside the actions already
there:

| screen | where |
|---|---|
| `ArtifactDetailScreen` | beside the existing signing actions |
| `ProjectDetailScreen` | same |
| `TranslationArtifactVersionDetailScreen` | same |
| `DialectDetailScreen` | the screen Phase 2 built |

The gate is `canAddSubgroup`'s, not `canSign` alone: a share of the key **and**
`mlsGroupState != null`, because a NIP-17 room has no admins to be a parent's and
these entities can live in one. Name the flag `canAddSubgroup` on all four states
so there is one spelling of one rule.

`canSign` is the half that is already there, and `ProjectDetailUIState` and
`TranslationArtifactVersionDetailUIState` already carry it beside the signing
actions they already draw. `ArtifactDetailUIState` does **not** — it has
`artifact`, `versions`, `chapters` and `translations` and no capability flag at
all — so it gains one, read the same way the other two read it, from
`frostSigningRepository.canSign`. That is a genuine addition and the one place
this phase is more than a button. `DialectDetailUIState` carries the flag from
the moment Phase 2 writes it, so only the artifact screen is short.

It is not the whole guard — `refuseSubgroup` still runs in the picker and again
before anything is published — but it is the one that decides whether the button
is drawn.

M3, per [CLAUDE.md](../CLAUDE.md): one new string in
`composeResources/values/strings.xml`, sentence case; spacing from
`MaterialTheme.spacing`; the dialect card's icon button gets a real
`contentDescription`, since "make a subgroup" is not repeated by any text beside
it; the card's existing decorative icons stay `Decorative`.

## Phase 4 — the tag

One commit.

`nostr/subgroup/tags/SubgroupOriginTag.kt`, following `PayloadKindTag`'s shape
exactly — `TAG_NAME`, `parse` with `ensure(tag.has(2))`, `toIntOrNull()` for the
kind, `assemble`. A tag with one of the two values parses to null, because half a
reference is a reference with the checkable part removed.

`SubgroupBirthCertificateEvent`:

- `assembleTags` takes `origin: SubgroupOrigin.Ref? = null` and appends the tag
  when it is there. **Appended after the name and before the `p` tags**, and the
  position is fixed for the same reason the lineage's order is fixed in
  `SubgroupLineage`: a reader that predates this takes the tags it knows off the
  front and ignores the rest, and a tag inserted *into* the run of `p` tags would
  split the admin list for anybody parsing positionally.
- `parseOrigin(tags)`, returning `Ref?`.
- `certifies` is **not touched**. Add the case to the test that guards the
  founding-roster rule: a certificate whose origin names an entity this device
  does not hold still certifies.

Tests (`commonTest`, extending `SubgroupBirthCertificateEventTest`):

- assemble → parse round trip for each of the four kinds;
- a certificate with no origin tag parses to null and still certifies;
- a malformed origin (missing id, non-numeric kind, 63-hex id) parses to null
  rather than to a half-reference;
- the tag does not disturb `parseAdminPublicKeys` or `parseName`.

## Phase 5 — carrying it through the ceremony, and schema 23

One commit. This is the only phase with a migration.

The certificate is proposed *after* the ceremony and may be proposed by an admin
who is not the coordinator — `SubgroupManager`'s whole shape is that every step
reads stored rows rather than a session object handed down from the screen that
started it. So the origin has to survive on the `DkgSession`, and it has to reach
every participant's session, not only the coordinator's.

The precedent is exact. v19 added `subject` and `participantPublicKeys` to
`DkgSession` for precisely this reason, and v17 added `parentChatRoomId`; all
three are nullable, none is a foreign key, and all three are read off the ritual
proposal's tags. This follows them line for line:

- `DkgSession` gains `originKind: Int?` and `originId: HexKey?`. Nullable, no
  default, not foreign keys — an origin routinely names a row a subgroup member
  does not hold, so a dangling value is the normal state and a cascade would mean
  deleting a project deletes a room.
- `MantraDatabase` → `version = 23`, `AutoMigration(from = 22, to = 23)`, with a
  comment in the house style saying what reads back null and why that is the true
  answer for those rows: a ceremony that ran before this was opened for no entity.
- `DkgRitualEvents.assembleTags` takes the ref and emits the same
  `SubgroupOriginTag`; `parseOrigin` reads it. The doc comment says what
  `parseParentChatRoomId`'s says and for the same reason — **this authenticates
  nothing**, anybody can put any id here, and the claim that decides is the
  certificate two steps later.
- `ChillDkgRitualManager.proposeRitual` and `acceptProposal` file it, the way they
  file `subject`.
- `DkgRepository.proposeRitual` grows the parameter; `NO_OP` grows it too.
- `SelectSubgroupAdminsViewModel.confirm` passes the `Ref` it has held since
  Phase 3.
- `SubgroupManager.proposeBirthCertificate` takes `origin: SubgroupOrigin.Ref?`
  and passes it to `assembleTags`; `DkgRitualViewModel.proposeBirthCertificate`
  reads it **off the session** — `session.originKind`/`session.originId` — never
  off a route, for the same reason it reads the parent off the state and not the
  route.

Tests (`jvmTest`):

- `SubgroupDaoJvmTest`: a session round trips both columns, and a row with both
  null reads back as no origin;
- `SubgroupCeremonyInParentRoomTest` or a new `SubgroupOriginJvmTest`: proposal →
  every participant's session → certificate tags, asserting the id that comes out
  of the signed certificate is the one that went into the picker;
- a ceremony opened with no origin produces a certificate with no origin tag,
  which is the room's own "Add subgroup" path and must not change.

## Phase 6 — what the signer and the room see

One commit, no schema.

`SubgroupManager.Subgroup` gains `origin: SubgroupOrigin.Ref?`, parsed from the
certificate it already parses the name and admins from. Nothing else about that
type changes, so every existing reader keeps working.

`ProposedEvent.summarize`'s 30329 branch gains the origin, resolved to a name
where the device holds the row and shown as a kind plus a short hash where it does
not — `describe`'s existing bargain, and the comment there already says why a
short hash in front of somebody deciding whether to sign is worth stating rather
than hiding. This is the phase that pays for the tag: a parent admin reads "a new
subgroup of this group · Setswana Luke · for the translation Setswana Luke · run
by 4 members" instead of a name and a count.

`SubgroupCard` in `ChatRoomDetailScreen` gains a supporting line saying what the
subgroup is for, on the same terms.

Resolution needs a lookup by id across four tables. `MantraRepository` already
has `getDialect`, `getArtifact`, `getTranslation` and `getProject`, each taking
an id — so this is one `when` on the kind over functions that exist, and
deliberately not a new DAO query.

## Phase 7 — the other direction

One commit, no schema. The phase that makes the link worth having.

Each of the four entity surfaces shows the subgroups made for it, and in-flight
ceremonies for it:

- **Made.** Filter `chatRepository.subgroupsOf(entity.chatRoomId)` on
  `origin == SubgroupOrigin.of(entity).origin`. No new query: the parent's
  certificates are already loaded by the room's screen, and the entity's screen
  reads the same list. A subgroup whose room this device does not hold still
  shows, because `Subgroup.room` is already nullable and already drawn that way.
- **In flight.** `DkgSession` rows in that room with that origin whose stage is
  neither `FAILED` nor `COMPLETE`. The same move `DialectScaffold.pendingIn`
  makes for the dialect catalogue, and for the same reason. Two admins both open
  the project's screen,
  both see no room, and the group gets two ceremonies and two rooms for one
  project — a race this feature would hit on first use, because the screen that
  invites the act is the screen that cannot see the act in progress. Making the
  open ceremony readable at the moment of proposing is what stops it, and it is
  cheap: `getLatestSubgroupSessionFor` is already the shape of the query.

Nothing is refused (Phase 0.4). The button reads differently when a room exists —
"open the project's room", or the add action beside a list of one — and that is
the whole intervention.

Tests (`jvmTest`): a VM whose room holds two subgroups, one for this entity and
one for another, lists exactly one; an entity with a ceremony in flight shows it;
a completed ceremony is not shown as in flight.

## Phase 8 — docs and the audit

One commit.

- This file, updated to say what the implementation chose differently. Every doc
  in `docs/` carries that table and it is the most-read part of them.
- A pointer from `docs/subgroups.md` beside the multiple-parents one.
- A line in `docs/project-feature.md` under "what this does not do", since a
  project's room is the first thing that file says it lacks.
- **The M3 floors do not move, and the reason is worth keeping.** The plan said
  Phase 2's new screen would let the adaptive floor be raised. It does not:
  `FLOOR_ADAPTIVE_APIS` counts `MaterialTheme.breakpoint`,
  `BoxWithConstraints`, `currentWindowAdaptiveInfo`, the pane scaffolds and
  `listPaneWidthFor`, and `FLOOR_NAVIGATION_COMPONENTS` counts the navigation
  components. `readableContent()` and `NavigateBackButton` are in neither list.

  So a screen that follows every rule in CLAUDE.md adds nothing to either count,
  and raising a floor to the number the tree happens to sit at would make it fail
  the next time something unrelated was removed. The floors guard two-pane layout
  and the navigation shell; a detail screen is neither. Both stay where phase 6
  of the conformance work left them.

## Verifying

In a fresh worktree, bootstrap the submodule chain first — four nested
submodules, and gradle otherwise dies on `':library' not found`.

```bash
ANDROID_HOME=/home/sigidli/Android/Sdk ./gradlew :composeApp:compileDebugKotlinAndroid
```

```bash
ANDROID_HOME=/home/sigidli/Android/Sdk ./gradlew :composeApp:jvmTest :composeApp:testDebugUnitTest
```

```bash
./gradlew :composeApp:m3Audit
```

`IdentitySwitchJvmTest`'s "last profile opened" case fails about one run in nine
on a clean tree; check the base before blaming a change here.

## What this does not do

- **It does not make a subgroup cheaper.** Four multi-party rounds, every one
  needing every selected admin present. The convenience is in the naming and the
  link, not in the ceremony, and a group that wants a room per chapter is going to
  find that out.
- **It does not move the entity.** A project whose room is a subgroup is still a
  row in the *parent's* tables, signed by the parent's quorum, and the child's
  members may hold none of it. Giving the child its own copy is a cross-room
  question and is Phase 9 of `docs/project-feature.md`, not this.
- **It does not resolve an origin across rooms.** A certificate may name an entity
  this device cannot see, and the UI shows a kind and a short hash rather than
  pretending.
- **It does not make the origin evidence.** The parent asserts what the room is
  for. Nothing here checks that the entity exists, and nothing can.
```
