# Adding a parent to a group that already exists

What it would take for a group to gain a second parent after it was founded.
Research, not a plan: nothing here has been built.

Read [subgroups.md](./subgroups.md) first. This extends it and contradicts it in
one place — the assumption, never stated because it never had to be, that a
lineage is a single link settled at founding.

Written against the code as it stands at `0be31803` — that is, **after**
`subgroups.md`'s Phase 10 moved the child's ceremony into the parent's Marmot room.
That move removed two of the costs an earlier draft of this note claimed adoption
would avoid and reshaped a third, so the case is made here on what is left. It also
makes founding with two parents *harder* — see the
[rejected alternatives](#considered-and-rejected).

## The short answer

**Adoption is two FROST sessions and no ceremony.**

```
  1. birth certificate, in the new parent  -> P2's quorum signs C
  2. key state, in C itself                -> C's quorum re-announces, carrying
                                              every certificate it accepts
```

That is the whole flow. Compare it with founding, which is four steps:

| founding a subgroup | adopting an existing group |
|---|---|
| 1. ChillDKG among the child's admins | — the key already exists |
| 2. birth certificate, in the parent | **the same step, unchanged** |
| 3. key state, in the parent's room where the ceremony ran | **the same event, in the child's own Marmot room** |
| 4. create the Marmot room | — the room already exists |

Losing step 1 is the point. `subgroups.md` names it as the single biggest
practical cost of a subgroup — *"a ChillDKG cannot finish without all `n`… a
member who never approves stalls the subgroup at step 1 indefinitely"* — and it
is also the last thing standing between a lineage and being an ordinary edit.

Phase 10 has already paid down what it used to drag in: the NIP-17 sibling room and
the member-derived id are gone, and the collision guard is now
`DkgSessionDao.getLatestSubgroupSessionFor` over `(room, parent, participants)` rather
than an address that could only be occupied once. So this note claims none of those as
savings — only the ceremony and the room creation, which is enough.

**The whole of `DkgSession`, `ChillDkgRitualManager`, `MarmotGroupCreation`,
`SelectSubgroupAdminsScreen` and the ritual screen's rung is untouched by this
design** — and Phase 10 makes that a stronger statement than it was, because those
files have just absorbed a large change that adoption does not have to follow.

Three things in the existing code make step 2 work as it stands:

- `GroupKeyStateManager.propose` already permits a room to propose its own key
  state: `check(chatRoomId == signingRoomId || key.chatRoomId == signingRoomId)`,
  and for an existing child the first branch holds. Nothing forbids
  re-announcement today; it is simply never done.
- `FrostSigningManager.signingPath` resolves correctly for it. Its first candidate
  is `GroupKeyStateManager.keyStateFor(chatRoomId)?.pathIndices()`, which for the
  child is `m/9420/0/0`, and `marmotGroupId(K, path) == chatRoomId` self-checks.
  The one un-self-checkable case is **not** reached. Phase 10 widened it from
  `mlsGroupState == null` to `isCeremonyRoom && (mlsGroupState == null ||
  isSubgroupCeremony)`, so it now admits the parent's room as well as a NIP-17 one —
  and adoption enters neither arm, because the child's own room is neither the
  ceremony's room nor its claimed parent.
- `GroupKeyStateDao.replace` keeps the newest state per room by `announcedAt`, and
  `announcedAt` comes off the signed event. So a re-announcement replaces the old
  lineage cleanly, and an out-of-order arrival is dropped rather than applied.

## A parent link is two signatures, one from each side

Worth stating plainly, because the founding flow bundles them and so hides it.

The certificate is the **parent's** consent: P's quorum saying "C is our child".
The parentage tags on the key state are the **child's** consent: C's quorum saying
"we accept P". Neither alone is a link. A parent cannot adopt a group that has not
agreed, and a group cannot claim a parent that has not signed — which is exactly
why `GroupKeyStateManager.parentageOf` drops a state whose certificate does not
check out, and why `SubgroupManager.proposeBirthCertificate` runs in the parent's
room rather than the child's.

At founding the two are bundled because the key state has to be signed anyway: it
is the founding fact, and the parentage rides along. For an existing group there
is no founding fact left to settle, so **step 2 is a key state signed purely as a
carrier for the child's consent.** That is the one slightly odd thing about this
design and it is worth doing anyway — see [why not a separate adoption
kind](#considered-and-rejected).

Once that is seen, the whole thing collapses to one sentence:

> A group's lineage is a set of (parent, certificate) pairs carried on its key
> state. Adding a parent is: that parent certifies, then the child re-announces
> its key state with the pair added. Founding a subgroup is the case where the
> set has one member and the key state was being signed anyway.

## The invariant, and how it differs from founding-time multi-parent

If two parents certified a child *at founding*, both certificates would be signed
at the same moment and could name each other — so a reader could demand that every
certificate agree on the whole parent set, and a coordinator could not staple two
unrelated certificates into a joint child.

**That check is not available here, and cannot be made available.** P1 certified C
before P2 existed as a possibility. An earlier signature cannot name a later
co-parent, and asking P1 to re-certify every time a sibling appears makes every
adoption cost a quorum in every existing parent.

So the invariant has to be weaker, and the honest version is:

> **The child's key state is the sole authority on the lineage set. Each
> certificate is checked individually — this parent signed for this child — and
> the set is whatever the child's quorum most recently declared.**

Which means a parent gets a veto on being *added* and no veto on *who else is in
the set*. That is not a compromise; it is the correct answer:

- Nothing P1 signed becomes false. P1 said "C is our child". C being P2's child
  as well does not contradict it.
- Exclusivity was never expressible and could not be enforced anyway. P2 can
  certify C whether P1 likes it or not — a certificate is a unilateral statement
  about a public room id. What P2 cannot do is make C *accept* it.
- **The child's consent is the whole gate**, and it should be, because the child
  is the one that acquires an obligation.

`parentageOf` therefore grows from four checks to six, and the two new ones are
small:

1. **All or none**, per pair. (Today's rule.)
2. Each certificate parses. (Today's.)
3. Each `certifies(certificate, child, itsParent)`. **`certifies` does not
   change** — it is six questions about one signer and one child and it stays
   that, so `SubgroupBirthCertificateEventTest`'s existing seven cases keep
   passing untouched.
4. Each certificate's `subgroup_key` is the state's own threshold key. (Today's.)
5. **No duplicate parents.** A set with a repeat in it is not a set.
6. Any failure drops the whole state. (Today's.)

There is no `co_parent` tag and no cross-certificate agreement check. Both were
needed only by the founding-time design, and both are gone.

### The footgun this creates

A key state is replaceable and the newest wins **wholesale**. So re-announcing to
add P2 must re-emit P1's pair as well, or P1 is silently dropped.

That is a bad API to leave open. `propose` should take the *resulting* set and
read the current one off the row, so that "add P2" is expressed as an addition
rather than as a full replacement a caller has to remember to assemble. The same
call then expresses removal, which is the next section.

## What this also unlocks

Two things fall out of the same mechanism, and neither needs extra work.

**Any existing robust group can become a subgroup.** Nothing in the two steps
requires the child to already have a parent. A top-level group with a key state
gets adopted exactly the same way, with the set going from empty to one. That is
arguably worth more than multi-parent itself, and it is the same code path.

**A child can drop a parent.** Re-announce with the pair removed. This partly
answers `subgroups.md`'s *"Removing a subgroup is not a thing"* — and only partly,
so the limit is worth being precise about:

- The certificate is forever. Nothing revokes a signature and nothing should
  pretend to.
- But a link is two signatures, and the child's half is replaceable. So the child
  can stop claiming a parent, and every device holding its key state will stop
  believing the link.
- The parent's own list does **not** follow, because `SubgroupManager.subgroupsOf`
  reads the parent's certificates, not the child's key state. P1 keeps listing C.
- That is fixable in the display without any new protocol: `Subgroup` already
  carries a `keyState` field, populated where the device holds the child's state.
  A parent device that also holds C's key state can see that C no longer names it
  and say so. A parent device that does not hold it cannot, and must not guess.

## The wire

### The key state carries pairs, in matched order

No new tag names. `parent_group` and `birth_certificate` repeat:

```
["parent_group",     <P1>]
["birth_certificate", <P1's certificate, as JSON>]
["parent_group",     <P2>]
["birth_certificate", <P2's certificate, as JSON>]
```

`SubgroupParentTag.parse` and `SubgroupBirthCertificateTag.parse` already work
per-tag; the readers change from `firstNotNullOfOrNull` to `mapNotNull`, and
`assembleTags` takes a list instead of a nullable single. That is the entire wire
change.

**The order is load-bearing, and only for old builds.**
`GroupKeyStateEvent.parseParentChatRoomId` and `parseBirthCertificate` take the
*first* of each, so a build predating this pairs `parent_group[0]` with
`birth_certificate[0]` and runs `certifies` on them:

- emitted **pairwise** — P1, P1's cert, P2, P2's cert — it pairs P1 with P1's
  certificate, it verifies, and the device files a true but partial lineage.
  Correct, just less.
- emitted **grouped** — both parents, then both certificates — it is coherent only
  while both lists happen to be in the same order, and silently wrong the moment
  they are not. Then `certifies` fails and the old build **drops the whole state**,
  losing the row that says which ceremony's share signs for that room. Recoverable
  through `completedKey`'s rederivation fallback, and only for default-path rooms.

So: emit pairwise, sorted by parent id so every device produces the same bytes.
Free, and the difference between graceful degradation and an old device quietly
losing its key state.

### The certificate does not change at all

Kind 30329 keeps its tags, its `certifies`, and its meaning. A certificate signed
for a founding subgroup and one signed for an adoption are the same event — which
is right, because they say the same thing.

## The schema

Much smaller than the founding-time version, because `DkgSession` is not involved.

**`GroupKeyState`: one-to-many.** The two columns `parentChatRoomId` and
`birthCertificateJson` become a table:

```kotlin
@Entity(primaryKeys = ["chatRoomId", "parentChatRoomId"], foreignKeys = [
    ForeignKey(entity = GroupKeyState::class, parentColumns = ["chatRoomId"],
               childColumns = ["chatRoomId"], onDelete = ForeignKey.CASCADE)
])
data class GroupKeyStateParent(
    val chatRoomId: String,
    val parentChatRoomId: HexKey,
    val birthCertificateJson: String,
)
```

The composite primary key *is* check (5) — one certificate per (child, parent),
enforced by the store rather than by a reader remembering. The cascade is onto
`GroupKeyState`, never onto `ChatRoom`, so `subgroups.md`'s argument against a
self-referential cascade stands untouched: the parent side stays a plain column
that routinely names a room this device does not have.

The cost is that filing a state stops being one write. `GroupKeyStateDao.replace`
has newest-wins logic and would have to become a `@Transaction` that rewrites the
parentage rows with the state, or a dropped parent survives the re-announcement
that dropped it — which is exactly the operation this design adds.

**`DkgSession`: untouched.** Adoption opens no ceremony, so it needs no session, no
discriminator and no migration there. Phase 10 has just added two columns to that
table — `participantPublicKeys`, a sorted comma-joined set matched whole by
`getLatestSubgroupSessionFor`, and `subject` — which is worth knowing for two
reasons: it is the precedent for the canonical-string shape, and it is the change
adoption is glad not to be entangled with.

**`ChatRoom.parentChatRoomId`: drop it.** It is write-only in production (see
[found while reading](#found-while-reading)), and leaving it holding one of
several parents makes it a cache that is wrong rather than merely unused.

Schema **19 → 20** — 19 is Phase 10's, for `DkgSession.participantPublicKeys` and
`subject`: one new table, one dropped column, and the existing rows carried across as
a single parentage row each. Not an `AutoMigration`; schema
9→10 already has the shape to copy.

### The zero-migration alternative, and why not

The parentage columns are already a *cache*: `stateFrom` derives them from the
signed 30326 event, and `signedStateFor` can re-read the newest such event for any
room at any time. So the lineage could be a derived read — parse the signed event,
run the checks, return the set — with no schema change whatsoever.

Worth knowing it is available, and worth not taking. `signingPath` reads the
`GroupKeyState` row on **every signature**, so the row stays either way; a design
where the path comes off the row and the lineage comes off the event is two
sources for one statement, which is the thing `subgroups.md` spends its appendix
refusing. Cache the whole state or cache none of it.

## Who can actually do this

The one requirement that does not soften, and it should be stated before the UI is
designed around it.

**Step 1** — proposing the certificate in P2 — needs a P2 admin holding a share of
P2's key, *who also knows the child's threshold key*. The certificate's tags carry
`subgroup_key`, and without it `certifies` fails at question 4. The child's
threshold key lives on the child's `GroupKeyState`, which only the child's members
hold. So the proposer is in both groups.

**Step 2** — proposing the key state in C — needs a member of C holding a share of
C's key, *who has P2's certificate*. Certificates are filed as `GroupSignedEvent`
rows by `recordSignedEvents`, keyed on the session's room, so only P2's members
have one. So this proposer is also in both groups.

They need not be the same person, and their capabilities differ — step 1 needs
admin rights in P2, step 2 needs a share of C's key. But **adoption requires at
least one person in both groups**, and there is no way around it: the certificate
has to cross a boundary that nothing in the app crosses.

That is still much weaker than founding-time multi-parent, where one coordinator
had to administer both parents *and* run a ceremony. And it is a realistic
requirement — a group being adopted by another group almost certainly has someone
in both.

What would remove it is a cross-group request: a way for C to publish "we would
like P2 to adopt us, here is our key" and for P2's admins to act on it. That is a
real feature with its own consent questions, and it should not be smuggled in as
a helper.

### The certificate rides on the key state, and needs no storage of its own

The obvious-looking alternative — file P2's certificate on the child's devices
when it arrives — is closed, and the closure is load-bearing:
`GroupSignedEvent` declares a foreign key onto `ChatRoom`, so a child device with
no P2 room row **cannot** store a P2-signed event at all.

It does not need to. `GroupKeyStateManager.propose` takes the certificate as an
`Event`, not as a row, and once the child's quorum signs the re-announced state
the certificate travels inside it — verified standalone by `stateFrom` on every
child device, exactly as at founding. The proposer needs the event; nobody else
does.

## Choosing the parent

Worth its own section, because "pick a group" is not what the picker can offer and
designing the screen around the wrong list would be the easy mistake.

### The list is the selector's own rooms, and cannot be anything else

A certificate is proposed **inside** the adopting group, by
`FrostSigningManager.proposeSigning` in that room. So the candidate list is not "the
groups that exist" or "the groups C's admins can name" — it is the rooms *this
device* can already act in:

```
getChatRoomListByUserPublicKey(user)
  filter { it is a Marmot room whose MarmotGroupData reads }
  filter { adminPubkeys.contains(userPublicKey) }
  filter { FrostSigningManager.canSign(database, it.id) }
  filter { it.id != C }
  filter { it.id !in C's current parents }
  filter { it.id is not a descendant of C }
```

The first three are `SubgroupManager.refuseSubgroup`'s existing checks with the
target moved from "the parent of a new subgroup" to "the adopting group", and the
admin one reads `MarmotGroupData.adminPubkeys` rather than `Participant.adminAt`, for
the reason that function already gives: the flag is written only by
`processGroupMembershipChanges` on an arriving commit, so it is null for everybody on
a room this device created.

**A group cannot be offered as a parent by somebody who is not in it.** That is the
overlap requirement from [who can actually do this](#who-can-actually-do-this) showing
up as a list length. If nobody in C administers P2, P2 simply does not appear, and the
remedy is a person rather than a button.

### Cost, and where it goes

`toMlsGroup()` deserialises the whole group state out of hex to reach `adminPubkeys`,
once per candidate room. `canSign` is cheap for exactly the rooms that pass —
`completedKey`'s first branch is a key-state lookup and a session lookup by id, and
its expensive fallback (`getKeyHoldingSessions` with a `marmotGroupId` derivation per
session) only fires for rooms with no key state, which fail the filter anyway. Its last
fallback is now `getLatestOwnSessionForChatRoom` — the same query scoped to
`parentChatRoomId IS NULL`, so that a parent hosting its child's ceremony cannot
resolve the child's key as its own.

So the filter is one MLS decode per room and two indexed reads. Fine at twenty rooms
and worth building the way `SelectChatRoomMembersViewModel.scheduleKeyPackageEventSynchronization`
already builds its prefetch: off the main thread, filling in as it resolves, rather
than blocking the screen on the whole list.

### The selection is an offer, not a decision

The ordering is forced — the key state carries the certificate, so the certificate is
signed first — and that has a consequence worth stating in the UI's own words:

> **P2's quorum commits first. C's quorum decides second.**

Whoever picks P2 is not choosing on C's behalf. They are asking P2 to make an offer,
and C's admins answer it at step 2 by signing the re-announced key state or not. The
real decision point for C is the key-state proposal, which is why
[the `ProposedEvent` gap](#consent-on-both-sides) is the most important small change
in this design.

It also means P2's quorum can be spent on an offer that is declined. That is not new —
founding has the same ordering, and `proposeBirthCertificate` runs before the child's
quorum has signed anything either — but at founding the child's admins have at least
*seen* the intent, because `DkgRitualEvents`' `parent_group` tag rides on the ceremony
proposal and the ritual screen says which group it is for. Adoption has no ceremony
and so no such hint.

### So C's admins need telling, and the arm to tell them already exists

Without one, the first thing an admin of C hears about the whole business is a
proposal asking for their signature.

`ChatMessage.applyInnerEvent`'s 30329 arm should grow a second branch. Today it calls
`SubgroupManager.record`, which asks `certifies(event, subgroup, chatRoomId)` — the
certificate's parent must be the room it arrived in — and drops anything else. A
certificate that instead names *this* room as its subject, and verifies against the
parent it names, is the arriving offer:

| the certificate | the line |
|---|---|
| signed by this room, about another | "The group made #Translators a subgroup…" (today's) |
| about this room, signed by another | "#Ekklesia has offered to adopt this group" |

Both branches are fully verified and neither needs a lookup: `certifies` checks the
signature against a room id, and a room id is a public key. Nothing is stored — the
`GroupSignedEvent` foreign key onto `ChatRoom` forbids it, and nothing needs it,
because the certificate reaches C's devices for real inside the key state at step 2.
The line is notice, not evidence, and should read as an offer rather than as a fact
about C's lineage.

Delivering the certificate into C is then one act by the member who is in both, and it
is the same act that gives the step-2 proposer the event they need.

### Which screen it starts from

Two defensible entry points, and they produce an identical certificate:

- **C's detail screen**, beside the parent card: *"add a parent group"*. The child is
  the one acquiring the relationship, its quorum is the one that ultimately decides,
  and it mirrors the parent screen's existing *"add subgroup"*.
- **P2's detail screen**, beside *"add subgroup"*: *"adopt an existing group"*. This is
  where the proposal actually lands, and where P2's admins would look for what their
  group has taken on.

C's screen is the better default, for the mirroring and because the intent usually
originates with the group looking for a parent. P2's is worth adding later; the guards
are the same set read in the other direction, and the candidate list becomes "Marmot
rooms I hold a key state for", since that is where `subgroup_key` comes from.

## Consent, on both sides

The parent's side already works. A P2 admin sees `ProposedEvent`'s 30329 summary —
the name and the admin count — and their device checks
`marmotGroupId(subgroup_key, frost_path) == content` before they agree. Unchanged.

**The child's side does not, and this is the gap that has to close.**
`ProposedEvent`'s 30326 arm reads:

```
label:  "This group's shared key"
detail: "m/9420/0/0 · a1b2c3d4e5f6"
```

A child admin approving an adoption would be shown the path and a ceremony id, and
nothing whatever about the fact that they are accepting a parent. The proposal is
their only chance to check — after it, a quorum has spoken — so the arm has to
read the parentage off the tags it is already holding and say which groups are
being accepted, and which are being dropped relative to the current row.

That is the single most important small change in this design. Everything else is
plumbing; this is the one place where a member is asked a question and is
currently not being told what it is.

## Guards

Two new refusal sets, both in the shape `SubgroupManager.refuseSubgroup`
already has: checked before the button is offered, and again in the manager.

**Before proposing a certificate in P2** — the first three are its existing checks,
retargeted from "the parent of a new subgroup" to "the adopting group":

| refused | because |
|---|---|
| P2 is not a Marmot room with readable group data | no admin list to consult; the same reason the subgroup button is hidden on a NIP-17 room |
| this device is not an admin of P2 | a non-admin proposing the group's signature is a proposal the admins must decline by hand |
| this device holds no share of P2's key | `proposeSigningBatch` throws, and throwing at a button is not a UI |
| this device does not hold C's key state | there is no `subgroup_key` to certify, and a certificate without one fails `certifies` question 4 on every device including this one |
| C **is** P2 | a group is not its own parent |
| P2 is already among C's parents | that is a re-certification, not an adoption. `proposeBirthCertificate`'s `check(certificateFor(...) == null)` already refuses it; it should say so in those words |
| P2 is a descendant of C, as far as this device can see | a cycle. Bounded walk, advisory only — see below |

**Before proposing the key state in C:**

| refused | because |
|---|---|
| this device holds no share of C's key | same reason |
| any certificate in the proposed set fails `certifies` | already checked by `propose`; now per pair. Proposing a state this device would drop spends a quorum's attention on nothing |
| the set has a duplicate parent | check (5), at the door rather than only on read |
| the set is unchanged from the current row | nothing to agree |

## New failure modes

**A lineage is a DAG now, so a walk can loop.** With one parent it is a tree and
`parentOf` returns a single id, so nothing was tempted to recurse. A cycle is
constructible — C's quorum certifies P1 using P1's public threshold key, and P1's
quorum re-announces its own key state accepting it, which this design is precisely
what makes possible. It takes both quorums cooperating in something pointless, but
any ancestor walk must be depth-bounded and visited-set guarded regardless, and
the guard above is only as good as what this device can see. `subgroups.md` already
notes depth 2 is untested; this makes it worth testing rather than merely noting.

**A half-adopted state exists between the two steps** — P2 has certified and C has
not accepted — and every reader has to render it as an ordinary intermediate rather
than as an error. Note it is *invisible* from C's side unless the certificate has
been brought across, which means the child's screen cannot show "P2 has certified
you" for a member who is not in P2.

**A re-announcement that forgets an existing parent drops it silently.** Addressed
by the API shape above, and worth a test that names it.

## The UI

Two rungs on the child's `ChatRoomDetailScreen`, beside the parent card. No new
screen, and no new approval UI at all — both proposals are ordinary
`FrostSigningEvents.PROPOSAL`s that `ProposalListScreen` and `FrostSigningScreen`
already show and approve.

**"Add a parent group"** opens the picker described under [choosing the
parent](#choosing-the-parent) and proposes the certificate in whichever group is
chosen. Offered only where step 1's guards pass, hidden rather than disabled, in the
same way the shared-key entry is hidden on an MLS room — and empty for a member who
administers none of C's candidate parents, which is a fact about them rather than a
failure.

**"Accept #P2 as a parent"** appears once a certificate this device can see has
been signed, and proposes the re-announced key state. Offered to any member of C
holding a share — not only to whoever started, for the same reason `subgroups.md`
gives for the ritual screen's rung. Its wording is the decision, not the mechanism:
C is accepting an offer, and the transcript line from the arriving certificate is
what it is accepting.

**The parent card becomes up to N cards.** It reads
`ChatRoomDetailUIState.parentChatRoomId` ← `chatRepository.parentOf` ←
`GroupKeyState`, so it is a straight plural, and it keeps the rule it has: absent
rather than guessed where the state is not held.

**`SubgroupManager.Subgroup` gains a "still claims us" signal**, off the `keyState`
field it already carries. Only where the device holds the child's state; silent
otherwise.

## Tests

- `SubgroupBirthCertificateEventTest` — **unchanged, and that is the assertion**.
  `certifies` does not change, so all seven cases including the one whose job is
  to fail if a roster check is added must keep passing with no edits.
- `GroupKeyStateTest` (commonTest, pure) — two parents both good; two parents with
  one certificate missing → drop; the same parent twice → drop; a single-parent
  state in today's shape still accepted, which is the compatibility case; and an
  empty set on a state carrying no parentage tags, which is every group made
  before this.
- A pairing-order test, because the reason for pairwise emission is a build that
  does not exist yet: assemble a two-parent state, read it with the *old*
  first-tag-wins parsers, and assert the pair that comes out is coherent.
- `SignedGroupKeyStateTest` (jvmTest) — the one that proves the flow. A real child
  key, a real P1 key and state, a real P2 key, a real certificate by P2 over the
  child, a real **re-announced** state carrying both, and the assertion that
  `stateFrom` accepts it and rejects each single-field mutation.
- `SubgroupDaoJvmTest` — schema 20 round-trips the table, a newer state replaces
  the parentage rows of an older one, and a re-announcement that drops a parent
  leaves no row behind.
- `SubgroupManagerJvmTest` — the two guard tables, and the candidate filter: a room
  this device is in but does not administer is not offered as a parent, and neither
  is one it administers but holds no share of.
- `ChatMessageJvmTest` — the 30329 arm's second branch: a certificate about this room,
  signed by another, writes the offer line; one signed by neither this room nor the
  parent it names writes nothing.

## Rollout

Additive apart from the migration.

`assembleTags` emits pairs only where a lineage is passed, so every state already
signed verifies exactly as it did. The plural parsers read a single pair as a set
of one. The new checks fire only on a state carrying more than one pair.
`certifies` does not change. **The founding flow is not touched at all** — a
subgroup is still founded with one parent, and multi-parent is reached by founding
normally and then adopting.

An older build meeting a two-parent state files a true, partial lineage — one
parent, verified — as long as the tags are pairwise. An older build meeting a
re-announced *single*-parent state sees an ordinary key state and replaces its row,
which is correct.

The migration is the only non-additive part, and the risk in it is the write path
rather than the read: `replace` becoming transactional is where a bug would put a
stale parent on a state that no longer claims it.

Phase 1 (the wire) and phase 2 (the schema) can ship dark. The rest is inert
without the two rungs, so the flag, if one is wanted, is again the button.

## Considered and rejected

**Founding a subgroup with two parents at once.** The version this question
replaced. An earlier draft rejected it on costs Phase 10 has since removed — the
member-derived ceremony room and the parent set as a `DkgSession` discriminator — so
the argument has to be made again, and it comes out the same way for a better reason.

**Phase 10 makes founding with two parents harder, not easier.** The ceremony now runs
in *the parent's room*, and a child with two parents has two candidate rooms and no
principled way to choose: `DkgSession.chatRoomId` is one column, the ceremony's
messages ride that room's MLS transport, and the members of the other parent — who may
be admins of the child — are not in it to receive them. Running it in both means two
ceremonies and two keys, which is two children. Running it in one means the other
parent's admins take part in a ceremony held somewhere they cannot see, which is not a
thing MLS will do.

Adoption sidesteps the question entirely by not holding a ceremony, and what remains
against founding-with-two is unchanged: it still needs a co-parent tag so certificates
can agree on the set, a coordinator who administers both parents, and two quorums
answering before the child room exists at all — and it still cannot adopt a group that
already exists, which is the case people actually have. Founding with two parents stays
reachable: found with one, adopt the second, and the room is a few minutes old instead
of a few seconds.

**A separate adoption kind, leaving the key state alone.** Cleaner-sounding: a new
event by the child's quorum saying "we accept P2", so the founding fact is never
rewritten. It splits the lineage across a founding key state plus N adoption
events, and then a device holding the state but missing adoption #2 has an
incomplete lineage **and no way to tell that it is incomplete**. One replaceable
statement is always whole or absent. That property is worth more than the
tidiness of not re-signing.

**Filing foreign certificates on the child's devices.** Closed by
`GroupSignedEvent`'s foreign key onto `ChatRoom`, and unnecessary: the certificate
rides inside the key state.

**An `exclusive` flag on the certificate**, meaning "we certify C on condition it
has no other parent", enforced by dropping any state whose set has more than one
member when any certificate claims it. Cheap and checkable, and it hands a group
that has gone quiet a permanent veto over a group that has not. If exclusivity is
ever wanted it should be a social arrangement, not a tag.

**Requiring every certificate to agree on the parent set.** Impossible here, and
the reason is the interesting part: P1 signed before P2 was a possibility. See
[the invariant](#the-invariant-and-how-it-differs-from-founding-time-multi-parent).

## Found while reading

Four things, none caused by this, all worth knowing.

**`ChatRoom.parentChatRoomId` is write-only in production.**
`MarmotGroupCreation` sets it; its only reader is
`ChatRoomDao.observeByParentChatRoomId`, whose only callers are tests. So are
`GroupKeyStateDao.getByParentChatRoomId`, its `observe` sibling, and
`DkgSessionDao.getByParentChatRoomId`. The live reads both go elsewhere —
`parentOf` off `GroupKeyState`, `subgroupsOf` off the certificates.

**`ProposedEvent`'s 30326 arm says nothing about parentage.** It shows the path
and the ceremony id. Today that is harmless, because a key state carrying a
parentage is only ever proposed at founding, where the proposer is the
coordinator who chose it. It stops being harmless the moment a key state can be
re-announced.

**`GroupSignedEvent` cannot hold another group's event**, by its foreign key onto
`ChatRoom`. Worth knowing before designing anything that wants to keep evidence
about a group this device is not in.

**The description hint from Phase 6 was never built.**
[subgroups.md](./subgroups.md#the-description-carries-the-parent-too-as-a-hint)
says `SharedKeyDerivation.describe` gains a `Parent group:` line so a member
welcomed into a subgroup after its founding learns it is somebody's child.
`describe(purpose, path)` has no such parameter and emits no such line — so the
"one member and one problem" that section describes is unaddressed even for one
parent. It is also unfixable by this design for a *later* parent, since the
description is baked into the epoch-0 group context and adoption happens after it.

## Rough size

| | files | notes |
|---|---|---|
| wire | 1 | `GroupKeyStateEvent` — list instead of nullable single, in three functions |
| lineage type | 2 | `SubgroupParentage` kept as one pair; a `SubgroupLineage` holding the set and its invariants |
| schema | 5 | `GroupKeyState`, a new entity, `GroupKeyStateDao` (transactional `replace`), `ChatRoomDao`, `MantraDatabase` + migration |
| managers | 2 | `GroupKeyStateManager` (`parentageOf`, `propose`), `SubgroupManager` (`parentsOf`, the two guard sets, adoption proposals) |
| repositories | 4 | two interfaces, two implementations, mostly signature plurals |
| text | 2 | `ProposedEvent`'s 30326 arm — the consent fix — and `ChatMessage`'s 30329 arm, for the offer line |
| UI | 5 | `ChatRoomDetailUIState`, its view model and screen, a parent picker, strings |
| tests | 8 | |

Around two dozen files, and the founding flow is not among them.
