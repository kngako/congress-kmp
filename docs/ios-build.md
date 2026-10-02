# Bringing up the iOS target

What it takes to build and run Mantra on an iPhone, phased, following the route
the Curated fork already walked. Every one of its findings is diagnosed and
reproducible here, so the work is porting a known answer rather than
re-discovering it.

The headline is that almost nothing is missing. The `iosArm64` and
`iosSimulatorArm64` targets are **already declared** in
[composeApp/build.gradle.kts:36](../composeApp/build.gradle.kts), gated behind
`OperatingSystem.current().isMacOsX` the way everything in the composite chain is;
`composeApp/src/iosMain` holds ten `actual` files that match `jvmMain`'s ten
one-for-one, so no `expect` is unimplemented; `commonMain` has no `java.*` or
`javax.*` import anywhere; quartz 1.14.0 publishes `iosArm64` and
`iosSimulatorArm64` klibs; and `iosApp/` is byte-identical to Curated's apart from
brand tokens and one icon file, Xcode build phase and all.

What stands in the way is five concrete defects, which Curated hit in the order a
build hits them — configure, compile, link, connect, sign — and fixed in eight
commits.

## The eight commits, and what each one is here

| Curated | what it did | how it lands here |
|---|---|---|
| `94de86a3`, `cbf23640`, `64b37447` | three gitlink bumps of `lightning-kmp-app`, carrying a secp256k1-kmp build fix | the same three bumps, verbatim — Mantra is pinned at their exact parent |
| `987df2c8` | `@ConstructedBy`, `@Throws(CancellationException)`, an unused import, `TEAM_ID` | the same, minus `TEAM_ID`, which is account-specific |
| `aa9312f0` | comma-free `commonTest` names, plus a disabled compiler phase | **rewritten**, not cherry-picked — see Phase 3 |
| `b9140c6a` | the Darwin ktor engine | near-verbatim; the shape it assumes is the shape here |
| `bd47c841` | app icon and `MARKETING_VERSION` | needs Mantra artwork |
| `a673c85e` | `versionName` | a release decision, not a port |

## What is already done for you

**The pin lines up exactly.** Mantra records `lightning-kmp-app` at
`84cc44c855e3d173e26016b807c449153a3af597`. That is the *parent* of Curated's
`94de86a3`. The three bumps are therefore a straight walk along the same gitlinks —
`84cc44c` → `2dcd4fb` → `db15e59` → `e0d2b82` — with no rebase and no divergence
to reconcile.

**The library's own iOS gating is already right.** `lightning-kmp-app/library`
declares `iosArm64()` and `iosSimulatorArm64()` under the same `isMacOsX` guard,
and sets `kotlin.native.enableKlibsCrossCompilation=true`. Nothing in the outer
build has to teach it about apple targets.

**`commonMain` is clean, and the publish surface is small.** One `HttpClient {`
construction in the whole module
([NostrSocketClientFactory.kt:10](../composeApp/src/commonMain/kotlin/press/mantra/compose/network/sockets/NostrSocketClientFactory.kt)),
no explicit Coil `ImageLoader` anywhere, six `@Throws` annotations on the publish
path. That is the same shape Curated's fixes were written against, so Phases 2 and
4 are small and exact rather than a search.

**The audit does not look at tests.** `docs/scripts/m3-audit.sh` scans
`composeApp/src/commonMain/kotlin/press/mantra/compose/ui` only, so Phase 3's sweep
over test names cannot trip `./gradlew check`.

## What actually blocks it

Five things, independent of each other except for the order the build reveals them
in:

1. **The chain will not configure on a mac.** secp256k1-kmp's `.def` declares
   `staticLibraries` for every target and the cinterop tool refuses to emit a klib
   while the archive is absent. Off a Linux host the Linux archives are never
   built, so the IDE sync and configuration fail with `cinterop file ... does not
   exist`. The fix — `9bcae25` in secp256k1-kmp, hardened by `8adf691` — stubs those
   archives under `if (!currentOs.isLinux)`. Its commit subject says "linux
   cinterop", which reads backwards from here: **it is the fix that makes the chain
   work on a mac**, and it is a strict no-op on Linux.

2. **Room cannot construct the database on native.**
   `MantraDatabaseConstructor` already exists in `commonMain`, `expect`,
   `@Suppress`ed and referenced by nothing. Without `@ConstructedBy` naming it,
   Room's KSP generates no `actual`, and on a target with no reflection there is no
   other way in.

3. **Swift cannot see a cancellation as a cancellation.** Six `@Throws` sites on
   the publish path name `NostrPublishException` and not
   `kotlin.coroutines.cancellation.CancellationException`, so a cancelled publish
   crosses the ObjC boundary as an unexpected throw.

4. **The iOS link tasks fail, twice over.** `linkDebugTestIosArm64` is rejected by
   Kotlin/Native over backticked test names containing a comma (`Name contains
   illegal characters: ','`), and `linkReleaseFrameworkIosArm64` crashes Kotlin
   2.4.10's release backend inside `StaticInitializersOptimization`.

5. **Nothing secure connects on a device.** CIO has no TLS implementation on
   Kotlin/Native — every `wss://` and `https://` call dies at the handshake with
   `TLS sessions are not supported on Native platform`. The relay sockets and
   Coil's image fetcher both sit on a default-engine client.

Everything below is ordered by that list.

---

## Phase 0 — bootstrap, and a recorded baseline

Nothing is changed in this phase. Its output is a green build to compare against,
because four of the five defects above are invisible until a mac is in the loop and
you do not want to be guessing afterwards which failure you introduced.

**In this worktree, initialise the chain first.** Four nested submodules —
`lightning-kmp-app` → `experimental/lightning-kmp` → `experimental/bitcoin-kmp` →
`experimental/secp256k1-kmp` (→ `native/secp256k1`) — are unpopulated, and gradle
dies early and confusingly with `Project with path ':library' not found`:

```bash
git -c protocol.file.allow=always submodule update --init --recursive
```

`protocol.file.allow=always` is required: the recorded url for `lightning-kmp-app`
is the main checkout's path, and git refuses `file` transport for submodules by
default. The last level (`native/secp256k1`, fetched from GitHub) can stall for
ten minutes or more; let the recursive call do what it can and finish that one
level from the main checkout's copy. Trust `git -C <level> rev-parse HEAD` per
level rather than `git submodule status --recursive`, which lies after a
per-level url override.

Then record the baseline:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:jvmTest :lightning-kmp-app:library:jvmTest
```

Note the jvmTest count and that it is green. `IdentitySwitchJvmTest`'s "last
profile opened" case fails about one run in nine on a 5s DataStore timeout on a
clean tree too — if you see exactly that, re-run before believing it.

**Deliverable:** no commit. A noted baseline.

---

## Phase 1 — advance the library chain

The three gitlink bumps, kept as three commits so each one says what it carries.
Walk the outer pin, and let the nested gitlinks come with it.

```bash
git -C lightning-kmp-app fetch https://github.com/kngako/lightning-kmp-app.git 'refs/heads/*:refs/remotes/github/*'
git -C lightning-kmp-app checkout --detach 2dcd4fbe1513421d100a4485583f167b1fdcd47c
git -c protocol.file.allow=always -C lightning-kmp-app submodule update --init --recursive
git add lightning-kmp-app && git commit
```

In a worktree the submodule's `origin` is the **main checkout's copy**, not GitHub,
so `git fetch origin` shows nothing new — fetch the `.gitmodules` url directly, as
above. Each bump moves `experimental/lightning-kmp` too, which moves `bitcoin-kmp`,
which moves `secp256k1-kmp`, so re-run `submodule update --init --recursive` inside
`lightning-kmp-app` after each checkout or the chain is left mid-walk.

The three, in order:

| commit | pin | what it carries |
|---|---|---|
| 1 | `2dcd4fb` | secp256k1-kmp `9bcae25` — the stub archives that let the chain configure off Linux |
| 2 | `db15e59` | the same fix merged into the `threshold` branches, so the nested gitlinks name branch tips rather than side commits |
| 3 | `e0d2b82` | secp256k1-kmp `8adf691` — registers the stub task with `tasks.register` instead of the delegate provider, which Gradle 9 requires |

Commits 2 and 3 look like bookkeeping and are not: without 3 the stub task is
never registered under a name, and without 2 the pins sit on commits that were
rebased away.

**Verify, on Linux.** The whole fix is behind `if (!currentOs.isLinux)`, so Linux
must be *unchanged*:

```bash
ANDROID_HOME=$HOME/Android/Sdk ./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:jvmTest :lightning-kmp-app:library:jvmTest
```

Same test count, same green, as Phase 0. The library's own ~159 tests run inside
the composite build and are the thing that proves the wiring still gives it what it
expects.

**Verify, on a mac.** `./gradlew :composeApp:tasks` configures at all, and
`:composeApp:compileKotlinIosArm64` resolves `fr.acinq.phoenix:lightning-kmp-app`.
That is the phase's actual purpose.

> Suggested message, commit 1:
> `build: carry secp256k1-kmp's fix for configuring off a linux host`
> — and say in the body that the upstream subject reads the other way round.

---

## Phase 2 — declare the Room constructor and cancellation to Swift

Four small edits, one commit. All of it compiles on Linux.

**`@ConstructedBy`.** Add the import and the annotation above
[MantraDatabase.kt:369](../composeApp/src/commonMain/kotlin/press/mantra/compose/database/MantraDatabase.kt)'s
`@ColumnTypeConverters`:

```kotlin
import androidx.room3.ConstructedBy
…
@ConstructedBy(MantraDatabaseConstructor::class)
@ColumnTypeConverters(MantraConverters::class)
abstract class MantraDatabase: RoomDatabase() {
```

The `expect object` it names already exists and needs no edit. This is the one
change in the whole plan that **also changes the JVM and Android builds**: Room's
KSP starts generating an `actual` for every target rather than none, so the
annotation that unblocks iOS is the annotation most likely to move something
underneath `:composeApp:jvmTest`. Run it and read the output rather than assuming.

**Cancellation on the publish path.** Add
`kotlin.coroutines.cancellation.CancellationException::class` to the six existing
`@Throws`, and give `EventPublishTransport.publishEvent` one it does not have:

- [RelayPool.kt:289](../composeApp/src/commonMain/kotlin/press/mantra/compose/network/relays/RelayPool.kt)
- [RelaysSocketManager.kt:103](../composeApp/src/commonMain/kotlin/press/mantra/compose/network/relays/RelaysSocketManager.kt) and `:118`
- [NostrPublisherRepository.kt:33](../composeApp/src/commonMain/kotlin/press/mantra/compose/repository/NostrPublisherRepository.kt), `:55` and `:66`
- [EventPublishTransport.kt:31](../composeApp/src/commonMain/kotlin/press/mantra/compose/network/relays/EventPublishTransport.kt) — new, naming `NostrPublishException` as well

Keep the fully-qualified style the surrounding annotations already use.

**One unused import.** `import kotlin.native.Platform` in
[Platform.ios.kt](../composeApp/src/iosMain/kotlin/press/mantra/compose/Platform.ios.kt)
shadows the app's own `Platform` interface. Delete the line. Nothing on Linux
compiles `iosMain`, so this one is carried on Curated's word and the compiler's,
later.

**Verify, on Linux:** `:composeApp:compileDebugKotlinAndroid :composeApp:jvmTest`.
**On a mac:** `:composeApp:compileKotlinIosArm64`.

> `feat(ios): declare the Room constructor and publish cancellation to swift`

---

## Phase 3 — unbreak the iOS link tasks

Two unrelated link failures, one commit, because they are the same symptom from the
outside: `./gradlew :composeApp:linkDebugTestIosArm64` and
`:composeApp:linkReleaseFrameworkIosArm64` both fail.

### The comma names — rewrite, do not cherry-pick

Curated's `aa9312f0` touched 38 `commonTest` files. **Ten of them do not exist
here** (`GroupEventProposalTest`, `CuratedSchemaEventTest`, `CuratedEntryEventTest`,
`CuratedSchemaTemplateTest`, `GroupCuratedEntryTest`, `GroupCuratedSchemaTest`,
`GroupPostTest`, `CuratedSuggestionTest`, `MarmotRoomIdentityTest`,
`MarmotPayloadBoundsTest`, `FrostSigningMembersTest`) — they belong to the fork's
curated-list and Marmot-v2 lines, neither of which Mantra has pulled. Meanwhile
Mantra has test files Curated does not: `ProjectEventTest`,
`ChapterProposalSizeTest`, `UploadChaptersStateTest`, `UploadReviewTest`,
`SubgroupLineageTest`, `MarkdownBlockTest`, `MarkdownProducerTest`,
`ChapterTextTest`.

So do the sweep over **Mantra's own tree**: 84 backticked `fun` names across 35
files in `composeApp/src/commonTest`. Every comma in every one of them is followed
by exactly one space — verified, no exceptions — so the rewrite is total and
unambiguous:

```bash
grep -rl 'fun `[^`]*,[^`]*`' composeApp/src/commonTest --include='*.kt' \
  | xargs perl -i -pe 's/(fun `[^`]*`)/($x=$1)=~s{, }{ }g; $x/ge'
```

Then read the diff. A name losing its comma loses a little of its rhythm and none
of its meaning (`read as its kind, tags and content` → `read as its kind tags and
content`); if any of the 84 reads badly afterwards, reword that one by hand rather
than leaving a comma behind.

**Leave `jvmTest` alone.** It has 138 comma names and Kotlin/Native never compiles
it. Curated left it alone for the same reason, and widening the sweep would put 138
cosmetic edits in a commit that is about a link failure.

### The release backend crash

Append to `composeApp/build.gradle.kts`, after the `kotlin { }` block and before
`android {` at line 161 — the same place Curated put it:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    if (name.startsWith("linkRelease")) {
        toolOptions.freeCompilerArgs.add("-Xdisable-phases=RemoveRedundantCallsToStaticInitializersPhase")
    }
}
```

With the comment Curated wrote, which is the valuable part: the pass is an LTO
optimization, so only optimized links reach it (which is why the debug test binary
links fine); it assumes every function in the call graph has at least one statement
in its body; something in this module's graph arrives with an empty one; and
disabling it costs the redundant initializer calls it would have removed and
nothing else.

`tasks.withType<KotlinNativeLink>()` matches nothing on Linux, so this is inert
there — but the class resolves from the KMP plugin's classpath on any host, so the
script still has to compile. `:composeApp:compileDebugKotlinAndroid` proves that
much.

**Expect this one not to be the end of it.** The crash is a property of the call
graph, and Mantra's graph is a superset of Curated's — projects, chapter uploads,
multi-parent subgroups, the markdown producer. The same phase may crash somewhere
else, or a different phase may crash. Treat the stack trace as the instruction:
disable the named phase, and write down why.

**Verify, on a mac:** `:composeApp:linkDebugTestIosArm64` and
`:composeApp:linkReleaseFrameworkIosArm64`.
**On Linux:** `:composeApp:compileDebugKotlinAndroid :composeApp:jvmTest` — the
renamed tests must still run, and the count must not move.

> `build(ios): unbreak the ios link tasks`

---

## Phase 4 — put every ktor client on Darwin

CIO has no TLS on Kotlin/Native. Pick the engine per platform instead of letting
the classpath pick it.

**Catalogue entry**, beside `ktor-client-cio` in
[gradle/libs.versions.toml:82](../gradle/libs.versions.toml):

```toml
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
```

**Dependency**, inside the existing `isMacOsX`-gated `iosMain.dependencies` block at
[composeApp/build.gradle.kts:154](../composeApp/build.gradle.kts). It goes inside
the gate, which is why Linux resolution is untouched by this phase.

**Three new files**, an `expect` and two `actual`s, in
`press.mantra.compose.network`:

- `commonMain/.../PlatformHttpClientEngine.kt` — `internal expect fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*>`
- `androidMain/.../PlatformHttpClientEngine.android.kt` — `= CIO`
- `jvmMain/.../PlatformHttpClientEngine.jvm.kt` — `= CIO`
- `iosMain/.../PlatformHttpClientEngine.ios.kt` — `= Darwin`

**Two call sites.** `defaultSocketsHttpClient` becomes
`HttpClient(platformHttpClientEngineFactory()) { … }`, keeping its `WebSockets`
block as-is. And `MantraApp` gains the singleton Coil loader, because Coil's
default ktor fetcher builds a bare `HttpClient()` and would fail on `https://`
images exactly as the sockets did:

```kotlin
private val imageHttpClient by lazy { HttpClient(platformHttpClientEngineFactory()) }

@Composable
fun MantraApp(…) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient = { imageHttpClient })) }
            .build()
    }

    MantraTheme { … }
}
```

`setSingletonImageLoaderFactory` goes *above* `MantraTheme`, at the top of the
composable's body, so it is installed before the first `AsyncImage` composes.
`MantraApp` is the only place both call sites are reachable from, and Mantra has no
`ImageLoader` configuration today to reconcile with.

**Verify, on Linux:** `:composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinJvm :composeApp:jvmTest` — the android and jvm `actual`s and both call sites compile here, which is most of the phase.
**On a mac:** `:composeApp:compileKotlinIosArm64`, then the real test — a device or
simulator that reaches a relay over `wss://` and renders an avatar over `https://`.

> `fix(ios): build ktor clients on Darwin so wss/https work on a device`

---

## Phase 5 — signing, icon, version

Three things Curated's last two commits did that **cannot be ported**, only
decided. Each needs an answer from you before it can be written.

**`TEAM_ID`.** [iosApp/Configuration/Config.xcconfig](../iosApp/Configuration/Config.xcconfig)
has `TEAM_ID=` empty, and the bundle identifier is built from it
(`press.mantra.ios$(TEAM_ID)`). Curated committed its development team id in the
clear, which is normal for an xcconfig — a team id is not a secret — but it is
*their* team id and signs builds to *their* account. Mantra needs its own, or an
`xcconfig` that stays out of git.

**The app icon.** Curated replaced its single `app-icon-1024.png` with a 1024
light variant and a 1024 dark one, adding the `luminosity: dark` appearance to
`Contents.json` and leaving the `tinted` slot filenameless so iOS derives it.
Mantra's `AppIcon.appiconset` still holds one 1024 file and one entry. The
*structure* ports; the artwork does not. Two 1024×1024 PNGs are needed.

**`MARKETING_VERSION`.** Mantra's xcconfig says `1.0` while
`android { versionName }` says `0.1.2` — the two have simply never been reconciled
because nothing shipped on iOS. Curated set both to the `0.1.x` line. Pick one
number for both; the mismatch is the bug, not either value.

Curated's `a673c85e` moved `versionName` from `0.1.2` to `0.1.1`, which is a
release-bookkeeping step on their tree and means nothing on this one. Do not port
it.

> `feat(ios): the app icon, the signing team, and one version across both stores`

---

## Phase 6 — the part only a mac can find

Phases 1 to 4 fix everything Curated hit. They do not fix what Curated never had,
and Mantra has pulled four lines of work the fork does not carry: projects, chapter
uploads, multi-parent subgroups, and the markdown producer. None of it has ever
been compiled for `iosArm64`.

So the honest shape of this phase is a loop, not a list:

```bash
./gradlew :composeApp:compileKotlinIosArm64
./gradlew :composeApp:linkDebugTestIosArm64
./gradlew :composeApp:iosSimulatorArm64Test
./gradlew :composeApp:linkReleaseFrameworkIosArm64
```

then Xcode, whose build phase already runs
`:composeApp:embedAndSignAppleFrameworkForXcode` against the `ComposeApp`
framework, deployment target 18.2.

Known places to look first, each a guess with a reason:

- **`FilePicker.ios.kt`** is the newest `iosMain` actual and the one behind chapter
  uploads. It exists, so it compiles in principle; whether `UIDocumentPicker`
  actually returns bytes on a device is untested anywhere.
- **Anything reached only from the new screens** — the `StaticInitializers` crash in
  Phase 3 is call-graph-shaped, and these four lines are what widened the graph.
- **`kotlinx.datetime` and `okio` edges** in the markdown and chapter paths, which
  are the usual source of a jvm-only overload that compiles everywhere but native.

Each fix found here is its own commit, in the voice of what broke rather than
"ios fixes".

---

## Phase 7 — write down what it cost

This document, revised from a plan into a record: which of the five defects
behaved as predicted, what Phase 6 actually found, and which compiler phase is
disabled with which stack trace behind it. Then revise its row in
[docs/README.md](./README.md), which this document already carries, so it describes
what happened rather than what was planned.

The `-Xdisable-phases` flag in particular needs a home in prose. It is a load-
bearing workaround for an upstream compiler bug, it is invisible from the source,
and the next person to bump Kotlin should be able to find out in one grep whether
they can take it out again.

---

## What to do if there is no mac

Phases 1 to 4 are worth landing anyway, and all four are verifiable on Linux for
the half that Linux compiles:

| phase | Linux-verifiable | needs a mac |
|---|---|---|
| 1 | the fix is `if (!currentOs.isLinux)`, so "unchanged" is the whole check | that it configures at all |
| 2 | `@ConstructedBy` and all seven `@Throws` | the unused-import removal |
| 3 | the renamed tests still run; the build script still compiles | both link tasks |
| 4 | the `expect`, the android and jvm `actual`s, both call sites | the Darwin `actual`, and TLS actually working |
| 5 | nothing | nothing — it needs decisions, not a host |

Phase 2's `@ConstructedBy` is the one with real Linux-side risk, since it changes
what Room's KSP generates for every target. Land it on its own and read
`:composeApp:jvmTest` carefully.
