# Adding members to a Marmot group

How members join an MLS group in this app, why the current shape has a silent
failure mode, and what to do about it.

This is the part of Marmot most likely to waste a day: everything compiles, the
invite reports success, and a member simply never appears. The reason is never in
the invite code.

## The two paths through `inviteMember`

`MarmotOutboundDao.inviteMember` branches on `isOneMemberInitialGroupCreation`:

**`true` — the group is only its creator.**
The Welcome goes out immediately via `deliveryWelcome`, which builds it and
inserts a `GiftWrapPayload`. No commit event is broadcast and no
`MarmotCommitResult` is stored. Correct, because there is nobody else in the group
who needs to learn anything.

**`false` — the group already has members.**
A commit event (kind 445, ephemeral signer, h-tagged with `nostrGroupId`) is
broadcast so existing members advance their epoch. The Welcome is *not* sent.
Instead `commitResult.welcomeBytes` is stored on a `MarmotCommitResult`, and
`DatabaseNostrRepository` picks it back up when the relay acknowledges the commit
and only then calls `deliveryWelcome`.

The deferral is deliberate: the invitee must not join an epoch the existing
members have not reached yet.

## Which branch is taken, and why nobody chooses it

`inviteMember` derives it:

```kotlin
val isOneMemberInitialGroupCreation = mlsGroup.members().size == 1
```

Read before `addMember` advances the tree, and `members()` skips empty leaves so it
stays right for a group that has had members removed.

No caller passes it, deliberately. None of them is in a better position to know,
and both that tried got it wrong: `inviteMemberToChatRoom` hardcoded `false`, so
**every group's first invite took the deferred path** even though the group was
still just its creator. That was wrong twice over — the commit had no audience,
and the Welcome was then gated on a relay acknowledging it. With
`Relays.DefaultDMRelayList` down to a single relay, that meant the first invitee
of every group depended on one ack for an event nobody needed.

## Why this fails silently

`MarmotInboundManager` refuses anything from a future epoch outright, on both
wire formats:

```
PrivateMessage epoch N is ahead of local epoch M; ignoring
Commit epoch N is ahead of local epoch M; ignoring
```

There is no queue and no replay for either. A commit that arrives before its
recipient's Welcome is **dropped, not deferred**, and that member never advances.
`EPOCH_RETENTION_WINDOW` (5) retains *past* epochs so late messages can still be
decrypted; it does nothing for messages from ahead.

Under the old hardcoded `false`, inviting two admins back to back went:

1. invite admin 1 → commit 1 broadcast immediately, Welcome 1 waits for ack 1
2. invite admin 2 → commit 2 broadcast immediately, Welcome 2 waits for ack 2

Both commits were on the wire before either Welcome. If commit 2 reached admin 1
before Welcome 1 did — different transports, no ordering guarantee, one a gift
wrap and the other a kind:445 — admin 1 dropped it and was stuck an epoch behind,
while the coordinator saw two successful invites.

Deriving the flag narrowed this, but did not close it: for n ≥ 3 the window
between Welcome 1 and commit 2 remained.

**Batching closes it.** When the membership is known up front, every member goes
into one commit, so no member ever has to process a commit for an epoch they were
not yet in — the race has nothing left to lose. See below.

## The condition holds at any group size

"The group has nobody to inform" is true exactly once, on the first invite, whether
the group ends up with 2 members or 30:

| invite  | `members().size` | branch                       | correct because           |
|---------|------------------|------------------------------|---------------------------|
| admin 1 | 1                | immediate Welcome, no commit | nobody to inform          |
| admin 2 | 2                | commit + deferred Welcome    | admin 1 must advance      |
| admin 3 | 3                | commit + deferred Welcome    | admins 1–2 must advance   |

Commit 2 is encrypted with `commitResult.preCommitExporterSecret` — the epoch-1
secret, which admin 1 received in their Welcome — so they can decrypt it and
advance.

## Batching every add into one commit

`MarmotOutboundDao.addMembersToChatRoom` stages every member with `proposeAdd` and
issues a single `commit()`. `MlsGroup.addMember` is just those two in one call, and
`pendingProposals` is a list, so nothing in MLS objected.

One commit produces **one** Welcome: `buildWelcome` emits an `EncryptedGroupSecrets`
per added member, and each joiner finds its own entry by key package reference. The
blob is shared; delivery is still per peer, because each Welcome event is tagged
with that peer's key package.

Both callers that know their membership up front now use it —
`SelectChatRoomTypeViewModel.inviteMembers` at room creation, and
`DkgRitualViewModel.inviteAdmins` for the `#admins` room.

### Why this needed no schema change

Batching at creation time means the single commit happens while the group is still
only its creator. That takes the immediate-Welcome branch: no commit is broadcast,
and `MarmotCommitResult` is never written. The bookkeeping that assumes one peer per
commit is simply not on the path.

So `addMembersToChatRoom` batches **only** when `members().size == 1`, and falls
back to inviting sequentially otherwise. Batching into a group that already has
members would take the deferred branch, where `MarmotCommitResult.peerKeyPackageEventId`
is singular and `DatabaseNostrRepository`'s ack-triggered delivery expects one
Welcome. Making that work means holding a list of peers there and fanning out on
acknowledgement — still outstanding, and only needed for adding several members to
an established group, which nothing currently does.

### Ordering within the batch

The group state is persisted after `commit()` and before any Welcome is delivered,
so a crash between the two leaves the group at the epoch the Welcomes describe
rather than one behind it.

## What the room is told

An invite writes three kinds of line into the room's transcript, none of which
travels — see `ChatMessage.MEMBERSHIP_TYPES`. They exist because the deferral
above is invisible from anywhere else: the screen that asks for an invite closes
the moment the commit is made, and everything that can still go wrong goes wrong
after that.

| line                        | written by                              | says                                  |
|-----------------------------|-----------------------------------------|---------------------------------------|
| `TYPE_MEMBER_INVITED`       | `inviteMember`, `addMembersToChatRoom`  | the membership change was made        |
| `TYPE_MEMBER_INVITE_SENT`   | `DatabaseNostrRepository`               | the deferred Welcome went on the wire |
| `TYPE_MEMBER_INVITE_FAILED` | `deliveryWelcome`, `DatabaseChatRepository` | it did not, and why              |

The invite line is written **when the invite is made**, inside the caller's
transaction. Both halves of that matter: written any later and an invite waiting
on an ack that never comes leaves the room showing nothing, which is what this
looked like before; written outside the transaction and an invite that does not
survive `addMember` leaves the room claiming one was made.

Only the immediate branch's Welcome goes out in the same breath as the invite, so
only the deferred branch owes a second line. Where the two are one event, one line
is the whole truth — and `createdAt` is stored to the second, so a second line
would be one the transcript could not reliably order after the first anyway.

A refusal is the awkward case, because rolling the transaction back is right and
takes the account of it with it. `DatabaseChatRepository.inviteMember` catches,
writes the failed line from outside the transaction, and rethrows — the throw is
what puts a message on the invite screen now, the line is what is still there
tomorrow.

## Other things that bite

**Sequential invites each advance the epoch.** Where they still happen — the
fallback in `addMembersToChatRoom` for a group that already has members, and any
direct `inviteMember` call — the room must be re-read from the database between
them. A snapshot taken before the previous invite builds its commit on state the
group has already left, and the symptom is a conflicting commit rather than an
error.

**A member with no published key package cannot be added.** A Marmot invite needs
the invitee's `MarmotKeyPackage`. Both call sites look it up with a timeout and
collect the ones that failed. That is still only a log and a returned list —
nothing puts it in the room, because it happens before there is an invite to write
a line about. The `TODO: Update status of participant Invitation.PENDING ->
Invitation.SENT` at the Welcome delivery site is the same gap seen from the other
end: the transcript lines above say what happened, but a Participant row still
carries no state saying where its invite got to.

**`deliveryWelcome` uses `Relays.DefaultDMRelayList`, not the room's relays.**
There is a `TODO: Get localChatRoom relays...` on the ack-triggered call site.
