# A long-running chat sync

Today every chat sync in this app is a *pull*: a screen queues a request row, a
pump drains it, the relay answers, the subscription is closed. Nothing arrives
between pulls. This document works out what it takes to keep the chat
subscriptions **open** for as long as the app is active, so relays push new gift
wraps and group events at us instead of us asking, and what has to change in the
existing pipeline before that is even possible.

The target is the two kinds that carry conversation:

- **kind 1059**, NIP-59 gift wraps p-tagged to us — DMs, and the Marmot Welcome
  events that make us a member of a new group.
- **kind 445**, Marmot group events h-tagged with a group id we belong to.

And one hard requirement: the group subscription must widen the moment we join
or create a group, without anyone remembering to call a "subscribe" function.

## What happens today

Three pumps in `SynchronizationViewModel` drain three single-row queues —
broadcast, REQ, negentropy — each row observed with `... ORDER BY createdAt ASC
LIMIT 1`. Screens are producers. Nothing else opens a socket.

| screen | queues |
|---|---|
| [`ChatRoomListViewModel.scheduleSynchronization`](composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/ChatRoomListViewModel.kt:71) | negentropy over `1059 #p=[me] limit 50`, then negentropy over `445 #h=[every group id]`, once per DM relay |
| [`ChatMessageListViewModel.scheduleSynchronization`](composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/ChatMessageListViewModel.kt:113) | for an MLS room: negentropy over `445 #h=[this group]`. For a NIP-17 room: gift wraps p-tagged to the *peer* and authored by us, or the peer's kind-10050 if we lack it |

A negentropy exchange that finds ids we are missing turns them into
`SynchronizeNostrEventRequest` rows, which the REQ pump then fetches in chunks of
500 ids. Both subscription types end the same way: at EOSE the flow completes,
the `finally` sends CLOSE, the semaphore permit is released.

That design is good at what it is for — reconciling a set we already have
against a set the relay has. It is structurally incapable of delivering a message
that arrives one second after EOSE.

## Why we cannot simply stop closing

Five properties of the current pipeline each have to be undone deliberately.
None of them is wrong; all of them assume a subscription is short.

**1. EOSE ends the flow.**
[`completeOnSubscriptionEnd`](composeApp/src/commonMain/kotlin/press/mantra/compose/network/sockets/NostrIncomingMessageExt.kt:42)
is what makes a collector finish at all — the socket's `incomingMessages` is hot
and a filtered view of it never completes on its own. For a live subscription,
EOSE is not the end, it is the boundary between "stored history" and "live
tail", and CLOSED is the only genuinely terminal message.

**2. Every subscription is force-killed at 120 seconds.**
[`SUBSCRIPTION_TIMEOUT`](composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/SynchronizationViewModel.kt:98)
exists so a relay that goes quiet cannot park a slot forever. A live
subscription is exactly the case it is designed to kill.

**3. There are four subscription slots, total, across all relays.**
[`subscriptionSlots`](composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/SynchronizationViewModel.kt:154)
is a `Semaphore(4)`. A permanently-open subscription is a permanently-held
permit. Two live subscriptions would leave the backfill queue with two slots and
a permanent halving of throughput. **Live subscriptions must not draw from this
semaphore** — they need their own budget, and the semaphore's comment ("well
under the ~20-per-connection cap relays commonly enforce") needs re-reading as a
combined budget rather than a queue-only one.

**4. The queue row *is* the subscription identity and the provenance record.**
The subId is the row's UUID, and both `saveNostrEvent` overloads
([`DatabaseNostrRepository.kt:483`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/repository/DatabaseNostrRepository.kt:483),
[`:515`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/repository/DatabaseNostrRepository.kt:515))
take a request object, use its `relayURL`/`level`, and flip its status to
"processed". A live subscription has no row and never finishes. It needs a third
entry point that goes straight to
[`NostrDao.storeNostrEvent`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrDao.kt:141)
with an explicit `relayURL` and `level = 0`.

**5. Nothing reconnects.**
`NostrSocketClientImpl` catches a socket failure, calls `close()`, and fires
`onSocketConnectionClosed`, which only flips a boolean in `relayPoolStatus`.
There is no retry loop anywhere. Today that is invisible: every subscription is
short, and the next queued request re-opens the socket through
`ensureSocketConnectionOrThrow`. A live subscription that outlives a socket drop
is a subscription that has silently stopped, on a socket whose status flag says
so and which nobody reads.

There is a sixth, quieter problem. `_incomingMessages` is a
[`MutableSharedFlow()`](composeApp/src/commonMain/kotlin/press/mantra/compose/network/sockets/NostrSocketClientImpl.kt:53)
with no replay and no buffer, and each frame is emitted from its own `launch`
([`processIncomingMessage`](composeApp/src/commonMain/kotlin/press/mantra/compose/network/sockets/NostrSocketClientImpl.kt:146)).
A message emitted while nothing is collecting is dropped, and messages can be
delivered out of order relative to the wire. For request/response that is
harmless — the collector is attached before the REQ goes out. For a firehose
whose consumer does MLS decryption and SQLite writes, it is not: give the router
a buffered channel and process in arrival order per relay.

## What Wisp does

Wisp (`/home/sigidli/Documents/development/nostr/wisp`) keeps its subscriptions
open for the life of the foreground app. Five mechanisms are worth copying:

- **Stable, meaningful subscription ids.** `"dms"`, `"notif"`, `"grp-<id>"`. Not
  UUIDs. Re-sending a REQ with the same id replaces the filter server-side, which
  makes "widen the filter" a send rather than a close-and-reopen.
- **The REQ text is retained per relay.** `activeSubscriptions: Map<relayUrl,
  Map<subId, reqJson>>`, and `resyncSubscriptions(relay)` re-sends all of them
  when that relay reconnects (`relay/RelayPool.kt`).
- **A per-relay subscription budget.** `SubscriptionTracker` enforces a soft cap
  of 20, with a priority prefix list (`"dms"`, `"notif"`, `"grp-"`, …) that
  bypasses the cap so a transient feed subscription can never crowd out DMs.
- **Explicit reconnect policy.** `RelayLifecycleManager` observes connectivity
  and app pause/resume, debounces, and drives `reconnectAll()`; on reconnect only
  the long-lived prefixes are retained and everything transient is dropped.
- **One router, not one collector per request.** `viewmodel/EventRouter.kt`
  dispatches by subscription id.

And one detail that is a direct warning to us: Wisp deliberately does **not**
put a `since` on its kind-1059 filter, because NIP-59 randomizes `created_at`
into the past.

## The shape to build

### A live tier beside the queue, not inside it

A `LiveSubscriptionManager` owned at the same level as `SynchronizationViewModel`
(the application scope, not a screen), holding one piece of state:

```kotlin
// what we want to be subscribed to, per relay
data class LiveSubscription(val subId: String, val relayUrl: String, val filters: List<Filter>)
```

and doing one thing: **reconcile desired state against what has actually been
sent to each socket.** Recompute the desired set, diff it against the sent set,
send a REQ for anything added or changed, send CLOSE for anything removed. Every
trigger — a new group, a relay reconnect, an app resume, a relay-list change —
becomes a call to the same `reconcile()`. That is the property that makes this
maintainable: there is no "subscribe" path and "resubscribe" path that can drift.

The sent-set belongs in `RelayPool`, next to `socketClients`, because that is
where a reconnect is observed. Mirror Wisp: keep the serialized REQ per
`(relayUrl, subId)` and re-send on connect.

### The two filters, and their timestamps

```kotlin
// live-giftwrap
Filter(kinds = listOf(GiftWrapEvent.KIND), tags = mapOf("p" to listOf(myPubkey)))

// live-groups-<n>
Filter(kinds = listOf(GroupEvent.KIND), tags = mapOf("h" to groupIdChunk))
```

The two kinds need opposite treatment on `since`, and this is not a matter of
taste — it is visible in our own outbound code.

Gift wraps are stamped with
[`TimeUtils.randomWithTwoDays()`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/repository/DatabaseChatRepository.kt:344),
so a wrap published *now* can carry a `created_at` up to two days in the past.
A live filter with `since = now` would silently drop a large fraction of
genuinely new messages, and the failure mode is "some DMs just don't arrive" —
the worst kind to debug. Either omit `since` entirely (gift wraps are low volume
and `storeNostrEvent` already no-ops on a known id) or floor it at
`now - 2 days` and accept the redelivery.

Group events use
[`TimeUtils.now()`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/MarmotOutboundDao.kt:578).
A watermark is safe there: `since = (newest 445 we hold for these groups) - slack`.
Keep the slack generous (minutes, not seconds) — relay clocks drift, and the
cost of re-receiving a handful of 445s is a few no-op `storeNostrEvent` calls.

`#h` has to be chunked. One subscription per group is the simple option and what
Wisp does for NIP-29, but Marmot groups here are all on the same DM relay set, so
one subscription carrying N group ids is far cheaper in sockets and slots. Chunk
at a conservative width (~100 ids) across `live-groups-0`, `live-groups-1`, … and
accept that a chunk boundary shift re-sends one REQ.

### Routing

One collector per socket, attached for the life of the socket, dispatching on
subscription id prefix — the `EventRouter` shape. That collector is also what
fixes problem six above: it is always attached, so nothing is dropped for want
of a subscriber, and it can impose ordering and back-pressure in one place.

Events off a live subscription go to the new request-free `saveNostrEvent`, and
from there through the same `storeNostrEvent` → `MarmotInboundManager` path as
everything else. **No inbound processing changes.** A 1059 carrying a Welcome
still lands in the branch at
[`NostrDao.kt:720`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrDao.kt:720);
a 445 still reaches
[`processGroupEvent`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrDao.kt:415).
Only the delivery mechanism is new.

### Keeping the group filter current

This is the requirement that decides the design, and it is the easy part —
provided we derive the filter from the database instead of from the join sites.

```kotlin
chatRepository.observeChatRoomListByPublicKey(publicKey)
    .map { rooms -> rooms.filter { it.chatRoom.mlsGroupState != null && it.chatRoom.leftGroupAt == null }
                         .map { it.chatRoom.id }.sorted() }
    .distinctUntilChanged()
    .debounce(500.milliseconds)
    .collect { groupIds -> live.setGroupIds(groupIds); live.reconcile() }
```

`ChatRoomListViewModel` already collects this exact Flow to render the list. The
difference is where it is collected: at application scope, so it keeps running
when no chat screen is open.

Driving it off the DB rather than off the join sites matters because there are
several ways a group id appears, and they are not all in view models:

- we create a group (`MarmotOutboundDao`),
- we are added to one — the Welcome arrives inside a gift wrap and is processed
  deep inside `NostrDao.storeNostrEvent`, on the *inbound* path,
- membership shifts under us via a commit
  (`MarmotInboundManager.processGroupMembershipChanges`),
- and we leave one (`leftGroupAt`).

Only the first is a place a developer would naturally think to call
`subscribeToGroup()`. Observing the room list catches all four, and it closes the
loop cleanly: **a Welcome arrives on the live gift wrap subscription → a
`ChatRoom` row is written → the Flow re-emits → the group filter widens → the
first message in that new group arrives without anyone opening the chat.**

Debounce is not optional. Joining a group writes the room, its participants, and
placeholder profiles in quick succession; without it we would re-send the group
REQ several times per join.

### Lifecycle

Mantra has no app lifecycle observer at all today —
[`MainActivity`](composeApp/src/androidMain/kotlin/press/mantra/android/MainActivity.kt)
only calls `setContent`. Long-running subscriptions make one necessary:

- **background:** stop the live subscriptions (CLOSE, or just drop the sockets).
  Holding a socket open behind a doze window achieves nothing except battery.
- **foreground:** reconnect and reconcile. Assume the server-side subscription is
  gone — Wisp's `forceReconnectAll` exists precisely because a socket that
  survives an OS sleep reports `isConnected == true` while being functionally
  dead — and queue a bounded catch-up negentropy pass for the gap.
- **connectivity change:** reconnect, debounced, with the suppression window
  Wisp uses to avoid a resume and a network-change event double-reconnecting.

The foreground catch-up is what preserves correctness across the gap. Live
subscriptions cover the online window; negentropy covers everything else.

## What the screens stop doing, and what they keep

Once the live tier exists:

- `ChatRoomListViewModel.scheduleSynchronization` — drop both queues on open.
  Keep the code path behind an explicit pull-to-refresh.
- `ChatMessageListViewModel.scheduleSynchronization` — drop the `mlsMessages`
  branch entirely; it is a strict subset of `live-groups-*`. Keep the
  `ChatMessageRelayListEvent` lookup: that is *discovery* (which relay does this
  peer read from), not message sync, and it still has to happen on open.
- Everything not chat — profiles, follows, feeds, key packages — is untouched.

One thing to fix while in here, since it is one of the per-chat syncs we are
proposing to stop firing: the `"sent-messages"` filter
(`kinds=[1059], authors=[me], #p=[peer]`, at
[`ChatMessageListViewModel.kt:113`](composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/ChatMessageListViewModel.kt:113)
and [`NostrDao.kt:643`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrDao.kt:643))
**cannot match anything.** Gift wraps are signed with a fresh throwaway key
([`val wrapperKeyPair = KeyPair()`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/repository/DatabaseChatRepository.kt:312)),
so `authors = [myPubkey]` matches no wrap this app has ever published. It also
turns out to be unnecessary: `createNip17ChatRoom` puts the user in their own
participant list
([`NostrNip17Dao.kt:50`](composeApp/src/commonMain/kotlin/press/mantra/compose/database/dao/NostrNip17Dao.kt:50)),
so we wrap a copy to ourselves and the account-wide `#p=[me]` subscription picks
our own sent messages up on every device. Delete the filter rather than port it.

## Negentropy's remaining job

Live subscriptions replace *polling*, not *reconciliation*. Negentropy is still
the right tool for:

- first login / restore, where the local set is empty,
- the foreground catch-up after a background gap,
- "load older messages" in a chat,
- and repairing a relay that was unreachable while we were online.

The distinction to hold onto: a live REQ answers "what is new since I connected";
negentropy answers "what do you have that I don't". Neither subsumes the other,
and the queue and its three pumps stay exactly as they are.

## Traps, in the order they will bite

1. **Reusing the `subscriptionSlots` semaphore.** Two permanent permits out of
   four halves backfill throughput forever. Give the live tier its own budget and
   read the two together against the relay's real cap.
2. **`completeOnSubscriptionEnd` on a live flow.** It will complete at the first
   EOSE and the subscription will look established while delivering nothing.
   The live variant must terminate on CLOSED only.
3. **`since = now` on kind 1059.** Randomized wrap timestamps mean silent,
   partial message loss. See above.
4. **Treating CLOSED as "retry now".** `isBackPressure` already exists in
   `SynchronizationViewModel` and should be reused: a rate-limited CLOSED on a
   live subscription needs exponential backoff, an auth-required CLOSED needs the
   AUTH flow (which the app does not implement yet — worth knowing before
   pointing this at a relay that requires it), and only an unsupported-filter
   CLOSED should fall back to a queued REQ.
5. **A NOTICE has no subscription id.** It is already admitted to every
   collector by `filterBySubscriptionId`; with a permanent collector attached,
   every notice reaches it forever. Handle it once in the router, not per
   subscription.
6. **Duplicate delivery.** `storeNostrEvent` no-ops on a known id, so duplicate
   1059s are cheap. Duplicate *445s* are not automatically cheap —
   `MarmotInboundManager` maintains epoch state and a commit tracker; confirm the
   re-delivery path is idempotent before widening the `since` slack.
7. **Relay list changes.** `RelaysSocketManager.updateRelayPools` calls
   `relayPool.changeRelays`, which closes sockets. Live subscriptions must be
   reconciled after that, or they quietly vanish on any relay edit.
8. **The DM relay set is one relay.** `Relays.DefaultDMRelayList` is
   `[wss://ephemeral.mantra.press]`. Every point above about per-relay budgets is
   currently a budget of one socket — which makes this cheap to build and makes a
   single relay outage total. Worth widening the set at the same time.

## Suggested order

1. **Reconnect first.** A supervised reconnect loop in `NostrSocketClientImpl`
   plus per-relay REQ retention and resync in `RelayPool`. This is a strict
   improvement on its own, before any live subscription exists.
2. **The router.** One permanent collector per socket, dispatching by subId
   prefix; the request-free `saveNostrEvent`. Existing pumps keep working.
3. **`live-giftwrap`.** One subscription, one relay, no `since`. Verifiable by
   sending yourself a DM from another client with the chat list closed.
4. **`live-groups-*`,** driven off the room-list Flow. Verify by having a second
   device add this one to a group and watching messages land with no chat screen
   ever opened.
5. **Lifecycle + foreground catch-up.**
6. **Then, and only then,** delete `scheduleSynchronization` from the two chat
   view models.
