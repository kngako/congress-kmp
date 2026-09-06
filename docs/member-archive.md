# Handing a new member the group's history

A member added after the work was done sees none of it, and no amount of waiting
fixes that. This is how to send them the group's signed record, why the sending
member cannot be trusted and does not need to be, and the one thing an archive
cannot give them.

Read [shared-key-derivation.md](./shared-key-derivation.md) first. The property
this whole design rests on -- that a room's id *is* the key it signs with -- is
stated there, and everything cheap about what follows is downstream of it.

## The constraint

Two independent facts, and both have to be understood before the design makes
sense.

**MLS gives no history.** A Welcome carries the ratchet tree at the current
epoch, not the transcript. `MarmotInboundManager` drops anything from an epoch it
holds no keys for, and nothing replays. This is not a gap to be closed -- it is
forward secrecy working, and a design that quietly undid it would be worse than
the problem.

**Group-signed events never travel at all.** This is the one that surprises
people. `FrostSigningManager.complete` says so in as many words:

> Nothing goes on the wire: a signed event authored by the threshold key cannot
> travel as an inner event anyway, because the outbound pipeline re-authors
> rumors as their sender and would strip the group's signature off.

Every device *derives* the finished event from its own `FrostSigningItem` rows
once the signature aggregates. A member who was not in the session has no items,
and no message ever sent afterwards carries the event. So the second fact does
not follow from the first and is not fixed by fixing it: even a member who could
decrypt the entire back-transcript would still hold nothing an artifact, chapter
or chunk could be built from.

Which makes an archive not a convenience but the only path, and fixes the line
the design has to hold:

> **An archive carries what the group signed. Never the chat.**

Two reasons, and the second is the load-bearing one. Restoring the chat would
undo forward secrecy on purpose. And a signed event is the only thing a new
member can check for themselves -- everything else would have to be believed
because a member said it, which is a worse property than the gap it fills.

## Verification costs a room id and nothing else

A new member holds their Welcome, and so the room's id. That turns out to be
everything they need.

`GroupKeyStateEvent.isSignedByGroup` already asks exactly the right question --
did *this room's* key sign this event -- in three parts: the author is the key
derivation reaches, the id is the hash of the fields sitting next to it, and the
signature verifies. And its first line is:

```kotlin
val author = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path)
if (!event.pubKey.equals(author, ignoreCase = true)) return false
```

That derived value is the room's id. `GroupKeyState.isMatchedBy` enforces it,
`FrostSigningManager.signingPath` resolves the path by it, and
`DkgRitualViewModel` creates the `#admins` room *at* it. So for any room with a
shared key, `marmotGroupId(thresholdPublicKey, path) == chatRoomId`, and the
check collapses to:

```kotlin
event.pubKey == chatRoomId && hashIdCheck(...) && Nip01Crypto.verify(...)
```

No `GroupKeyState` row, no threshold key, no derivation path, no lookup. A member
who can name the room can verify its signatures. That single fact decides most of
what follows:

| question | answer, and why |
|---|---|
| Who may send an archive? | Anyone in the room. The receiver checks every payload, so a hostile sender can inject nothing. |
| Does it need encrypting to the recipient? | No. It is the group's own history going back to the group. |
| Does the new member need the key state first? | No. That was the ordering problem this removes. |
| What can a hostile archive do? | Omit. Not forge. See [What this does not do](#what-this-does-not-do). |

### The guard that is not optional

Nothing on the inbound nip30303 path verifies a signature today.
`ChatMessage.applyInnerEvent` parses and upserts, and that is *correct* as things
stand: rumors carry `sig = ""` and are authenticated by the MLS frame, so nothing
on the wire has ever claimed group authorship. An archive is the first thing that
does.

So `isSignedByRoom` is not hardening. It is the feature's entire security, and
without it any member can submit a fabricated `ArtifactEvent` with `pubKey` set
to the room id and a junk signature, and a new member files it as agreed group
work.

### And the guard behind that one

Verification admits an event to the apply path on the strength of the group's
signature. That makes **every kind the group has ever signed replayable by any
member at any time**, which is a larger door than it first looks.

`GroupKeyStateEvent` (30326) is group-signed and would pass `isSignedByRoom`
perfectly. An archive carrying an old one is a validly signed statement about
which key the room signs with, replayed by whoever kept a copy.

> **An archive carries an allowlist of document kinds, never everything that
> verifies.** The list is the nip30303 kinds `applyInnerEvent` dispatches, and
> the rule is checked on the way out *and independently on the way in*.

Same shape as the cap on `k` in
[frost-batch-signing.md](./frost-batch-signing.md#a-cap-on-k), and the same
reasoning: the outbound check is politeness, the inbound one is the security
boundary.

## Push and pull

The obvious trigger is the invite: send the archive right after the Welcome. That
works, and on its own it is unreliable in the way
[marmot-membership.md](./marmot-membership.md#why-this-fails-silently) describes.
An archive is an application message in the epoch the add created. If it reaches
the invitee before their Welcome does -- different transports, no ordering
guarantee -- it is **dropped, not deferred**, and the sender sees nothing wrong.

The fix is not to make the push more careful. It is to let the joiner ask:

- A request is proof of arrival. A device that can send an application message in
  the room has processed its Welcome; the race has nothing left to lose.
- It covers what a push never can. A reinstall, a second device, a member whose
  inviter has since left, an archive that was lost -- none of those has an invite
  to hang off.
- It converges. Requests repeat, archives are idempotent, and any member can
  answer.

So both, on the same two events: **the pull is the mechanism and the push is a
latency optimisation on top of it.** Phase 6 is the push, and it is deliberately
after the phase that makes it unnecessary.

---

## Phase 1 -- the verifier

**Half a day. No wire change, no behaviour change.**

In [GroupKeyStateEvent.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/nostr/frost/GroupKeyStateEvent.kt),
split the existing check in two and keep the existing one as a caller:

```kotlin
/**
 * Whether the room with id [chatRoomId] signed [event].
 *
 * The room's id is the group's signing key -- see shared-key-derivation.md --
 * so this needs nothing but an id the caller already has. That is what makes
 * an archive checkable by a member who holds no key state and no share.
 */
fun isSignedByRoom(event: Event, chatRoomId: HexKey): Boolean = runCatching {
    if (!event.pubKey.equals(chatRoomId, ignoreCase = true)) return false
    if (!EventHasher.hashIdCheck(...)) return false
    Nip01Crypto.verify(...)
}.getOrDefault(false)

fun isSignedByGroup(event: Event, thresholdPublicKey: HexKey, path: List<Long>) =
    isSignedByRoom(event, SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path))
```

Everything already caught stays caught: every input is off the wire, and a pubkey
that is not a point, a signature that is not 64 bytes and hex that is not hex all
mean the same thing here.

**Test**, in `commonTest` beside the existing ones -- pure functions, no database:
a real group-signed event passes against its own room id and fails against
another's; a member-authored rumor (`sig = ""`, member pubkey) fails on both
counts; an event with the room's pubkey and a random signature fails; an event
whose content is edited after signing fails on the id check before the signature
is even reached.

---

## Phase 2 -- the events and their codec

**A day.**

A new package, `press.mantra.compose.nostr.archive`, with `ArchiveEvents.kt`
holding the kinds -- mirroring `FrostSigningEvents`.

```
 holder    --[ 30327 archive         ]-> one member   a page of signed events
 joiner    --[ 30328 archive request ]-> everyone     "I have none of this"
```

**Why 3032x and not 30313.** The nip30303 family runs 30300 to `SubmissionEvent`
at 30312, and 30313 is free *in the Marmot inner-event space*. It is not free in
the NIP-17 gift-wrap space, where the DKG sits on 30310-30316.
`FrostSigningEvents`' own header calls that overlap "an accident of routing
rather than a decision, and the next family added should not rely on it." This is
that next family, so it does not. 30327 and 30328 sit past `GroupKeyStateEvent`
at 30326 and clash with nothing on either transport.

It is also the right neighbourhood on the merits. An archive is not a document
kind; it is a statement about the record, which is what `GroupKeyStateEvent` is
too.

### Why not just send N `SubmissionEvent`s

The envelope is right there, it already carries a payload whole "keeping its own
id, author and signature", and its header even names the case. It is still the
wrong kind here, for three reasons:

- **A submission is an act** -- *this member is putting this event in front of
  this group*. An archive asserts nothing; it re-delivers what the group already
  agreed. On one kind, a 400-event backfill is indistinguishable from 400 new
  submissions, and every device has to guess which it is looking at.
- **N submissions are N inner events and N kind:445s.** A page is one.
- **The submission arm files a `ChatMessage` per payload.** An archive must not
  -- see Phase 4.

### Shape

Content is a JSON array of the signed events, whole. Always an array, even for
one: there is no old build to stay compatible with, which is the only reason
`FrostSigningEvents.encodeProposal` has a bare-object form. Do not copy that
shape here.

Tags, one value each, per the house convention:

| tag | holds | why |
|---|---|---|
| `ArchiveIdTag` | 32-byte hex | Ties pages of one archive together, so two members answering the same request do not interleave into one nonsense sequence. |
| `ArchivePageTag` | index, total | The receiver can say whether it holds a whole archive. |
| `p` | recipient pubkey | **A hint, not access control** -- see Phase 4. |

### Two caps, both enforced on receive

```kotlin
const val MAX_PAGE_BYTES = 64 * 1024
const val MAX_PAGE_EVENTS = 256
```

A byte cap rather than a count alone, because the events vary by two orders of
magnitude -- a chunk is a paragraph, an artifact is a URL. The count cap bounds
the receiver's *work* where the byte cap bounds the *transport*.

Both are checked independently on the way in, for the reason the batch cap is:
an archive is the second place in this protocol where a remote party decides how
much work everyone else does. Measure the 64 KB against a finished kind:445
rather than trusting it -- MLS framing and NIP-44 expansion both sit outside it.

**Test:** codec round-trip; a page over either cap is refused on receive; an
array containing a non-event is refused whole.

---

## Phase 3 -- assembling an archive

**A day.**

`ArchiveManager.assemble(database, chatRoomId, recipient): List<EventTemplate<*>>`

Read every group-signed event this device holds for the room, order it, pack it
into pages, and queue each page as a `MarmotInnerEvent` -- the ordinary outbound
path, nothing new.

### Where the events come from

Signed events are not stored as events; they are stored as rows. So the archive
is rebuilt from `Mantra*` rows via each entity's `toXEvent()`, which is exactly
what the round-trip convention exists for: `toXEvent` emits tags in the same
order as `build`, so the id round-trips, and the row carries `signature` and
`publicKey` alongside. Reassembled event, original signature, verifies.

**This is the assumption to test first, before writing anything else in this
phase.** If any entity's `toXEvent` does not round-trip to an id whose signature
still verifies, that entity cannot be archived at all, and it is better to find
out in an afternoon than in Phase 8. A round-trip test per kind, over rows
produced by a real signing session, is the gate on the rest of this work.

### Ordering

Room enforces the shape, so an archive out of order is a foreign key violation
rather than a wrong answer. The rank:

| # | kind | event | depends on |
|---|---|---|---|
| 1 | 30304 | Dialect | -- |
| 2 | 30300 | Artifact | Dialect |
| 3 | 30301 | ArtifactVersion | Artifact |
| 4 | 30302 | Chapter | ArtifactVersion |
| 5 | 30303 | Chunk | Chapter |
| 6 | 30306 | TranslationArtifactVersion | ArtifactVersion, Dialect |
| 7 | 30305 | TranslationArtifactVersionContributorList | TranslationArtifactVersion |
| 8 | 30308 | TranslationChapter | TranslationArtifactVersion, Chapter |
| 9 | 30307 | TranslationChapterContributorList | TranslationChapter |
| 10 | 30309 | TranslationChunk | Chunk, TranslationChapter |
| 11 | 30311 | Translation | TranslationChunk, TranslationArtifactVersion |
| 12 | 30310 | TranslationContributorList | Translation |

Kind order is not rank order -- 30305 and 30307 are contributor lists that hang
off things numbered above them -- so the rank is a table, not a `sortedBy { kind }`.
Note also that `TranslationChunkEvent` and `TranslationChunkProposalEvent` share
kind 30309; they have identical dependencies, so one rank covers both, and
`applyInnerEvent` dispatches 30309 to the chunk arm regardless.

The same rule the batch signing work landed on -- *the thing being referenced is
signed first* -- and the same reason.

**Pages preserve the rank across the whole archive**, not within each page. Page
boundaries fall wherever the byte cap lands.

### Packing

Greedy: serialise, accumulate, cut when the next event would cross either cap.
An event that alone exceeds `MAX_PAGE_BYTES` cannot be archived; log it by id and
carry on rather than failing the archive. That is a real hole and should be
visible -- but a chapter nobody can archive is better than a member who gets
nothing.

---

## Phase 4 -- applying one, and the sweep

**Two days. The phase with the correctness in it.**

### Who applies

A page names its recipient in a `p` tag, and **a device that is not the named
recipient stores the inner event and does nothing else.** It already holds the
work; re-applying would rewrite `marmotGroupEventId` on every one of its rows to
point at an archive page rather than at the event that actually introduced it,
which is provenance loss for no gain.

So the `p` tag is an addressing hint and not a secret. Say so where it is
defined. The group can read the page and is welcome to -- it is their own
history. What the tag decides is who *acts*.

### Applying

```kotlin
ArchiveManager.apply(database, chatRoomId, page: MarmotInnerEvent)
```

1. Parse the content array. A page that will not parse is dropped whole.
2. Check both caps.
3. For each payload, in this order and all of it per payload:
   - kind is in the allowlist, else drop and log the id;
   - `isSignedByRoom(payload, chatRoomId)`, else drop and log the id;
   - `applyInnerEvent(...)` with the page's ids, inside `try/catch`.
4. Discard every `ChatMessage` it returns.

**Per payload, not per page.** A forged payload sitting beside honest ones must
cost itself and nothing else -- the same reasoning `MarmotInboundManager` uses
for a forged direct message, and for the same reason: the caller is inside a
transaction and one bad event should not take the room down with it.

**Discard the chat lines.** `ChatMessage` has an `autoGenerate` primary key, so
every applied payload mints a *new* row -- there is no id to dedupe on. An
archive that filed them would give the new member a synthetic transcript dated
now, and give them a second one on every re-run of the sweep. The archive
restores the work; the conversation is forward secret and stays gone.

`applyInnerEvent` already does its entity upserts internally and merely *returns*
the line for the caller to file, so this is a matter of not calling `upsert`.
No change to `ChatMessage.kt` at all.

### The sweep, and why it needs no table

Pages arrive over relays with no ordering guarantee, so page 3 can land before
page 2 and its chunks have no chapter to hang off yet. Those payloads throw a
foreign key violation, get caught, and are lost -- unless something re-runs them.

Nothing has to be stored for that, because the inbound path already stores every
inner event it decrypts. This is precisely the situation
`FrostSigningManager.replayStoredMessages` is built for, and it takes the same
shape:

```kotlin
database.marmotInnerEventDao()
    .getByChatRoomAndKinds(chatRoomId, listOf(ArchiveEvents.ARCHIVE))
```

Re-apply every stored page for the room, oldest first, after each new page
arrives; repeat while a pass applies something it did not apply before; stop when
a pass applies nothing. Everything in it is an `upsert` keyed on the event id, so
a re-run is free and a converged archive costs one no-op pass.

Only the recipient sweeps, which is what bounds it: the members who skip apply
never build the list.

**Test:** an archive delivered in reverse page order converges to the same rows
as one delivered in order; a page whose payloads are all already applied changes
nothing; a page containing one forged payload applies the rest.

---

## Phase 5 -- the request, and self-healing

**A day, including one schema change.**

`ArchiveRequestEvent` (30328), sent into the room, content empty.

**When a device sends one.** On entering a room it holds no signed work for --
no `MantraArtifact` and no `MantraDialect` rows -- having processed its Welcome.
That covers the new member, the reinstall and the second device with one rule,
because all three look identical from inside the database, which is the point.

**Who answers.** Any member holding the work. Answering costs bandwidth and
nothing else -- pages are idempotent and non-recipients skip them -- so a
duplicate answer is waste, not damage. A random 0-30 s stand-down, skipped if
another member's archive for that request id is already on the wire, is worth
adding and is worth adding *last*: it is an optimisation, and shipping it with
the correctness would make it look like part of it.

### Schema 11 -> 12

One nullable column, so Room generates it:

```kotlin
val archiveRequestedAt: Instant? = null   // on ChatRoom
AutoMigration(from = 11, to = 12)
```

It stops a device re-requesting on every launch while an answer is in flight.
Rooms written before it read back null, meaning "never asked" -- true of all of
them, and harmless: the request is only sent for a room with no work in it, and
a room that has work will not ask.

Clear it when an archive for the room applies anything, so a partial answer is
followed by another request rather than by silence.

---

## Phase 6 -- the push, from the invite

**Half a day.**

Now that the request exists, the push is a latency optimisation and can be
written as one.

`MarmotOutboundDao.deliveryWelcome` is the seam -- both branches of
`inviteMember` reach it, the immediate one and the ack-triggered one in
`DatabaseNostrRepository`. Assemble an archive for the invitee there and queue
its pages behind the Welcome.

Two things to be honest about at that call site, in a comment:

- **Queued behind the Welcome is not delivered after it.** They are different
  transports -- a relay-borne gift wrap and a kind:445 -- and pages that arrive
  first are dropped for good. The request is what recovers that, and this push is
  worth having only because it usually wins.
- **The room must be re-read between the invite and the assembly**, for the same
  reason sequential invites re-read it: a snapshot taken before the commit
  describes an epoch the group has left.

Nothing here is allowed to report failure to the inviter. A push that does not
land is not an error; it is the ordinary case the pull exists for.

---

## Phase 7 -- UI

**A day.**

- **A room being backfilled says so.** A banner on the room and on the artifact
  list: *"Catching up on this group's work"*, with the page count from
  `ArchivePageTag` when an archive is in flight. Without it the first minutes in
  a new room are indistinguishable from a group that has done nothing, which is
  the wrong first impression and generates the support question this whole
  document exists to answer.
- **A "Send history" action** on the member row, for the case the automation
  misses and for testing. It queues an archive to that member.
- **The transcript gets one line per archive**, not one per event: *"Sent the
  group's history to X"* / *"Received the group's history"*. `TYPE_ARCHIVE`,
  added to `FROST_TYPES`' neighbours in
  [ChatMessage.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/database/model/ChatMessage.kt)
  -- check every set a new type has to be added to, because one missed set
  renders it silently as a chat bubble.
- **Say what the new member cannot do.** See below; this is the part of the
  feature most likely to be reported as a bug, and the screen is where to answer
  it.

---

## Phase 8 -- the tests that actually prove it

**A day and a half, and do not skip it.**

The unit tests are named in their phases. Three that only exist between devices,
all extending
[SignedGroupKeyStateTest](../composeApp/src/jvmTest/kotlin/press/mantra/compose/managers/SignedGroupKeyStateTest.kt)'s
harness -- two databases and the wire held by hand, with a third database added
for the joiner:

**The whole thing, end to end.** A and B run a real signing session over a
chapter and its chunks. C, a database with no share, no `DkgSession`, no
`FrostSigningSession` and no `GroupKeyState`, receives the archive and ends
holding rows identical to A's for every archived kind -- compared field by field,
signature included, not merely counted.

**The negative one that matters.** A hostile member sends C an archive of
hand-built events: one with C's room id as `pubKey` and a random signature, one
validly signed by *another* room's key, one edited after signing, and one honest.
C ends with exactly one row. This is the test the feature's security is, and the
only way to be a dishonest member in this harness is to build the inner event by
hand rather than let a device queue it.

**The replay that must not work.** An archive carrying a genuine, still-valid
`GroupKeyStateEvent` from an earlier epoch changes nothing about C's key state.
It passes every signature check there is; the allowlist is the only thing that
stops it, which is why it needs a test of its own rather than a line in the one
above.

And in `commonTest`, with no database: the `toXEvent` round-trip per archived
kind, asserting the reassembled event's id and that its stored signature still
verifies against it. That is the assumption Phase 3 rests on.

---

## Phase 9 -- rollout

**No code.**

Both kinds are new, so an old build receives an unknown inner event kind and
files it as unsupported, exactly as it does today for anything it does not know.
Nothing breaks in a mixed group; a joiner on an old build simply gets no archive,
and one on a new build in a group of old builds gets no answer to its request.
Neither is worse than today, which is no archive for anybody.

The order is deliberate: Phases 1-4 are shippable together and do nothing on
their own, because nothing sends an archive until Phase 5 asks for one. That
makes the first release a pure receiving capability, which is the safe half to
have in the field first.

---

## What this does not do

Each of these will be reported as a bug. None of them is.

**A new member still cannot sign, and an archive cannot change that.** This is
the big one. `proposeSigningBatch` resolves a `DkgSession` with a non-null
`secretShare` and then `signerIdOf`, or throws *"This device is not a participant
in ceremony ..."*. `GroupKeyState` states it plainly: *"A member can be in the
room without holding a share -- they were added after the ceremony, or
reinstalled -- and the state is still worth keeping: it says what the room signs
with, which is what tells them they cannot."*

Re-running the ceremony is not an escape either: *"a group that re-runs its
ceremony derives a different room rather than re-keying this one."* A post-archive
member can read everything and can still submit what needs no quorum --
`saveTranslation` and `addArtifactVersion` go through `MantraDao.submitToGroup`
with no share -- but cannot add a dialect, artifact, chapter or translation
version, and cannot sign anyone else's.

Closing that needs share resharing on the threshold key: a t-of-n key issuing a
share to a new participant without changing the public key it derives from.
It is a real protocol, it is a great deal more work than this document, and it is
the thing to build after this one.

**An archive can omit.** Verification stops forgery and does nothing about
silence: a sender can leave things out, and the receiver has no way to know. Any
member can send one and they merge idempotently, so asking a second member is the
practical answer, and a group that suspects one member is not the threat model
this app is otherwise built for. Making omission *detectable* needs a manifest of
ids that the group signs periodically -- one quorum, cheap, and rejected as the
general answer for the reason
[frost-batch-signing.md](./frost-batch-signing.md#appendix--what-was-considered-and-rejected)
gives for manifests. Worth revisiting once anything depends on completeness.

**The chat is gone and stays gone.** By design, restated here because it is the
first thing a new member will notice and the archive is what makes them expect
otherwise.

**A room with no shared key gets an empty archive.** An ordinary Marmot room's id
is `RandomInstance.bytes(32)`, not a derived key, so nothing can be signed by it
and there is nothing to archive. Correct, and worth a log line rather than a
silent empty result.

**An oversized single event cannot be archived.** A chapter whose text exceeds
`MAX_PAGE_BYTES` on its own is skipped with a log. Splitting a page mid-event
means a reassembly protocol, and that is not worth building before something hits
the limit.

**Nothing expires.** An archive grows with the group forever, and a member
joining a five-year-old room downloads five years. A cursor -- *everything since
event X* -- is the obvious next thing and is deliberately not in v1, because
"since" is a partial order over a dependency graph, not a timestamp, and getting
it wrong means an archive that references rows the receiver does not have.

---

## Appendix -- what was considered and rejected

**Re-sending the FROST session instead of the event.** Give the new member the
`FrostSigningSession` and its items and let them derive the signed events the way
everyone else did. It works and it is strictly worse: it ships nonce seeds and
signer sets to somebody who has no business holding them, to reconstruct an event
that could simply have been sent.

**Publishing signed events to relays.** They are already signed by a key anyone
can verify, so a relay could hold them and a new member could fetch them with an
ordinary REQ on `authors: [chatRoomId]`. Rejected, and it is the tempting one: it
would make the group's work public. Every artifact, chapter and chunk a private
group has agreed becomes readable by anyone who knows the room id -- and the room
id is in the `h` tag of every kind:445 the group has ever sent. A separate,
deliberate publication step for work a group *chooses* to publish is a good
feature; making it the backfill mechanism is a leak.

**One `SubmissionEvent` per archived event.** Covered in Phase 2. The envelope
fits and the meaning does not.

**Carrying the archive as a Marmot direct message.** The natural reading of "send
it to the new member" -- an NIP-59 wrap inside the group, per
[marmot-direct-messages.md](./marmot-direct-messages.md). Rejected: it encrypts
the group's own history to one member, which protects nothing; it costs a *"sent
a private message"* line per page in everyone's transcript; and its inner layers
are not forward secret, so it would be the weakest-protected copy of the group's
record on any device holding it. The `p` tag as a hint gets the addressing
without any of that.

**A dedicated table for unapplied archive payloads.** Phase 4's sweep reads
`MarmotInnerEvent`, which already holds every page. A second copy is a second
thing that can disagree with the first.

**Pushing on invite only.** The design that was asked for, and it works right up
until the epoch race in
[marmot-membership.md](./marmot-membership.md#why-this-fails-silently) -- where
it fails silently, looks like a successful invite, and leaves a member with a
room full of nothing. Kept as Phase 6, on top of the pull that makes it safe.
