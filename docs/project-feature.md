# Projects

A phased plan, not yet built.

`mantra-db-models` — the platform's server-side Sequelize schema — has had three
models waiting for this app since before it existed: `project`, with a name and a
description, and the two join models that give it its contents,
`projectArtifactVersion` and `projectTranslationArtifactVersion`. A project is
how the platform says *this body of work*: a set of source versions to translate
from and a set of translations being produced against them, grouped under one
name so that funding, progress and membership have something to attach to.

This app has the whole tree underneath a project already — eight nip30303 kinds
from `Artifact` (30300) down to `TranslationChunk` (30309), each one an event the
group signs with its own threshold key and a Room row derived from it. What it
has no way to say is that two of those translations and three of those source
versions are one piece of work.

The server's schema is a relational description of a platform with accounts. This
app is a Marmot/MLS group with no accounts, no server and no authority but a
quorum of its own members. Every foreign key on `project` therefore has to be
re-answered, and saying how is most of the work this document does: one becomes a
tag of its own, one becomes a set of tags, one becomes the room, and one is out of
scope. The code to write is comparatively mechanical — the same eight-file shape
every nip30303 entity already has, three times over — and the one place it goes
quietly wrong is a role the parser has not been told about.

## What the server's schema says, and what survives

```
project
  id            string, nanoid
  name          string, not null
  description   text,   not null
  creatorId     -> user,     not null
  ownerId       -> owner,    not null, UNIQUE
  campaign      hasOne campaign
  hasMany projectArtifactVersion
  hasMany projectTranslationArtifactVersion

projectArtifactVersion              projectTranslationArtifactVersion
  id                                  id
  creatorId -> user,            NN    creatorId -> user,                      NN
  projectId -> project,         NN    projectId -> project,                   NN
  artifactVersionId
            -> artifactVersion, NN              -> translationArtifactVersion, NN
```

Taking those fields one at a time:

**`name` and `description` survive** as the substance of the thing. By this
family's convention the long text goes in the event's `content` and the short
label gets a tag of its own — `ArtifactEvent` puts the name in content because an
artifact has no longer field, `ChapterEvent` puts `originalText` there and the
name in a `NameTag`. A project has both, so it follows the chapter: `content` is
the description, and `NameTag` carries the name.

**`creatorId` survives as a `CreatorTag` on the project event**, and the
difference between that and a column is the whole point. Every nip30303 entity a
member creates is now proposed for FROST signing rather than submitted, so the row
that lands is authored by *the room's own key* and carries no trace of who typed
it: `publicKey` is the room's id, and both Marmot columns are null on a
group-signed row. A `creatorPublicKey` column filled in from local knowledge would
be a field the signed event does not carry — which, under a signature, is the one
kind of mistake that is expensive. A `["creator", "<hex>"]` tag written into
the template *before* it is proposed is not that: it is inside the id the quorum
signs, so what lands is the group saying "this project was started by X" rather
than one device's note about who happened to insert a row.

That is a stronger statement than the server's column, and it is worth being clear
about what kind of statement it is. The creator does not sign the project event —
the room does — so this is a **group attestation, not proof of authorship**. A
member could propose a project crediting somebody else, and a quorum that does not
read the proposal would sign it. The mitigation is not cryptographic and does not
need to be: it is that the signing screen must show the creator, which makes it one
of the things a member is agreeing to. See Phase 4.

The proposer's identity is still recorded independently, on the
`FrostSigningSession` the proposal opened. The two can disagree, and when they do
the session is the fact about who acted while the tag is the group's claim about
whose work it is. Those are different questions and both are worth keeping.

**`ownerId` survives as `ContributorTag`s with an owner role — plural**, and it
buys something the structural answer could not. The structural answer is real and
stays: a project is signed by the group's threshold key at the room's derivation
path, so it says which *group* it belongs to by being signed at all, and
`chatRoomId` — a foreign key to `ChatRoom` with `onDelete = CASCADE` — carries that
exactly as on `MantraArtifact` and `MantraDialect`. What it cannot say is that a
project is the group's work and somebody *else's* property, which is the whole
reason the server has an `owner` with an `ownerEntity` join behind it: an owner
there can be an individual or an organization, and need not be the people doing
the work.

Pubkeys collapse that chain, because in this app an owner that is not a person is
a group, and a group is a pubkey — its room id. **A project can have several**, so
this is a set rather than a field: two organizations co-owning a translation, or a
publisher and the group that did the work, are ordinary cases, and the server's
`UNIQUE` on `project.ownerId` — one owner, one project, which looks like a
modelling slip on that side — is dropped rather than reproduced.

**Always emit at least one, and default to the room's own public key.** Not an
empty set with the room as an implied default: a project with no owner tag and a
project owned by the room would then be the same project, and making them the same
*event* means no reader ever has to know the default. The room's id is 64 hex
characters, so it is a well-formed `ContributorTag` value, and the common case —
"we own what we are making" — comes out as a real entry rather than an absence.

**`campaign` is out of scope.** There is no `Campaign` or `Pledge` anywhere in this
app — `grep -ril "campaign\|pledge\|satoshi"` over `commonMain` returns the
lightning wallet and `Zap` and nothing else — and nothing in this plan models one,
reads one or leaves a column for one. Funding is a separate feature that needs
projects to exist first.

**The two join models survive as they are**, minus `creatorId` — see
[Phase 1](#phase-1--three-events-and-their-tags) for why a membership gets no
creator tag of its own — and they are the interesting half.

## A creator tag, and owners on the `p` tag

Two fields, two shapes, and the thing that decides which is cardinality.

**One creator, so a tag of its own.** `CreatorTag`, tag name `creator`, a
single-value tag holding 64 hex characters, in `NameTag`'s shape. A project is
started by one person; there is no list to keep.

**Many owners, so `ContributorTag` with an owner role.** That is exactly what
`ContributorTag` is for — an open-ended set of people in a named role, which is
also how the translation side uses it for translators, proofreaders and editors.
A project owned jointly is the case the server's `owner` → `ownerEntity` join
exists to express, and a `p` tag per owner expresses it without a second table of
events.

Keeping them apart is worth a sentence because reusing `ContributorTag` for both
is the obvious move. It is wrong for the creator twice over: it would make a
single-valued field look like a list, and it would put two tags of the same name
on one event for no gain — see [the two traps](#the-two-traps-owners-still-have)
below, both of which the creator now simply avoids.

### `ROLE_OWNER` has to reach `KNOWN_ROLES`, in the same commit

```kotlin
private val KNOWN_ROLES = setOf(ROLE_TRANSLATOR, ROLE_PROOF_READER, ROLE_EDITOR)
…
val role = tag.getOrNull(2)?.takeIf { it in KNOWN_ROLES }
```

The filter is there for a good reason, which the file states: a stale relay hint
copied out of a kind:3 p-tag would otherwise render as `Role: wss://relay…` under
an author chip. The consequence for an unregistered role is specific and silent —
the tag parses, the role comes back `null`, the string stays inside the signed
event id, the event still verifies, and the project lands with no owners. Nothing
is logged, because nothing went wrong as far as the parser is concerned.

So `ROLE_OWNER = "owner"` goes into the constants **and** into `KNOWN_ROLES`, in
the same commit as the first event that emits it. That is [Phase
1](#phase-1--three-events-and-their-tags), and it is the first line of it. Note
that the app's three existing roles already diverge from NIP-F4, whose
spec-defined set is host/cohost/editor; this continues that divergence rather than
starting it, and the comment above `KNOWN_ROLES` should say so.

`ROLE_CREATOR` is **not** added. There is no creator role, because the creator is
not a contributor tag.

### The two traps owners still have

**`addUnique` keeps one tag per *name*, not per value.**

```kotlin
fun addUnique(tag: Array<String>): TagArrayBuilder<T> {
    if (tag.isEmpty() || tag[0].isEmpty()) return this
    tagList[tag[0]] = mutableListOf(tag)   // replaces the whole list
    return this
}
```

Emitting three owners with `addUnique` leaves one. Use `addUniqueValueIfNew`,
which appends and dedupes on `tag[1]` — the pubkey — so a key listed twice by a
form appears once. For a *set* of owners that is the correct semantics;
`addUniqueValueIfNew` was the wrong call only back when creator and owner shared
the tag name and deduping by pubkey would have collapsed a person who was both.

**Emit them sorted.** The id is a hash over the tag array as emitted, so the order
owners come out in is part of what the group signs. A multi-select hands them over
in selection order, which is nothing in particular. `DkgSession.participantPublicKeys`
already sorts for a version of this reason — "so two devices that assembled the
set in different orders store the same string" — and the same line makes a project
canonical, keeps the owner list on the detail screen stable, and costs a
`.sorted()`.

**Selecting by role, not position**, is no longer a trap: with the creator gone
from the `p` tag, every `ContributorTag` on a project event is an owner. Filter on
the role anyway — `it.role == ROLE_OWNER` — because that assumption is one future
role away from being false, and the failure would be an owner list quietly
containing somebody else.

### What the `p` tag costs the owners, and why it is acceptable here

A `p` tag is not an inert field in nostr. NIP-01 indexes single-letter tag names
and makes them queryable as `#p`, so it means "this pubkey is involved": it is what
relays index, what `Nip17Filters` subscribes on, and what a notification system
reads as a mention.

Inside a Marmot group none of that applies — these events are never on a relay as
themselves. But `BroadcastGroupSignedEventScreen` takes an event the group signed
and publishes it to public relays, and a broadcast project would put its owners'
keys into those relays' addressed-pubkey index, queryable, with a client free to
notify an owner about a project they were not told about.

For owners that is tolerable and arguably right: an owner *is* involved, which is
what the tag means, and a project the group chose to publish is one it chose to
publish under their names. It is still worth the broadcast screen saying whose keys
are in what is about to leave, and that belongs on the list of things that screen
should say rather than in this feature.

The creator escapes this entirely, which is a second small reason the split is the
right one: `creator` is multi-character, so it is not indexed, not queryable and
not an address, and a broadcast project carries the creator as data rather than as
a recipient.

### One thing not to copy

Six existing tags carry a stray `Tag` suffix in the *wire value* — `chapterIdTag`,
`chunkIdTag`, `dialectIdTag`, `translationIdTag`, `translationChapterIdTag`,
`translationChunkIdTag` — while `artifactId`, `artifactVersionId` and
`translationArtifactVersionId` do not. It is a copy-paste slip and it is
**unfixable**: those strings are inside event ids the group has already signed, so
renaming them would invalidate every signature over them. The new tag is plain
`creator`, and the slip is worth a comment where somebody would otherwise tidy it.

## Three kinds, and where they can go

The nip30303 band is full, and worse than full.

```
30300 Artifact                     30310 TranslationContributorList   / DKG proposal
30301 ArtifactVersion              30311 Translation                  / DKG host key
30302 Chapter                      30312 Submission                   / DKG round 1
30303 Chunk                        30313                                DKG coordinator round 1
30304 Dialect                      30314                                DKG round 2
30305 TranslationAVContributorList 30315                                DKG certificate
30306 TranslationArtifactVersion   30316                                DKG failure
30307 TranslationChapterContribLst 30320-30325                          FROST signing
30308 TranslationChapter           30326                                GroupKeyState
30309 TranslationChunk             30327-30328                          Chronicle
                                   30329                                SubgroupBirthCertificate
```

Three kinds are double-booked. `SubmissionEvent.KIND` is 30312 and
`DkgRitualEvents.ROUND_1` is 30312; 30310 and 30311 collide the same way. It has
been survivable because the two families travelled in different envelopes — a
ceremony's messages were NIP-17 gift wraps between participants and a submission
was a Marmot inner event — and since subgroups moved a child's ChillDKG into the
**parent's Marmot room** that separation is thinner than it was. None of the three
colliding nip30303 kinds has a producer (`TranslationEvent` has an inbound arm and
nothing that builds one; the contributor lists have neither), which is the only
reason this is a latent bug rather than a live one.

So: do not extend the 303xx nip30303 band, and do not fill the 30317–30319 gap,
which is the ceremony family's growth room. Projects take the three slots after
the highest kind in use:

```
30330  ProjectEvent
30331  ProjectArtifactVersionEvent
30332  ProjectTranslationArtifactVersionEvent
```

Worth doing in passing, in Phase 0, because it costs a comment and this document
is the only place anyone will look: a note on `DkgRitualEvents` and on
`SubmissionEvent` saying the two bands overlap and which side has no producer. A
collision nobody has written down is one somebody resolves by moving the wrong
family.

## One event per membership, not one list per project

Two shapes are available for "the versions in this project".

**A list event per project**, replaceable, carrying every member as a tag. One
event to sign when membership changes, and removal is free — the new list simply
omits it.

**An event per membership**, which is what the server's schema describes and what
every other entity in this family is. Append-only; removal needs something extra.

Take the second, for three reasons. The first is that every nip30303 entity here
is its own event and its own row, and the *only* list-shaped kinds in the family —
the three contributor lists, 30305/30307/30310 — are precisely the ones that were
never made to work. `applyInnerEvent` has no arm that writes a row for any of
them, `MantraTranslationArtifactVersionContributor` has a companion full of
`TODO`s assembling an event out of empty strings, and the chronicle leaves all
three out on the grounds that chronicling them "would cost bytes and restore
nothing". The list shape in this codebase is a shape that has failed twice.

The second is batch signing. A project with its first contents is a natural batch
— the project as lead, its memberships as dependents, each carrying an id that is
a hash over the group's key at the room's path and so cannot be known by the
screen that proposes it. That is exactly `proposeSigningBatch(lead, dependents)`,
and exactly what an artifact and its initial version already do. A list event
would have to be signed *after* the project, in a second ceremony, because it has
to name the project's id.

The third is that a membership is a thing a group decided, and one quorum per
decision is the grain this app signs at. A replaceable list makes "we added
Setswana to the project" and "we removed Sesotho from it" the same event, and the
room's record of the first becomes unreadable once the second lands.

The cost is removal, and it is deferred to Phase 8 rather than solved now —
see [What this does not do](#what-this-does-not-do).

## The scope fork: one room, or several

This is the decision that changes the shape of the plan, and it should be made
before Phase 1 rather than discovered in Phase 5.

Every foreign key on every `Mantra*` entity today points inside one room. A
translation names an artifact version in its room; a chapter names a version in
its room; nothing names anything a member of this room might not hold. A project
is the first entity with an obvious reason to cross that line: a group translating
into Setswana may well want the Sesotho translation that another group produced
counted as part of the same project.

**Phase 1 through 7 assume one room.** `projectId`, `artifactVersionId` and
`translationArtifactVersionId` are real foreign keys with `onDelete = CASCADE`,
like every sibling. A proposal naming a version this room does not hold is
refused at apply time, which is the same refusal `MantraTranslationArtifactVersion`
already gets for free from its FK.

**Cross-room is Phase 9, and it is not a small change.** The precedent is
`parentChatRoomId` on `ChatRoom` and `GroupKeyState`, which is deliberately *not*
a foreign key: a subgroup member need never have been in the parent, so a
dangling value is the normal state rather than an error. A cross-room project
membership is in the same position, and so it needs:

- the FK dropped, and the apply arm's "do we hold it" check replaced with
  something that stores the reference unresolved,
- a way to name a version in another room at all — a room id beside the event id,
  since an event id alone is unresolvable,
- a UI that can show a member it cannot resolve, honestly, without looking broken,
- and an answer to what the group's signature means over a reference it cannot
  check. It means the group asserts the membership; it does not vouch for the
  thing named, which no amount of signing can.

Do not design Phases 1–7 to be cross-room-ready. The FK is load-bearing in Phase
1 (it is what makes a bad proposal fail loudly) and taking it out later is one
migration, which is cheaper than carrying an unverifiable column through seven
phases of a feature that does not use it.

## Phase 0 — the decisions, written down

No code. Settle, in this document:

- the three kind numbers, and the collision note described above;
- one-room or cross-room (recommended: one room, Phase 9 for the rest);
- that the creator is a `creator` tag of its own and the owners are
  `ContributorTag`s with an owner role, rather than columns filled from local
  knowledge, and that the owner set is never empty and defaults to the room's own
  key;
- that `ROLE_OWNER` reaches `KNOWN_ROLES` in the same commit as the first event
  that emits it — Phase 1's first line, and the one omission in this plan that
  fails without a word anywhere;
- that campaign is out of scope entirely — no model, no column, no reader;
- **the schema version number.** The database is at v20 and the Curated fork
  shares this history and allocates version numbers out of the same space — the
  record of the last three pulls says v20 on both sides. Whoever lands first
  takes 21; check the Curare clone before claiming it, because two trees that
  both call their next migration 21 is a merge nobody can fix afterwards.

## Phase 1 — three events and their tags

New, under `press.mantra.compose.nostr.nip30303`:

```
ProjectEvent.kt                            30330
ProjectArtifactVersionEvent.kt             30331
ProjectTranslationArtifactVersionEvent.kt  30332
tags/ProjectIdTag.kt
tags/CreatorTag.kt                         "creator"
```

**First, and in this same commit**, `tags/ContributorTag.kt` learns the owner role:

```kotlin
const val ROLE_OWNER = "owner"

private val KNOWN_ROLES = setOf(
    ROLE_TRANSLATOR, ROLE_PROOF_READER, ROLE_EDITOR, ROLE_OWNER,
)
```

Both halves. A role in the constants and not in the set parses back as `null`
while staying inside the signed event id, so the project lands with no owners and
nothing is logged — see [why that is
silent](#role_owner-has-to-reach-known_roles-in-the-same-commit). Extend the
comment above `KNOWN_ROLES` to say that the set has now diverged from NIP-F4's
host/cohost/editor by four rather than three.

No `ROLE_CREATOR`: the creator is not a contributor tag.

`CreatorTag` is `NameTag`'s shape with one extra check, since it holds a key
rather than free text:

```kotlin
class CreatorTag(val pubKey: HexKey) {
    companion object {
        const val TAG_NAME = "creator"

        fun parse(tag: Array<String>): CreatorTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }
            return CreatorTag(tag[1])
        }

        fun assemble(pubKey: HexKey): Array<String> = arrayOf(TAG_NAME, pubKey)
    }
}
```

The length check is the one guard that matters: without it a malformed value
parses, lands on the row, and names nobody. `ContributorTag` has the same check for
the same reason, which is what makes an owner's key safe to take from a form.

`ProjectIdTag` is an `EventReference` tag, copied from `ArtifactIdTag` exactly —
the `ensure(tag.has(1))`, the `tag[1].length == 64`, the optional relay hint at
index 2 and pubkey at 3. `ArtifactVersionIdTag` and
`TranslationArtifactVersionIdTag` already exist and are what the two membership
events point with, so no other tag is new. `NameTag` already exists.

`ProjectEvent`:

```kotlin
const val KIND = 30330
const val ALT_DESCRIPTION = "Project"

fun name() = tags.firstNotNullOfOrNull(NameTag::parse)?.name

fun creator() = tags.firstNotNullOfOrNull(CreatorTag::parse)?.pubKey

fun owners() = tags.mapNotNull(ContributorTag::parse)
    .filter { it.role == ContributorTag.ROLE_OWNER }
    .map { it.pubKey }

fun build(
    name: String,
    description: String,
    creatorPublicKey: HexKey,
    ownerPublicKeys: Set<HexKey>,
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<ProjectEvent>.() -> Unit = {},
) = eventTemplate(KIND, description, createdAt) {
    require(ownerPublicKeys.isNotEmpty()) { "A project has at least one owner" }

    alt(ALT_DESCRIPTION)
    addUnique(NameTag.assemble(name))
    addUnique(CreatorTag.assemble(creatorPublicKey))
    // Sorted, because the id is a hash over the tags as emitted and a form hands
    // its selection over in no particular order. addUniqueValueIfNew, not
    // addUnique, which would keep one p-tag of the several.
    ownerPublicKeys.sorted().forEach {
        addUniqueValueIfNew(ContributorTag.assemble(it, ContributorTag.ROLE_OWNER))
    }
    initializer()
}
```

Content is the description. Name and creator are single-value tags, so `addUnique`
as every sibling does; the owners are a set, so `addUniqueValueIfNew` in sorted
order. `owners()` filters on the role rather than taking every `p` tag, which is
true today and one future role away from not being.

A `Set` rather than a `List`, and non-empty by contract: the caller always has an
answer, since the room's own key is the default, and an empty set would make "owned
by us" and "owner unstated" the same event.

Tag order is fixed and starts with `alt`, because the id is a hash over the tags and
anything that rebuilds this event has to emit them in the same order or produce an
event the signature does not cover. For the owners that includes the order among
themselves, which the sort fixes.

The two membership events carry two id tags and an empty content. `build` takes
both ids:

```kotlin
fun build(
    projectId: HexKey,
    artifactVersionId: HexKey,
    createdAt: Long = TimeUtils.now(),
    initializer: TagArrayBuilder<ProjectArtifactVersionEvent>.() -> Unit = {},
) = eventTemplate(KIND, "", createdAt) {
    alt(ALT_DESCRIPTION)
    addUnique(ProjectIdTag.assemble(projectId))
    addUnique(ArtifactVersionIdTag.assemble(artifactVersionId))
    initializer()
}
```

Note `TagArrayBuilder<ProjectArtifactVersionEvent>` — the real type. Three events
in this package take `TagArrayBuilder<ArtifactEvent>` by copy-paste, which is a
phantom generic that happens to compile; do not add a fourth.

**No creator tag on a membership**, though the server's schema has `creatorId` on
both join models. Two reasons. A membership is an act rather than a work — "this
version is part of this project" is a fact about the project, and who moved it
there is a fact about process, which the `FrostSigningSession` already records. And
a membership proposed in the founding batch has the same creator as the project it
arrives with, so the tag would be redundant in the common case and ambiguous in the
other, where it would be the first place in the app two different people are named
as the creator of one decision.

If it turns out to be wanted, the cost is a nullable column and one `add` — the
tag parses on an event that lacks it, and a row written without it reads back null.
Nothing in Phases 2 through 7 forecloses it.

Also in this phase, and the reason it is a phase of its own: a `membershipsOf`
companion on each membership event, the counterpart to
`ArtifactVersionEvent.initialVersionOf`. It takes the signed `project` event and
the list of version ids the form collected, and returns the templates — borrowing
the project's own `createdAt`, so a project and its first contents read as one act
rather than four events that share a session.

**Tests** (`commonTest`):

- a `RumorIdAgreementTest` case per kind, in the shape the five existing ones have
  — build the template, hash it the way `MantraDao.rumorOf` does, and assert the
  row's id agrees;
- a `ContributorTag` case asserting `ROLE_OWNER` survives `parse`. **This is the
  test that catches the silent drop**, it is three lines, and without it the only
  symptom is projects with no owners;
- a `CreatorTag` case: a well-formed value parses, a value that is not 64
  characters does not;
- a `ProjectEvent` case with **three owners**, asserting all three come back and in
  sorted order — the failure mode of `addUnique` is one owner, which looks like a
  form bug rather than a builder bug;
- a `ProjectEvent` case where the creator is also an owner, asserting `creator()`
  and `owners()` both name them. The two live on different tags now, so this is a
  regression test against anyone folding them back together;
- a `ProjectEvent` case asserting `build` rejects an empty owner set.

## Phase 2 — three rows, and schema 20 → 21

New, under `press.mantra.compose.database.model`:

```
MantraProject.kt
MantraProjectArtifactVersion.kt
MantraProjectTranslationArtifactVersion.kt
```

`MantraProject` is `MantraArtifact`'s shape with fewer fields:

```kotlin
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"], childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraProject(
    @PrimaryKey val id: HexKey,
    val publicKey: HexKey,         // the room's key on a signed row
    val name: String,
    val description: String,
    val creatorPublicKey: HexKey,  // off the creator tag, not local knowledge
    /** The owner p-tags, comma-joined and sorted. See [ownerPublicKeySet]. */
    val ownerPublicKeys: String,
    val chatRoomId: String,
    val signature: String,        // empty for a rumor
    val relayHint: String? = null,
    val marmotGroupEventId: HexKey? = null,
    val groupSignedEventId: HexKey? = null,
    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
): OptionalNostrEventEntity, TimestampedEntity
```

with `fromProjectEvent(event, chatRoomId)` and
`fromProjectEventTemplate(template, chatRoomId, userPublicKey)`, the latter
hashing the id through `EventHasher.hashId` so the row on disk and the payload on
the wire are one event.

`creatorPublicKey` and `ownerPublicKeys` are non-null and come **only** from
`event.creator()` and `event.owners()`. `fromProjectEvent` returns null when the
creator is missing or the owner list is empty, in the `?.let` chain
`MantraArtifact.fromArtifactEvent` already uses for its four required fields — an
event missing something it was built to carry has either been truncated or is not
what it claims, and a row that fills the gap from whatever this device happens to
know is the failure the tag exists to prevent.

Never default `creatorPublicKey` to the submitter and never default the owners to
`chatRoomId`; the room's key is the *form's* default, which is a different thing
and belongs in Phase 7. An empty owner list here is the signature of `ROLE_OWNER`
missing from `KNOWN_ROLES`, so the refusal is also the alarm for that bug — which
is the one argument for refusing rather than tolerating it.

**Owners are one column, not a fourth table.** `DkgSession.participantPublicKeys`
is the precedent: a comma-joined, sorted string with a set accessor beside it, and
the reason it sorts — "so two devices that assembled the set in different orders
store the same string" — holds here too.

```kotlin
/** The project's owners, or an empty set on a row that somehow has none. */
fun ownerPublicKeySet(): Set<HexKey> = ownerPublicKeys
    .split(",")
    .mapNotNull { key -> key.trim().takeIf { it.isNotEmpty() } }
    .toSet()
```

A join table would buy querying by owner, which no screen does — Phase 6 says why
— and cost a table, a DAO, a cascade and three more lines of apply. Revisit it
when something needs "every project this key owns" across rooms, which is Phase 9's
territory anyway.

The membership rows add the two foreign keys and nothing else. Both get an index
on `projectId`, because every read of them is "this project's contents" — the same
reason v16 added one on `ChatMessage.chatRoomId`.

**No `toProjectEvent()`.** The eight `toXEvent()` methods exist for one purpose:
rebuilding events for a chronicle in rooms whose work predates `GroupSignedEvent`
(schema v13). They are deprecated, there is a removal checklist in
`docs/member-chronicle.md`, and each one drags a case of
`ChronicleRoundTripTest` behind it. Projects post-date v13 by seven schema
versions, so every project this app will ever hold is on file as a signed event
and `signedEventsOf` finds it. Adding three more rebuilds would be adding to a
mechanism being retired, to serve rows that cannot exist.

**Schema.** Three tables, nothing existing changes shape, so this is an
`AutoMigration(from = 20, to = 21)` entry with a comment in the style of the other
nineteen, plus three entities in `@Database`, three `abstract fun`s on
`MantraDatabase`, and three DAOs.

The DAOs are `MantraDialectDao`'s shape — an interface, `@Upsert`, and the queries
each screen needs:

```kotlin
@Query("SELECT * FROM MantraProject WHERE chatRoomId = :chatRoomId ORDER BY name")
suspend fun getProjectsByChatRoomId(chatRoomId: String): List<MantraProject>

@Query("SELECT * FROM MantraProject WHERE id = :id")
suspend fun getProjectById(id: String): MantraProject?
```

and, on the membership DAOs, `getByProjectId` and the inverse
`getProjectsByArtifactVersionId` — the inverse is what lets an artifact's own
screen say which projects it is part of, in Phase 7.

**Tests** (`jvmTest`): a `MantraProjectDaoJvmTest` in the shape of
`DkgSessionDaoJvmTest` — real in-memory Room over `BundledSQLiteDriver` — covering
upsert, the by-room query, and that a membership naming an absent version is
refused by the FK. That last one is the Phase 1 scope decision made testable.

## Phase 3 — applying a signed project

Three arms in `ChatMessage.applyInnerEvent`, which is where every nip30303 kind
lands whether it arrived over the wire or was signed locally — `FrostSigningManager`
reuses this dispatch rather than repeating it.

`ProjectEvent.KIND` writes the row and a chat line:

```kotlin
ProjectEvent.KIND -> {
    MantraProject.fromProjectEvent(
        projectEvent = ProjectEvent(event.id, event.pubKey, event.createdAt,
                                    event.tags, event.content, event.sig),
        chatRoomId = groupId,
    )?.let { project ->
        database.mantraProjectDao().upsert(
            project.copy(
                marmotGroupEventId = marmotGroupEventId,
                groupSignedEventId = groupSignedEventId,
            )
        )

        ChatMessage(
            giftWrapPayloadId = null,
            messageType = "project",
            marmotGroupEventId = marmotGroupEventId,
            marmotInnerEventId = marmotInnerEventId,
            senderPublicKey = senderPublicKey,
            isUserMessage = isUserMessage,
            chatRoomId = groupId,
            createdAt = createdAt,
            content = "Started the ${project.name} project",
        )

        // The line names the project and not its creator. `senderPublicKey` is
        // the room's coordinator on a group-signed row, so a line saying "X
        // started" would be the transcript's own guess; the creator and the
        // owners are on the row, and the detail screen is where they are shown.
    }
}
```

The two membership arms write their row and **return null** — no line of their
own. This is the convention the whole family follows: a version writes none
beside its artifact, a chunk none beside its chapter, because they arrive as the
later items of a batch whose lead has already said what happened. A membership
added on its own, later, is not silent either: `FrostSigningManager.announce`
already writes "The group signed …" when a session completes, which is the line
that covers it. Three new strings for a transcript the signing session is already
narrating would be three places for the same event to be described differently.

Both membership arms must tolerate the version not being here. The FK will refuse
the insert; catch it, log it, and return null rather than letting it escape —
`applySignedEvent` swallows throwables for a reason (the signature is valid
whether or not this device could write the row down) but a refusal that reaches it
is reported as a failed apply on a session that succeeded.

**Tests** (`jvmTest`): a `ProjectApplyJvmTest` in the shape of
`TranslationChunkApplyJvmTest` — apply a project with three owners and assert
`creatorPublicKey` and `ownerPublicKeySet()` are what the event carried; apply a
project event with the `creator` tag stripped and assert **no row is written**;
apply one whose owner tags all carry an unknown role and assert the same, since
that is what a missing `KNOWN_ROLES` entry looks like from here; apply a
membership, assert the row; apply a membership whose version is absent and assert
the project survives.

## Phase 4 — asking the group

`ProposedEvent.summarize` gets three branches, or members are asked to sign
"Event of kind 30330".

```kotlin
ProjectEvent.KIND -> Summary(
    label = "New project",
    detail = ProjectEvent(/* … */).let { project ->
        listOfNotNull(
            project.name(),
            event.content.takeIf { it.isNotBlank() },
            // Shown because signing this is what makes the group's attestation
            // that X started it. A member who cannot see the claim cannot be
            // said to have agreed to it -- see the creator note above.
            project.creator()?.take(12)?.let { "started by $it" },
            // Only the owners that are not this room. "owned by us" is the
            // common case and saying it every time trains members not to read
            // the line that matters when somebody else is on it.
            project.owners()
                .filter { it != localChatRoom.chatRoom.id }
                .takeIf { it.isNotEmpty() }
                ?.let { others ->
                    "owned by " + others.joinToString(", ") { it.take(12) }
                },
        ).joinToString(" · ")
    }
)
```

The owner line needs the room's id, which `summarize` does not have: it is a pure
function over one event, by design, so that the signing screen and the proposal
list cannot drift into two names for one thing. Two ways out, and they are not
equally good. Threading the room id through `summarize` is three call sites and no
new concept, but it is the start of passing context into a function whose value is
that it has none. Omitting the comparison and listing every owner is honest and,
with the room always among them, noisy on every single project.

Take the first, and take it *as* the resolver this phase already needs — the two
problems are one problem. A `summarize(event, resolve)` where `resolve` turns a
pubkey or an event id into a name answers the owner question, the creator question
(`started by a4f1be55d0c3` is barely better than nothing; `started by Kgothatso`
is the point) and the membership question below, all in one change. Both call
sites have a database behind them. Design it once, here, rather than three times.

The two membership branches are the honest problem in this phase. `summarize` is a
pure function over one event with no database — deliberately, so that the signing
screen and the room's proposal list cannot drift into two names for one thing —
and a membership event carries two 64-character ids and nothing else. The best it
can do is:

```kotlin
ProjectArtifactVersionEvent.KIND -> Summary(
    label = "A source version joins the project",
    detail = ProjectArtifactVersionEvent(/* … */)
        .artifactVersionId()?.take(12) ?: "Unknown version"
)
```

which is a member being asked to sign a truncated hash. It is tolerable **only**
because of the batch: a project and its first contents are proposed together, the
project's own line names the project and says what it is, and the memberships read
as its continuation — the same way "Version of the artifact" reads as the second
line of a batch of two.

It stops being tolerable in Phase 7's "add an existing version to an existing
project", where the membership is the whole of the proposal — "sign a4f1be55d0c3"
is a request no member can evaluate. The resolver described above is what fixes
this one too, and it is why that design belongs in this phase rather than a later
one: three summaries want the same lookup, and the owner comparison wants it on a
line where getting it wrong is a member agreeing to something they could not read.

**Tests** (`commonTest`): `ProposedEventTest` cases per branch, plus one asserting
a project whose only owner *is* the room says nothing about owners, and one with
an outside co-owner that names them and not the room. The second is the case the
plural exists for, and the one a member most needs to read before signing.

## Phase 5 — the chronicle

Three entries appended to `ChronicleEvent.APPLY_ORDER`:

```kotlin
private val APPLY_ORDER: List<Kind> = listOf(
    DialectEvent.KIND,
    ArtifactEvent.KIND,
    ArtifactVersionEvent.KIND,
    ChapterEvent.KIND,
    ChunkEvent.KIND,
    TranslationArtifactVersionEvent.KIND,
    TranslationChapterEvent.KIND,
    TranslationChunkEvent.KIND,
    ProjectEvent.KIND,                              // new
    ProjectArtifactVersionEvent.KIND,               // new
    ProjectTranslationArtifactVersionEvent.KIND,    // new
)
```

Order is the whole content of this phase. `APPLY_ORDER` is dependency order and a
page applies front to back, so a membership must sort after *both* the project and
the version it names. Appending gets that right: the memberships are last, and
everything they can name is already in the list. `CHRONICLABLE_KINDS`,
`applyRank`, `inApplyOrder` and `isChroniclable` are all derived from this one
list and need no change.

Projects are chroniclable where the contributor lists are not, and for the
opposite of their reason: these have an apply arm that writes a row, and they are
signed rather than submitted, so a receiver can check them. A late member who
gets a room's chronicle and not its projects gets the whole library and no account
of what any of it is for.

`rebuiltEventsOf` — the deprecated walk — is **not** touched, per Phase 2.

**Tests** (`commonTest` / `jvmTest`): a `ChronicleEventTest` case asserting the
three ranks, specifically that each membership outranks the project and the
version kind it names; a `ChronicleAssemblyJvmTest` case asserting a room with a
project chronicles it.

## Phase 6 — the repository

`MantraRepository` gains, with `DatabaseMantraRepository` implementing and `NO_OP`
returning empty:

```kotlin
suspend fun getProjects(chatRoomId: String): List<MantraProject>
suspend fun getProject(id: String): MantraProject?
suspend fun getProjectArtifactVersions(projectId: String): List<MantraArtifactVersion>
suspend fun getProjectTranslationArtifactVersions(projectId: String): List<MantraTranslationArtifactVersion>
suspend fun getProjectsForArtifactVersion(artifactVersionId: String): List<MantraProject>
```

No query by creator or owner. Both are columns on the row and a screen that wants
them has the row; a `getProjectsOwnedBy` would be a query with one caller and an
index to go with it, and the room's project list is short. Add it when a screen
asks, not before.

The two contents queries return the *versions*, not the join rows: no screen wants
a join row, and resolving it in the repository is one query there instead of an N+1
in a view model. Room does this with a join; both are indexed by Phase 2.

**Nothing goes in `MantraDao`.** Its `addDialect` / `addArtifactVersion` /
`rumorOf` / `submitToGroup` path is the old submit flow, reached now only from
`MantraDaoJvmTest`, and every entity a member creates is proposed for signing
instead. An `addProject` there would be a second way to create a project that
bypasses the quorum, which is the thing this feature is for.

## Phase 7 — the screens

Five files per screen, in the shape every screen in this app has: a
`@Serializable data class` route, a sealed `UIState` (Loading / Error / Loaded), a
view model with a `factory`, the composable, and a `composable<Route>` entry in
`MantraNavHost`.

**`ChatRoomDetailScreen` gets a projects section**, between the library and the
dialects, copying the artifacts section exactly: a `labelMedium` heading, an empty
line when there are none, a `Card` + `ListItem` per project navigating to
`ProjectDetailRoute`, and a `TextButton` to `AddProjectRoute`.
`ChatRoomDetailUIState.Loaded` gains `projects: List<MantraProject> = emptyList()`
and the view model's `initiate` loads them.

**`AddProjectScreen` / `AddProjectViewModel` / `AddProjectRoute`.** Name and
description fields, a multi-select of the room's artifact versions and a
multi-select of its translations, an owner, and one primary action. The view model
is `AddArtifactViewModel`'s shape, including the parts that are not obvious:

- the **creator is `activeUserPublicKey`**, not a field. It is who is filling the
  form in, the view model already has it, and a picker would only let somebody
  credit the wrong person;
- the **owners are a list, pre-filled with the room's own id** and labelled as this
  group, with an add control and a remove on each. Most projects never touch it,
  and that is the point of pre-filling rather than hiding: a group that *is* making
  something for somebody else needs the question asked, and a group that is not
  needs to see the answer it is giving. Each entry takes a pubkey or an npub;
  validate to 64 hex on entry, since `ContributorTag.parse` drops a tag whose value
  is not that length and the owner would vanish between the form and the signature.
  The list cannot be emptied — `build` requires one and the last remove is
  disabled rather than allowed and then refused;
- `isActionPending`, guarding the double tap;
- `canSign` on `UIState.Loaded` from `frostSigningRepository.canSign`, with the
  FAB borrowing `ButtonDefaults.buttonColors().disabled*` and
  `Modifier.semantics { disabled() }` when it cannot;
- `proposeSigningBatch(lead = projectTemplate, dependents = …)`, where
  `dependents` reads the signed project's id and returns both membership lists.
  The project's id is a hash over the group's key at the room's path, which this
  screen does not know and should not;
- on success, navigate to `FrostSigningRoute(session.id)` with
  `popUpTo<AddProjectRoute> { inclusive = true }`.

A project with no contents is allowed — unlike an artifact with no version, which
is inert because nothing can attach to it, an empty project is a name waiting for
work and Phase 7's second flow is how work reaches it.

**`ProjectDetailScreen` / `ProjectDetailViewModel` / `ProjectDetailRoute`.** Name,
description, creator, owner, the source versions, the translations, and two add
buttons. Lists load once in `initiate()`; to reflect a just-signed change, navigate
to a fresh `ProjectDetailRoute` with `popUpTo<ThatRoute> { inclusive = true }`.
`DetailRow` is the shared label/value widget.

Creator is a `DetailRow` and the owners are a short list, both showing the
**profile** rather than the hex — resolve through `ProfileDao` and fall back to a
truncated npub, which is what the rest of the app does with a key it has no profile
for. An owner entry that is the room's own key reads "this group" rather than
printing the room id back at a member standing inside it, and sorts first, so the
common single-owner case is one unremarkable line.

**`AddToProjectScreen`** — pick a version, propose one membership. **This is the
flow that needs Phase 4's resolver**, because here the membership is the entire
proposal and a truncated hash is not a thing to sign.

Each screen obeys `CLAUDE.md`, and the audit is not optional —
`docs/scripts/m3-audit.sh --check` runs inside `./gradlew check` and fails on a
budget exceeded:

- spacing from `MaterialTheme.spacing`, never a `.dp` literal in a spacing
  position (budget 0);
- colour from a role, never a `Color(0x…)` and effectively never `.copy(alpha=)`
  (budget 0 outside `ui/theme/`);
- every string in `composeResources/values/strings.xml`, **sentence case** —
  "Add project", "No projects in this group", not "Add Project" (budget 0
  title-case strings);
- `NavigateBackButton(onNavigateBack)` in the app bar of all four, since all four
  are pushed and the desktop target has neither a system back nor an edge swipe;
- `Modifier.padding(innerPadding).readableContent()` on each content root;
- all four states said, with `ErrorState(onRetry = …)` and `EmptyState`'s message
  given explicitly;
- `ScreenStateTransition(viewModel.uiState) { … }` wrapping the state `when`, and
  only where the `when` is the composable's whole body;
- icons with a decided `contentDescription` — a real one, or `Decorative` where
  the label is beside it, never a bare `null`.

Around twenty new strings — the six beyond the screens themselves are the owners
field's label, its "this group" value, its add and remove actions, its validation
error, and the creator row's label. Check `grep -c "<string"` before and after; the
catalogue is at 460.

## Phase 8 — removing something from a project

An append-only membership log has no way to say "not any more", and this is the
cost of the Phase 2 shape, paid here rather than designed around.

Two options, and they should be weighed with the schema in front of you rather
than now. A **revocation event** (30333) naming the membership it ends, which is
how a signed log normally retracts and which keeps the room's record of both
decisions — at the price of a fourth kind, a fourth arm, a fourth chronicle rank,
and every read of a project's contents becoming a left-join against revocations.
Or **supersession**, the mechanism `TranslationChunkEvent` already uses: a new
event replaces an old one and the apply arm drops what it replaced, with the
per-kind replace/dedupe rule living in `applyInnerEvent`. That is cheaper and
fits a *correction* better than a *removal*; there is no "nothing" for a
membership to be superseded by.

Lean toward revocation, and note that the chronicle has a stake in whichever is
chosen: `TranslationChunkEvent`'s supersession means a chronicle carries the
group's current answer rather than its drafts, and a revocation means a chronicle
has to carry the revocation too or a late member sees a membership the group
ended.

## Phase 9 — projects across rooms

Described under [the scope fork](#the-scope-fork-one-room-or-several). One
migration dropping two foreign keys, a room id beside each referenced event id, an
apply arm that stores what it cannot resolve, and a UI that can show an unresolved
member without looking broken. Follow `parentChatRoomId`'s precedent — a plain
column, never a foreign key, because a dangling value is the normal state.

## Verifying

```bash
ANDROID_HOME=/home/sigidli/Android/Sdk ./gradlew :composeApp:compileDebugKotlinAndroid
```

```bash
./gradlew :composeApp:jvmTest :composeApp:m3Audit
```

Room-backed tests go in `composeApp/src/jvmTest`, not `commonTest` — the android
unit-test variant's `PlatformDatabaseBuilder` needs a real `Context` and there is
no Robolectric. In a fresh worktree, bootstrap the four nested submodules first.

## Rollout

Phases 0–6 are invisible: new kinds nothing proposes, new tables nothing writes,
new arms nothing reaches. They can land in any order after 0, and each compiles
and tests on its own.

Phase 7 is what turns the feature on, and it is one-way in the sense that
matters: once a group has signed a project, every member's device must understand
kind 30330 to make sense of its room. A member on an older build sees three
unrecognised kinds — no row, no chat line, and `GroupSignedEvent` keeps the event,
so the row appears when they upgrade. That is the same degradation every kind in
this family has had, and it is why the inbound half ships before the screens that
produce anything.

Phase 8 should land close behind 7. A group that can add and cannot remove will
want to remove within a week.

## What this does not do

- **Funding.** No campaign, no pledge, no satoshis, and no column held open for
  one. It is a feature with its own kinds and it needs projects to exist first.
- **Proof of authorship.** The creator tag is the group attesting to a claim the
  proposer wrote; the creator does not sign anything. The `FrostSigningSession`
  records who actually acted, and the two can disagree — which is a fact worth
  having, not a defect to reconcile.
- **An owner that is not a key.** The server's `owner` → `ownerEntity` → `entity`
  chain can name an organization with members, an email and a type. A set of
  pubkeys carries none of that. If an owner needs to be a described thing rather
  than an addressable one, that is an entity feature and it is not this.
- **Changing the owners.** They are tags on the project event, so the owner set is
  fixed at the quorum that signed it. Editing it means a new project event
  superseding the old, which is the same question Phase 8 asks about memberships
  and should be answered with it rather than separately.
- **Creator or owner on a membership.** One tag and one column away, deliberately
  left out — see Phase 1.
- **Removal** until Phase 8, **cross-room** until Phase 9.
- **Project-level approval or progress.** The server has
  `artifactApproval`, `artifactVersionApproval` and
  `translationArtifactVersionApproval`, none of which exists here either. A
  project is a grouping; what a group says *about* the grouping is later work.
