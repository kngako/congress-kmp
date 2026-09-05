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

Deriving the flag narrows this: Welcome 1 is now sent before commit 2 exists at
all, so admin 1 is already at epoch 1 when it arrives.

**It does not close it.** For n ≥ 3 the window between Welcome 1 and commit 2
remains, and losing it is still silent. See "Batching every add" below.

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

This is the remaining work, and the only thing that closes the race rather than
narrowing it.

`MlsGroup.addMember` is `proposeAdd` + `commit()` in one call, but those are
separate functions and `pendingProposals` is a list. Staging every member with
`proposeAdd` and issuing a single `commit()` gives:

- one commit, which nobody has to have already joined to process
- n Welcomes carrying identical state
- no intermediate epoch for anyone to miss, so the race has nothing to lose

This is the right shape whenever the whole membership is known up front, which is
exactly the case for a room created from a completed key ceremony.

The cost is bookkeeping. `MarmotCommitResult` assumes one peer per commit —
`peerKeyPackageEventId` is singular — so batching means changing that model and
the ack-triggered fan-out in `DatabaseNostrRepository` to deliver several Welcomes
from one acknowledgement.

## Other things that bite

**Invites are sequential and each advances the epoch.** The room must be re-read
from the database between invites; a snapshot taken before the previous invite
builds its commit on state the group has already left. Both `inviteMembers` and
`inviteAdmins` do this, and both say so in a comment, because it is not obvious
and the symptom is a conflicting commit rather than an error.

**A member with no published key package cannot be added.** A Marmot invite needs
the invitee's `MarmotKeyPackage`. Both call sites look it up with a timeout and
collect the ones that failed. Today that only reaches the log — the
`TODO: Update status of participant Invitation.PENDING -> Invitation.SENT` at the
Welcome delivery site is the same gap seen from the other end.

**`deliveryWelcome` uses `Relays.DefaultDMRelayList`, not the room's relays.**
There is a `TODO: Get localChatRoom relays...` on the ack-triggered call site.
