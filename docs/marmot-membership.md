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

## The flag is not reachable from callers

`inviteMemberToChatRoom` hardcodes `isOneMemberInitialGroupCreation = false`, and
`ChatRepository.inviteMember` does not expose it at all. Only
`createMlsDirectMessageChatRoom` passes `true`, for the single peer of a DM.

So every group invite — `SelectChatRoomTypeViewModel.inviteMembers` when a room is
created, and `DkgRitualViewModel.inviteAdmins` for the `#admins` room — takes the
deferred path, **including the first one, when the group is still just the
creator**.

For that first invite this is wrong twice over:

- the commit has no audience. Nobody else is a member, and nobody outside the
  group can decrypt it. It is noise on the relay.
- the Welcome is then gated on a relay acknowledging that pointless commit. If the
  ack never arrives, the first invitee never receives anything.

That second point sharpened when `Relays.DefaultDMRelayList` became a single relay:
every Welcome now depends on one relay acking.

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

Now consider inviting two admins back to back under the current behaviour:

1. invite admin 1 → commit 1 broadcast immediately, Welcome 1 waits for ack 1
2. invite admin 2 → commit 2 broadcast immediately, Welcome 2 waits for ack 2

Both commits are on the wire before either Welcome. If commit 2 reaches admin 1
before Welcome 1 does — different transports, no ordering guarantee, one is a gift
wrap and the other a kind:445 — admin 1 drops it and is stuck an epoch behind. The
coordinator sees nothing wrong: both invites returned successfully.

## Options

### 1. Expose the flag, pass `true` for the first invite

Smallest change. Removes the pointless commit and the ack dependency for the first
invitee. Leaves n−1 sequential commits, and leaves every other caller unfixed
unless they are each updated.

### 2. Derive it in the DAO (recommended as the immediate fix)

`inviteMemberToChatRoom` can decide for itself:

```kotlin
isOneMemberInitialGroupCreation = mlsGroup.members().size == 1
```

`MlsGroup.members()` already exists. No signature changes, and it fixes every
caller at once — group creation as well as the `#admins` room.

The condition is right for any group size, not just DMs. "The group has nobody to
inform" is true exactly once, on the first invite, whether the group will end up
with 2 members or 30:

| invite  | `members().size` | branch                       | correct because           |
|---------|------------------|------------------------------|---------------------------|
| admin 1 | 1                | immediate Welcome, no commit | nobody to inform          |
| admin 2 | 2                | commit + deferred Welcome    | admin 1 must advance      |
| admin 3 | 3                | commit + deferred Welcome    | admins 1–2 must advance   |

Commit 2 is encrypted with `commitResult.preCommitExporterSecret` — the epoch-1
secret, which admin 1 received in their Welcome — so they can decrypt it and
advance.

This also *narrows* the race above rather than widening it. Welcome 1 is sent
before commit 2 exists at all, so admin 1 is already at epoch 1 when commit 2
arrives.

It does not close the race. For n ≥ 3 the window between Welcome 1 and commit 2
still exists, and losing it is still silent.

### 3. Batch every add into one commit

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
