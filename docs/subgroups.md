# Subgroups

A group makes another group, and the child can prove where it came from. This is
how the four ceremonies fit together, what the parent's signature actually
covers, and the one thing a subgroup cannot inherit.

Read [shared-key-derivation.md](./shared-key-derivation.md) and
[shared-key-ceremony.md](./shared-key-ceremony.md) first. Everything below is
built on the property they state — that a room's id *is* the key it signs with —
and on the ceremony they describe, which a subgroup runs again from scratch.

**Not built.** Phases 1-9, one commit each. Written against the code as it stands
at `b50b1762`.

## What a subgroup is

A subgroup is an ordinary Marmot room with an ordinary threshold key, made by an
ordinary ChillDKG ceremony, plus a **birth certificate** — the parent group's
signature over the child's room id — carried on the child's key state.

The key state grows two tags for it, not one, and only the first is an artefact:

| tag | what it is |
|---|---|
| `birth_certificate` | the parent's signed statement, whole. The claim itself |
| `parent_group` | the parent's room id — an **index** into the certificate, which already carries the same value in its own `parent_group` tag and as its author |

The second is redundant by construction and is kept anyway, so a reader can
answer "whose child is this" without parsing an event out of a tag value. What
stops it being a second, weaker source of truth is Phase 3's rule that a state
carrying one without the other, or the two disagreeing, is dropped entirely.
There is no state in which the index is believed and the certificate is not.

That is the whole of the relationship. The child does not derive its key from the
parent's, does not share a share with it, and cannot be signed for by it. What
the certificate buys is a checkable claim, and only that claim:

> The group holding key `P` said, with a quorum, that the room `C` is its child.

Everything else about a subgroup — how it signs, who administers it, what it
holds — is the same as any other robust group in this app, and deliberately so.

### Why the key is fresh rather than derived

`SharedKeyDerivation` can already walk a group's key to any path, and
`m/9420/1/0` would give a child room for free with no ceremony at all. It is the
wrong answer for one reason, stated in that file:

> Anyone who learns one derived private key recovers the threshold key and can
> sign as the group without any quorum at all.

A derived child is the parent wearing a different hat. Its members would be the
parent's members, its quorum the parent's quorum, and the parent's admins would
be able to sign as it. A subgroup exists precisely so that a *different* set of
people — including people the parent does not trust to administer the parent —
can act on their own. A fresh ceremony is what makes the child's authority the
child's.

The cost is that a subgroup is not cheap: four multi-party rounds, every one of
which needs every selected member to show up. That is named again under
[what this does not do](#what-this-does-not-do).

## The order, and why it is forced

```
  1. ChillDKG among the new admins       -> threshold key K, and so C = marmotGroupId(K)
  2. birth certificate, in the parent    -> parent's quorum signs C
  3. key state, among the new admins     -> child's quorum signs "C signs with K", carrying the certificate
  4. the Marmot room                     -> coordinator creates C and welcomes the admins
```

Nothing here is a policy choice. Step 2 needs `C`, which does not exist until
step 1 produces `K`. Step 3 carries the certificate, so it needs step 2. Step 4
is gated on the key state for the same reason `DkgRitualViewModel.createAdminGroup`
is today — a room created before its group has agreed what it signs with is a
room whose founding fact is settled after the founding.

The coordinator is the same person throughout: the parent admin who pressed the
button. They coordinate the child's ceremony, propose the certificate in the
parent, propose the key state in the child, and create the room. ChillDKG and
FROST both treat a coordinator as untrusted, so this buys them nothing but work —
the same bargain the existing ceremony makes.

## Three ceremonies, two quorums, one coordinator

| step | runs in | signed by | transport |
|---|---|---|---|
| ChillDKG | a NIP-17 room over the child's admins | n/a — every participant | gift wraps |
| birth certificate | the **parent** Marmot room | the parent's quorum, as the parent's key | MLS group events |
| key state | the child's NIP-17 ceremony room | the **child's** quorum, as the child's key | gift wraps |

The middle row is the only new shape, and it is not new machinery:
`FrostSigningManager` already runs on both transports and already gates each
signer's approval behind the existing proposal UI. What is new is a kind for it
to carry and a reason for the parent's admins to say yes.

## Informed consent, and what the parent's admins are actually signing

The spec for the certificate is "the hex of the new groupId, signed with the
existing groupId's key". Taken literally that is 32 opaque bytes: a parent admin
would be asked to put the group's signature to a number they cannot check,
produced by a ceremony most of them were not in, on behalf of people they have
only the coordinator's word about.

So the **content** is exactly the new group id, as specified, and the **tags**
carry what makes it checkable. The signature covers both — an event id hashes
over its tags — so nothing is added to the claim by putting it there, only to
what a signer can see before agreeing:

```
kind:    30329
pubkey:  <the parent room's id>            (set by the signing session, not the proposer)
content: <the child's room id, 32-byte hex>
tags:
  ["d",            <the child's room id>]        addressable; newest certificate per child wins
  ["parent_group", <the parent room's id>]
  ["subgroup_key", <the child's threshold key, 33-byte hex>]
  ["frost_path",   "m/9420/0/0"]
  ["p",            <each of the child's admins>]
```

With those, a parent admin's device can check the thing that actually matters
before it signs — `marmotGroupId(subgroup_key, frost_path) == content` — and
`ProposedEvent` can render "a subgroup for Alice, Bob and Carol" rather than a
hash. A coordinator who lies about who is in the child is then lying in a field
the parent's signature covers, which is the difference between a mistake and
evidence.

## Phase 1 — the certificate, and checking one

New package `nostr/subgroup/`, no database, no coroutines — the same shape as
`GroupKeyStateEvent`, and testable the same way.

**`SubgroupBirthCertificateEvent`** at kind **30329**, the next free kind after
`ChronicleRequestEvent` (30328). Sits in the private 303xx range with everything
else that never leaves an encryption.

```kotlin
object SubgroupBirthCertificateEvent {
    val KIND: Kind = 30329

    fun assembleTags(
        subgroupChatRoomId: String,
        parentChatRoomId: String,
        thresholdPublicKey: HexKey,
        adminPublicKeys: List<HexKey>,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    ): Array<Array<String>>

    fun parseSubgroupChatRoomId(tags: Array<Array<String>>): String?
    fun parseParentChatRoomId(tags: Array<Array<String>>): String?
    fun parseThresholdPublicKey(tags: Array<Array<String>>): HexKey?
    fun parsePath(tags: Array<Array<String>>): List<Long>?
    fun parseAdminPublicKeys(tags: Array<Array<String>>): List<HexKey>

    /** Whether [event] is a certificate the room [parentChatRoomId] actually signed for [subgroupChatRoomId]. */
    fun certifies(event: Event, subgroupChatRoomId: String, parentChatRoomId: String): Boolean
}
```

`certifies` is where the whole of the trust in a parent link lives, and it is six
questions, all answerable from the event:

1. the kind is 30329;
2. `content == subgroupChatRoomId`, and the `d` tag agrees with it;
3. the `parent_group` tag is `parentChatRoomId`;
4. `SharedKeyDerivation.marmotGroupId(subgroup_key, frost_path) == content` — the
   id really is that key's room, so a certificate cannot be pointed at a room the
   key does not derive;
5. `GroupKeyStateEvent.isSignedByRoom(event, parentChatRoomId)` — the author is
   the parent's id, the id is the hash of the fields beside it, and the signature
   verifies;
6. everything above inside a `runCatching`, because every input is off the wire.

Point 5 is `GroupKeyStateEvent.isSignedByRoom` used verbatim, not reimplemented.
It already asks "did *this room* sign this", and a room id is a public key here —
that is the whole economy of `docs/member-chronicle.md` and it applies unchanged.

Two tag classes beside it, in `nostr/subgroup/tags/`, matching
`FrostDerivationPathTag`'s shape: `SubgroupParentTag` (`parent_group`) and
`SubgroupKeyTag` (`subgroup_key`). The `d` and `p` tags are quartz's.

**Tests** — `SubgroupBirthCertificateEventTest`, commonTest, pure: a certificate
that verifies; one whose content does not match its `d` tag; one whose id does
not derive from its key; one signed by a different room; one whose signature is
64 bytes of nonsense; one whose key is not a point on the curve.

## Phase 2 — schema 16 → 17

Three nullable columns and nothing else, so Room migrates itself and the entry
joins the list in `MantraDatabase`.

| table | column | filled from | trusted? |
|---|---|---|---|
| `GroupKeyState` | `parentChatRoomId` | the state's parent tag | **yes** — `stateFrom` verified the certificate |
| `GroupKeyState` | `birthCertificateJson` | the state's certificate tag | **yes** — same |
| `ChatRoom` | `parentChatRoomId` | the child's key state, as the room is created or adopted | **yes** |
| `DkgSession` | `parentChatRoomId` | a tag on the ceremony proposal | **no** — a hint for the UI, see Phase 4 |

The trust column is the point of the table. Two of these are written only after a
signature has been checked and one is written from an unauthenticated claim on a
wire message; a column that mixes the two is a column no reader can act on. The
`DkgSession` one is never read for anything but a screen's title.

The whole certificate event is stored as JSON rather than the signature alone.
That is deliberate and is argued in the [appendix](#appendix--what-was-considered-and-rejected):
a signature plus a rule for rebuilding the event it covers is a rule that breaks
silently the first time the event's shape changes.

New DAO reads, all straightforward:

- `GroupKeyStateDao.getByParentChatRoomId(parentChatRoomId)` and an
  `observe` beside it.
- `ChatRoomDao.observeByParentChatRoomId(parentChatRoomId)`.
- `DkgSessionDao.getByParentChatRoomId(parentChatRoomId)`, for resuming a
  ceremony whose room has not been made yet.

## Phase 3 — a key state that names its parent

`GroupKeyStateEvent` gains two optional tags, and `GroupKeyStateManager` gains
the checks that make them mean something.

```
["parent_group",     <the parent room's id>]
["birth_certificate", <the signed 30329 event, as JSON>]
```

`assembleTags` takes an optional `parent: SubgroupParentage?` carrying both, and
emits neither when it is null — so every existing call site and every state
already signed is untouched.

The work is in `stateFrom`, which is where a state earns its row. After the two
checks it already makes — the room rederives from the key, and the group signed
it — a state carrying either new tag has to pass four more:

1. **Both or neither.** A parent tag with no certificate, or a certificate with
   no parent tag, is dropped. A half-claim is not a weaker claim; it is a claim
   with the checkable part removed.
2. The certificate parses as an `Event`.
3. `SubgroupBirthCertificateEvent.certifies(certificate, state.chatRoomId, parentChatRoomId)`
   — the parent signed *this* child, not some other one.
4. The certificate's `subgroup_key` equals the state's own threshold key.
   Redundant with (3) via the derivation, and kept because it costs nothing and
   the two facts are stated in different places.

A failure at any of them **drops the whole state**, rather than keeping it as a
parentless one. A state that claims a parentage it cannot back is not a state
with one field wrong — it is a device asserting a relationship the parent group
never agreed to, and half-believing it is worse than believing none of it.

`GroupKeyStateManager.propose` takes the same optional parentage and passes it
through. `record` and `adopt` carry the two verified values onto the row; they
already funnel through `stateFrom`, so nothing else is needed to keep the row
honest.

**Tests** — added to `GroupKeyStateTest` (commonTest, pure `stateFrom` cases) and
`SignedGroupKeyStateTest` (jvmTest, real FROST signatures end to end): a subgroup
state that verifies; one whose certificate names another child; one whose
certificate is signed by a room that is not the named parent; one carrying a
parent tag and no certificate; one carrying a certificate whose key is not the
state's.

## Phase 4 — where the subgroup's ceremony runs

The child's ChillDKG needs a room whose participant set is exactly the child's
admins, and whose transport is gift wraps. There is already one:
`chatRepository.createNip17ChatRoom` over the selected admins plus the
coordinator. Its id is `ChatRoom.deriveChatRoomId` over the member set, so every
device lands on the same room, and `ChillDkgRitualManager` needs no change at all
— its `broadcast` p-tags come from that room's participants and `acceptProposal`
derives `n` from those same p-tags.

This is the same move `SelectChatRoomTypeViewModel.createNip17ChatRoom` already
makes for a robust group, and the reason step 1 costs almost no new code.

**One tag is added to the ceremony proposal**: `parent_group`, on
`DkgRitualEvents.PROPOSAL` only, parsed by `acceptProposal` onto
`DkgSession.parentChatRoomId`. It authenticates nothing — anyone can claim any
parent — and is read for exactly two things: the ritual screen saying "a subgroup
of Ekklesia" instead of "a shared key ceremony", and the coordinator finding
their way back into a flow they closed the app halfway through. The load-bearing
claim is the certificate, three steps later, and the code should say so where the
column is declared.

### The collision this buys, and it is real

A NIP-17 room's id is a pure function of its members, so **one admin set gets one
ceremony room, forever**. Two consequences, and both need a guard rather than a
comment:

- Selecting an admin set that already holds a completed ceremony returns that
  ceremony (`proposeRitual` hands back anything not `FAILED`), which would make
  the "new" subgroup the old group under a new name — same key, same room id.
- Selecting *every* member of the parent is the pathological case of that: the
  parent's own ceremony room is the room over all its members, so the child would
  come out as the parent itself.

Refused in Phase 8, on both readings, before the button is offered.

### Why not the parent's Marmot room

The obvious alternative is to hold all three steps in the parent room: no sibling
room, no member-derived id, and the collision above disappears. It is a better
design and it is not available yet. What follows is why, because "we did not"
without "and here is the price" is how a decision gets re-litigated every six
months.

**It is one decision, not three.** The certificate already runs in the parent
room and is the only one of the three free to run anywhere. The key state is not:

```kotlin
// GroupKeyStateManager.propose
check(chatRoomId == signingRoomId || key.chatRoomId == signingRoomId)

// FrostSigningManager.signingPath, the one case that cannot be self-checked
if (localChatRoom.chatRoom.mlsGroupState == null && key.chatRoomId == chatRoomId) {
    return SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
}
```

The child's room does not exist, so `chatRoomId == signingRoomId` cannot hold and
the key state has to be signed **where its ceremony ran**. Both guards say so
independently. So the ceremony and the key state move together or not at all, and
the question is only ever "which transport does the child's ChillDKG ride".

**What moving it would cost, mechanically.** Three things, all enumerable:

1. `ChillDkgRitualManager.broadcast` writes only a `GiftWrapPayload`; it needs the
   two-transport shape `FrostSigningManager.broadcast` already has, plus a Marmot
   inbound arm in `NostrDao` beside the one dispatching FROST signing at kind
   30320-30325. The p-tags stay on **both** transports, unlike FROST's — for a
   ceremony the p-tag set *is* the participant set every device derives `n` from,
   and inside MLS encryption naming the participants leaks nothing.
2. `DkgSession.chatRoomId` stops identifying a ceremony. Three queries key on it
   and all three break: `proposeRitual`'s one-ritual-at-a-time guard would return
   the parent's *own* completed session and refuse to open a subgroup ceremony at
   all; `observeLatestSessionForChatRoom` would show the child's ceremony on the
   parent's shared-key screen; and `completedKey`'s third fallback would resolve
   the **child's** key for the parent room, for any parent member holding no key
   state row — which is every member welcomed after the parent's own ceremony.
   Fixable with a `subjectChatRoomId` column and three scoped queries, but that
   third one is a load-bearing fallback and the failure is silent.
3. `signingPath`'s special case is gated on `mlsGroupState == null` and would have
   to admit a Marmot room. Still device-local — both inputs are read from this
   device's own database, so a proposer still chooses nothing — but it widens the
   one function whose contract is that the path can never come off a proposal.

**Why not, even after all that.** `docs/mls-skipped-keys.md` names
`ChillDkgRitualManager.proposeRitual` in its table of reliable triggers —
"proposal, then the host key" — and today that costs nothing, because the ritual
runs on gift wraps and the bug is in MLS. Moving it onto group events puts a
ceremony on the one transport where a message arriving a moment late is dropped
for good.

A DKG is the worst possible payload for that. It **cannot finish until every
participant takes part**, so one lost round-1 message stalls it permanently for
everybody, and the ritual's own resume machinery cannot help: relays redeliver
gift wraps and `replayStoredMessages` re-feeds the backlog, while a group event
whose inner event never materialised leaves a stored `MarmotGroupEvent` row and
nothing to replay. FROST signing survives the same transport only because it
needs `t` of `n` and routes around the signer whose nonce was lost.

`MlsGroupCache` covers the burst-in-one-sync case and nothing else: a restart
loses the skipped keys, and any other writer in the room — a chat message, a
member being added — invalidates the cache mid-ceremony.

**So: revisit when the quartz fix lands.** Once `saveState`/`restore` carry the
skipped keys, the parent room is strictly the better home for all three steps,
the collision above stops being a refusal and becomes a non-issue, and
`DkgSession.parentChatRoomId` stops being needed at all. Until then the child's
ceremony rides the transport that tolerates reordering, and pays for it with one
sibling room and one refusal.

## Phase 5 — the manager, and the four steps in order

New `managers/SubgroupManager.kt`, holding the orchestration that does not belong
to any one of the three existing managers. It calls them; it does not
reimplement them.

```kotlin
object SubgroupManager {
    /** The NIP-17 room a ceremony for these admins would run in. Pure. */
    fun ceremonyRoomIdFor(adminPublicKeys: Set<HexKey>, coordinator: HexKey): String

    /** Step 1: stand up the ceremony room and open the ritual in it. */
    suspend fun openCeremony(...): DkgSession

    /** Step 2: ask the parent's quorum to certify the child. Runs in the parent room. */
    suspend fun proposeBirthCertificate(
        database: MantraDatabase,
        parentRoom: LocalChatRoom,
        userPublicKey: HexKey,
        key: DkgSession,
        adminPublicKeys: List<HexKey>,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    ): FrostSigningSession

    /** The newest certificate for this child that actually certifies it, or null. */
    suspend fun certificateFor(
        database: MantraDatabase,
        subgroupChatRoomId: String,
        parentChatRoomId: String
    ): GroupSignedEvent?

    /** Step 3: ask the child's quorum to sign its key state, carrying the certificate. */
    suspend fun proposeSubgroupKeyState(...): FrostSigningSession

    /** Everything the parent has certified, whether or not the room exists here. */
    suspend fun subgroupsOf(database: MantraDatabase, parentChatRoomId: String): List<Subgroup>

    /** Files a certificate that arrived, or drops it and says why. */
    suspend fun record(database: MantraDatabase, chatRoomId: String, innerEvent: Event): GroupSignedEvent?
}
```

`proposeBirthCertificate` is one `FrostSigningManager.proposeSigning` call in the
parent room with `key = null` — the parent's key is resolved from the parent room
the way every other signature in that room is, and a caller naming a ceremony
here would be a caller choosing what the group signs as. A session of one, never
batched, for the same reason `GroupKeyStateManager.propose` gives: a batch is only
as available as its worst item, and this one is a precondition of everything
after it.

`proposeSubgroupKeyState` is `GroupKeyStateManager.propose` with the parentage
filled in, and it must refuse to run without a certificate that passes
`certifies`. Proposing a state the coordinator's own device would drop is not a
mistake worth letting the group discover by signing it.

`subgroupsOf` reads the parent's `GroupSignedEvent` rows of kind 30329, filters
them through `certifies`, and joins each to the local `ChatRoom` if there is one.
No table of subgroups: the certificates *are* the record, they are the group's own
signed statement, and every device in the parent room already has them —
`FrostSigningManager.complete` files a `GroupSignedEvent` on every device that
followed the session, not only on the signers'.

### Two dispatch arms, both easy to forget

`ChatMessage.applyInnerEvent` needs a **30329** arm. Without one the certificate
falls to `else ->` and every parent member gets a raw-JSON chat bubble; that arm
is the failure mode `docs/member-chronicle.md` names for an old build meeting a
new kind, and here it is avoidable. The arm delegates to `SubgroupManager.record`,
which verifies against the room it arrived in — the event reaches this path both
from `FrostSigningManager.applySignedEvent`, where it is already checked, and
from an arriving inner event, where it is not, and the arm cannot tell which.

Unlike the key-state arm it **does** write a chat line. A key state is standing
state whose session already wrote the transcript; a subgroup being born is
something that happened, and it happened on behalf of parent members who are not
in the child and will otherwise never be told.

`ProposedEvent.summaryOf` needs a 30329 arm too, or the parent's admins approve
"Event of kind 30329" with the JSON underneath. It should read as the members and
the path — the id is the thing being decided and is shown by the screen anyway.

### Not chroniclable

30329 does **not** join `ChronicleEvent.CHRONICLABLE_KINDS`, for the reason that
list gives for excluding `GroupKeyStateEvent`: verification admits an event on the
strength of the group's signature, so any chroniclable kind is replayable by any
member forever. A member added to the parent after a subgroup was made therefore
sees an empty subgroup list — named under
[what this does not do](#what-this-does-not-do), with the argument for changing
it later.

## Phase 6 — creating the room, once, for both flows

`DkgRitualViewModel.createAdminGroup` is 120 lines of Marmot room creation living
in a ViewModel: resolve every member's key package or refuse, build
`MarmotGroupData` with the admin list baked into epoch 0, `MlsGroup.create`,
`getOrCreateChatRoom`, adopt the key state, add the members. The subgroup needs
all of it and differs in four values.

So it moves, unchanged in behaviour, to `managers/MarmotGroupCreation.kt`:

```kotlin
suspend fun create(
    database: MantraDatabase,
    chatRepository: ChatRepository,
    groupId: String,
    name: String,
    purpose: String,
    adminPublicKeys: Set<HexKey>,
    userPublicKey: HexKey,
    keyPair: KeyPair,
    path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
    parentChatRoomId: String? = null
): Outcome   // Created(room, notAdded) | BlockedOn(missingKeyPackages) | Existing(room) | Failed(reason)
```

Both call sites then read as four values and a result. The rules that are already
right stay right for the subgroup for free, and they are the ones worth not
re-deriving:

- **Every key package before anything exists.** The id is derived, so there is
  exactly one room per key at this path; a half-created one occupies that address
  permanently and there is no second id to retry with. Better to create nothing
  and name who is missing.
- **`adminPubkeys` baked into epoch 0**, so a member welcomed later gets a
  populated group rather than chasing a bootstrap commit that predates them.
- **`adopt` before the members are added**, because filing the key state is local
  and certain and adding members is neither.

`parentChatRoomId` is the one genuinely new parameter, written onto the
`ChatRoom` row. The subgroup's description carries the path through
`SharedKeyDerivation.describe`, exactly as the admin room's does — MIP-01 has no
field for either, and the path is what rebuilds the `TweakCache`.

## Phase 7 — the UI

Four pieces, three of them small. Every one of them is subject to `./gradlew
:composeApp:m3Audit`: strings in `strings.xml` in sentence case, spacing from
`MaterialTheme.spacing`, colour from a role, `minimumInteractiveComponentSize`
on anything clickable that is not an `IconButton`, and a decided
`contentDescription`.

**A subgroups section on `ChatRoomDetailScreen`**, between members and the
danger zone, listing `SubgroupManager.subgroupsOf` with one of three supporting
lines per row — *certified, not yet created* / *created, you are not a member* /
the room's own subject if this device is in it — and an "add subgroup" `TextButton`
above them in the shape the existing "invite new member" one has. The row's tap
target navigates into the child's room if this device has it and to the
certificate's `NostrEventDetailRoute` if it does not.

Offered only where it can work: this device is an admin of the parent
(`Participant.adminAt != null`), the parent can sign
(`FrostSigningManager.canSign`), and the parent is a Marmot room. Hidden, not
disabled, in the same way the shared-key entry is hidden on an MLS room.

**A parent row on the child's detail screen**, read from the verified
`GroupKeyState.parentChatRoomId`, sitting directly under the signing-key card —
where a member looks to find out what the room is.

**`SelectSubgroupAdminsScreen`** and its route and ViewModel, modelled on
`SelectChatRoomMembersScreen`. The pool is the parent's own participants, admins
and non-admins alike, with the parent's admins marked rather than filtered — the
point of a subgroup is that it can be run by people the parent does not let run
the parent.

**Three admins in total, the coordinator included**, so the picker asks for at
least **two** others. Three is `ChatRoomType.MINIMUM_ROBUST_GROUP_SIZE`, and it
is the same floor for the same reason it states: below three a quorum is not a
check, because two admins means every decision needs both of them and one means
the coordinator is deciding alone. The coordinator counts because they are a
ChillDKG participant by construction — they hold a share whether or not anybody
ticked their name — so the screen shows their own row selected and locked rather
than leaving them out of the count the confirm button is testing.

**The threshold is the coordinator's to set, on this screen, and only here.**
A stepper over `ChatRoomType.quorumRange(adminCount)` — `2..3` for the smallest
subgroup — starting at `ChatRoomType.defaultQuorum(adminCount)`, which is `2` of
`3`. It has to be settled before anything is published, and that is a protocol
fact rather than a layout preference: ChillDKG hashes the threshold and the host
keys into the session identity, so `t` is fixed the moment the proposal goes out
and a group that disagrees about it gets no key at all rather than a weak one.
There is no later screen where it could be changed, and the plan should not
pretend otherwise.

It is also the one value the coordinator picks *for other people*. Consent is not
lost by that: the threshold rides on the proposal in `DkgThresholdTag`, every
invitee's `acceptProposal` re-checks it against `quorumRange` and drops a
proposal outside it, and the `HOST_KEY` gate is where each of them agrees to the
`t`-of-`n` they can now see. Joining is the agreement; the coordinator only
writes down what is being joined.

Confirm stays disabled below two selections, and below a threshold outside the
range, with the reason stated rather than left to be guessed.

**`DkgRitualRoute` gains an optional `parentChatRoomId`**, and the ritual screen
grows one rung. The alternative — a parallel subgroup screen — duplicates a
ladder, a threshold picker, three approval gates and a key-state rung in order to
insert one step, and the two copies would drift within a release. The rung is
rendered only when a parent is present, and it is where the coordinator proposes
the certificate and watches the parent's quorum answer.

The approvals themselves need **no new UI**. The certificate is a
`FrostSigningEvents.PROPOSAL` in the parent room and the key state one in the
ceremony room; `ProposalListScreen` and `FrostSigningScreen` already show and
approve both, on both transports.

## Phase 8 — the refusals

A subgroup is expensive and its room id is permanent, so every one of these is
checked before the button is offered *and* again in the manager. The UI check is
what keeps a user out of a dead end; the manager check is what keeps a bug out of
a room nobody can replace.

| refused | because |
|---|---|
| fewer than three admins in total — the coordinator plus two | below three a quorum is not a check — the same floor `ChatRoomType.MINIMUM_ROBUST_GROUP_SIZE` states |
| a threshold outside `ChatRoomType.quorumRange(adminCount)` | `t = 1` is a threshold key any one member signs with, and ChillDKG will happily generate one. `acceptProposal` already refuses such a proposal, so an unchecked one produces a ceremony every invitee silently drops |
| the selection is not a proper subset of the parent's members | selecting everybody derives the parent's own ceremony room, and the "child" comes out as the parent |
| the ceremony room already holds a non-`FAILED` `DkgSession` | `proposeRitual` would return that ceremony, and the "new" subgroup would be the old one — same key, same id |
| this device holds no share of the parent's key | it cannot open the certificate session; `proposeSigningBatch` throws, and throwing at the button is not a UI |
| this device is not an admin of the parent | a non-admin proposing the parent's signature is a proposal the parent's admins have to decline by hand |
| the parent has no signed key state and no resolvable key | there is nothing for the certificate to be signed with |
| a certificate already stands for this child | one certificate per child; a second is a `d`-tag replacement of the first, not a second subgroup |

The first and the third are the ones a user will actually meet, so both say what
to do: "pick at least two more people" and "a subgroup cannot be the whole group
— leave at least one member out".

## Phase 9 — the tests that prove it

The pure ones carry the weight, because every check that matters is pure:

- `SubgroupBirthCertificateEventTest` (commonTest) — Phase 1's six cases.
- `GroupKeyStateTest` (commonTest) — Phase 3's five `stateFrom` cases.
- `SubgroupGuardsTest` (commonTest) — Phase 8's table, one case each, against
  the pure predicates rather than the ViewModel.

Two that need a database, in jvmTest, where `secp256k1` loads and Room runs:

- `SignedGroupKeyStateTest` extension — a real ChillDKG key, a real parent key, a
  real certificate signed by the parent, a real child key state carrying it, and
  the assertion that `stateFrom` accepts it and rejects each single-field
  mutation of it.
- `SubgroupDaoJvmTest` — schema 17 round-trips the three columns, and
  `subgroupsOf` returns a certified child whose room does not exist locally.

One end-to-end, and it is the one that would have caught the collision in Phase
4: two ceremonies proposed over the same admin set land in the same room, and the
second is refused rather than silently returning the first's key.

## Rollout

Nothing here changes an existing flow's behaviour. The two shared pieces are the
risk, and both are additive:

- `GroupKeyStateEvent.assembleTags` emits the new tags only when a parentage is
  passed, so every state signed before this ships verifies exactly as it did.
- `GroupKeyStateManager.stateFrom`'s new checks fire only on a state carrying one
  of the new tags. A state with neither takes the path it takes today.

An older build meeting a subgroup's key state ignores two unknown tags and files
the state as an ordinary one — it loses the parent link and keeps a correct
answer to what the room signs with, which is the right way round. An older build
meeting a **certificate** has no 30329 arm and writes a raw-JSON chat bubble in
the parent room, once per subgroup. That is cosmetic, it is the same thing
`docs/member-chronicle.md` reports for chronicle pages, and it is the reason to
ship Phase 5's dispatch arm before anybody creates a subgroup rather than after.

Phases 1-3 can ship dark: they add a kind nothing produces and checks nothing
triggers. Phases 4-6 are inert without Phase 7's entry point. So the flag, if one
is wanted, is the button.

## What this does not do

**A subgroup cannot sign for its parent, and the parent cannot sign for it.**
Two independent keys, two independent quorums. The certificate is a claim about
lineage, not a delegation of authority, and nothing in this design lets one group
act as the other. If delegation is ever wanted it is a different mechanism and it
should not be built on top of this one silently.

**A member added to the parent later sees no subgroups.** Certificates are not
chroniclable, for the reason Phase 5 gives. The argument for admitting them is
better than it is for a key state — a certificate says "P certified C", which is
a fixed historical fact, whereas a key state decides what a live room signs with,
so a replayed certificate changes nothing a fresh one would not — but it is an
argument that deserves its own change, its own idempotency test and its own
apply-order slot, not a line in this one.

**One admin set, one subgroup.** Phase 4's collision is refused, not solved. Two
subgroups with exactly the same admins need the NIP-17 ceremony room to be
distinguishable by something other than its members, and
`ChatRoom.deriveChatRoomId` is a pure aggregate of member keys that the inbound
path recomputes. Changing it is a change to how every NIP-17 room in the app is
addressed, and it is not worth making for this.

It is also the limitation that disappears on its own: move the ceremony into the
parent's Marmot room, as [Phase 4](#why-not-the-parents-marmot-room) defers doing,
and there is no member-derived id left to collide.

**Every selected admin has to show up, twice.** A ChillDKG cannot finish without
all `n`, and the key state then needs a quorum of them. A member who never
approves stalls the subgroup at step 1 indefinitely, and the only signal is the
ritual screen naming who it is waiting on. This is inherited from
`docs/shared-key-ceremony.md` and is the single biggest practical cost of a
subgroup.

**A subgroup of a subgroup is untested.** Nothing forbids it — the child is an
ordinary robust group with a key of its own, so it can certify a child in turn —
and the chain is checkable link by link. But no phase here exercises depth 2, and
the parent row on the detail screen shows one level rather than a path.

**Removing a subgroup is not a thing.** There is no revocation, and a certificate
is a signature that exists forever once made. Deleting the child's room locally
leaves the parent's list showing a certified child nobody has.

## Appendix — what was considered and rejected

**A derived child key at `m/9420/1/0`, with no ceremony.** Free, instant, and
wrong: `docs/shared-key-derivation.md` states that anyone learning one derived
private key recovers the threshold key, and more to the point the child would be
administered by the parent's members with the parent's quorum. That is a channel,
not a subgroup.

**The certificate as a bare 64-byte signature plus a rebuild rule.** Smaller on
the wire, and it makes `GroupKeyStateManager.stateFrom` responsible for
reconstructing byte-for-byte the event the parent signed — kind, author, tags, and
`created_at` — from fields carried alongside. The first change to the
certificate's tags then invalidates every state already signed, silently, because
a rebuilt event that differs by one byte hashes to an id whose signature does not
verify and is indistinguishable from a forgery. Storing the whole event costs a
few hundred bytes inside an encryption and removes the class.

**A `Subgroup` table.** The certificates already are the record: signed, held by
every parent member, and verifiable without the table. A table would be a second
copy that can disagree with them, and the only thing it would add is flow state
for the coordinator — which `DkgSession.parentChatRoomId` covers with one column.

**Running the child's ChillDKG and key state inside the parent's Marmot room.**
The better design, deferred rather than rejected, and argued in full in
[Phase 4](#why-not-the-parents-marmot-room). Short version: the two move
together, the mechanical cost is three enumerable changes, and the reason to wait
is `docs/mls-skipped-keys.md` — a ceremony needs every participant, and group
events lose one of any two that arrive back to back.

**A dedicated subgroup derivation path.** `m/9420/0/0` is reused, because the
path is walked from the *child's own* key and two different keys at the same path
derive two different rooms. A separate constant would say something true about
lineage in a place nothing reads for lineage.

**A parallel subgroup ritual screen.** Rejected in Phase 7: it duplicates four
pieces of a five-piece screen to insert one, and the copies drift.
