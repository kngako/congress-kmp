# Bringing up the JVM target

What it would actually take to build Mantra for desktop, phased, with the
blocking work separated from the mechanical work.

The headline is not what you would expect. The four-deep native chain — secp256k1
→ bitcoin-kmp → lightning-kmp → lightning-kmp-app — is **already building for
JVM**, and has been all along. The thing standing in the way is an empty source
set in our own phoenix fork.

One scoping note before anything else: **none of this is needed to test the
DAOs.** Host-speed Room tests run under `androidUnitTest` today, given one extra
dependency. The JVM target is a product decision — a desktop Mantra — not a
testing prerequisite. See [Room DAO tests](#appendix-room-dao-tests-do-not-need-this)
at the end.

## What is already done for you

**The native chain is already JVM.** This is the expensive part, and it is
finished. The comment at [composeApp/build.gradle.kts:50](../composeApp/build.gradle.kts)
records why: `lightning-kmp-core` publishes no android variant, so our android
target resolves it to the **jvm** one, which in turn pulls
`secp256k1-kmp-jni-jvm` — desktop `.so`/`.dylib`/`.dll` files. That is why the
build has to name `secp256k1-kmp-jni-android` by hand.

Read that the other way round and it is good news: every JVM artifact in the
chain is already compiled from source, by the composite build, on every android
build we do. Turning on a JVM target adds no cinterop, no C compilation, and no
new native constraints.

**The third-party dependencies all have JVM variants.** Verified against the
repositories the build actually resolves from:

| dependency | JVM artifact | status |
|---|---|---|
| quartz 1.14.0 | `com.vitorpamplona.quartz:quartz-jvm` | on Maven Central |
| room3 3.0.1 | `androidx.room3:room3-runtime-jvm` | on Google Maven |
| sqlite 2.7.0 | `androidx.sqlite:sqlite-bundled-jvm` | on Google Maven |
| sqldelight 2.3.2 | `app.cash.sqldelight:jdbc-driver`, `:sqlite-driver` | on Maven Central |

**`commonMain` is clean.** No `android.*` and no `java.*` imports anywhere in it.
The three NFC files in `androidMain` are not referenced from common code either,
so there is nothing to stub out and no android-only API to route around. The
shared tree will compile for JVM as-is.

## What actually blocks it

`lightning-kmp-app/library` declares 25 `expect` symbols in `commonMain` and
implements them across 35 files in `androidMain`. Its `jvmMain` contains exactly
one file:

```kotlin
// lightning-kmp-app/library/src/jvmMain/kotlin/fibiprops.jvm.kt
package io.github.kotlin.fibonacci

actual val firstElement: Int = 2
actual val secondElement: Int = 3
```

That is the Kotlin multiplatform library template's Fibonacci boilerplate, left
over from whenever the module was scaffolded. It implements two of the 25
expects, and both of them are the template's own.

So **23 JVM actuals are missing**, one level down from us, and
`lightning-kmp-app/library/build.gradle.kts:18` has `jvm()` commented out because
of it. Mantra cannot declare `jvm()` — commented out in turn at
[composeApp/build.gradle.kts:46](../composeApp/build.gradle.kts) — until phoenix
does.

Everything below is ordered by that dependency.

---

## Phase 0 — build configuration

**~half a day. No blockers.**

Nothing here needs a decision; it is the groundwork the later phases assume.

1. **Delete the stale `jvmMain` tree.** Six files under
   `composeApp/src/jvmMain/kotlin/ac/cord/auxiliary/` survive from the old Aux
   project. They are an orphan source set that nothing currently compiles, which
   is why they have gone unnoticed — they use the wrong package, import
   `androidx.room` (Room 2), and reference a long-gone `AuxDatabase`. The moment
   `jvm()` is declared they become compile errors.

   Keep them open in a scratch buffer while doing Phase 4: five of them are a
   usable skeleton for the actuals we still need.

2. **Add the missing catalog entries** to `gradle/libs.versions.toml`:

   ```toml
   androidx-room3-runtime-jvm  = { module = "androidx.room3:room3-runtime-jvm", version.ref = "room3" }
   androidx-sqlite-bundled-jvm = { module = "androidx.sqlite:sqlite-bundled-jvm", version.ref = "sqlite" }
   sqldelight-jdbc-driver      = { module = "app.cash.sqldelight:jdbc-driver",   version.ref = "sqldelight" }
   sqldelight-sqlite-driver    = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
   ```

3. **Wire KSP for the JVM target.** Uncomment
   [composeApp/build.gradle.kts:194](../composeApp/build.gradle.kts). Without it
   there is no generated `MantraDatabase_Impl` or DAO implementation for the
   host, and `MantraDatabaseConstructor` has no JVM actual — Room's KSP generates
   that one, so it costs nothing once the processor runs.

4. **Leave `jvm()` commented out for now**, in both builds. It goes on at the
   start of Phase 4, once there is something for it to resolve against. Turning
   it on earlier just means living with a broken build through Phases 1–3.

**Verification:** `./gradlew :composeApp:compileDebugKotlinAndroid` still passes.
This phase changes nothing observable; the point is that it changes nothing
observable.

---

## Phase 1 — phoenix: the mechanical actuals

**~1–2 days. Blocked by nothing. Do this first.**

Fourteen of the 23. None of them requires a decision — each is either a direct
copy of the android implementation or a handful of lines of JVM file handling.

**`DbHooks.jvm.kt` — six functions, and this one is free.** The android
implementation is 15 lines and every function is an empty body; the hooks only do
work on Apple platforms, where they drive CloudKit sync. Copy the file, change
the suffix.

```kotlin
actual fun didSaveWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didDeleteWalletPayment(id: UUID, database: PaymentsDatabase) {}
actual fun didUpdateWalletPaymentMetadata(id: UUID, database: PaymentsDatabase) {}
actual fun didSaveContact(contactId: UUID, database: PaymentsDatabase) {}
actual fun didDeleteContact(contactId: UUID, database: PaymentsDatabase) {}
actual fun makeCloudKitDb(appDb: SqliteAppDb, paymentsDb: SqlitePaymentsDb): CloudKitInterface? = null
```

**`PlatformContext.jvm.kt` — the class plus four directory paths.** On android
these come off a `Context`; on desktop there is no context object, so
`PlatformContext` becomes either an empty class or one holding an explicit root
directory. Prefer the latter — it makes tests and multi-profile desktop installs
possible later, and it costs nothing now.

The four paths (`getApplicationFilesDirectoryPath`,
`getDatabaseFilesDirectoryPath`, `getApplicationCacheDirectoryPath`,
`getTemporaryDirectoryPath`) should resolve to a per-OS application data
directory, not `java.io.tmpdir`. The old Aux code used tmpdir and left a `TODO`
about it; do not inherit that.

**The remaining singles:** `platformElectrumRegtestConf`, `AppVersion`,
`phoenixLogWriters` (a console writer is fine), `computePreferencePath`, and the
two Fibonacci template properties — which should be deleted along with the
template file rather than reimplemented, assuming nothing references them.

**Verification:** still nothing compiles for JVM at this point, because Phases 2
and 3 are outstanding. Work against the expect list, not the compiler.

---

## Phase 2 — phoenix: drivers and connectivity

**~2–3 days. Blocked by nothing, but do it after Phase 1.**

Three of the 23 are database drivers and one is the network monitor. These are
real implementations, but they are bounded — the shape is known and the failure
modes are ordinary.

**`DbFactory.jvm.kt` — `createChannelsDbDriver`, `createPaymentsDbDriver`,
`createAppDbDriver`.** Use SQLDelight's `sqlite-driver` (the JDBC driver
specialised for SQLite). The one real difference from android: the android driver
creates and migrates the schema for you via `AndroidSqliteDriver`'s callback, and
the JDBC driver does not. You call `Schema.create(driver)` and the migration path
explicitly, and you have to track the applied version yourself. Budget for that,
not for the driver construction.

`createPaymentsDbDriver` also takes an `onError: (String) -> Unit` — make sure
corruption and migration failures actually reach it rather than throwing past it,
because on android that callback is what surfaces the problem to the user.

**`NetworkMonitor.jvm.kt`.** The android implementation is 90 lines built on
`ConnectivityManager` and its `NetworkCallback` — genuine push notification of
connectivity changes. The JVM has no equivalent. The options are a polling
reachability check, or treating the connection as always-available and letting
the lightning stack's own reconnect logic handle reality.

Start with polling on a slow interval. It is worse than the android behaviour and
that is acceptable — the alternative is pretending the network never changes,
which produces confusing UI on a laptop that gets closed and reopened.

**Verification:** none of this is exercisable until Phase 4. Write the SQLDelight
schema-creation path against a scratch `main()` if you want feedback sooner.

---

## Phase 3 — phoenix: key storage

**A decision, not a port. Unbounded until the decision is made.**

Four of the 23: `keyStoreEncryption`, `keyStoreDecryption`, and the two inline
wrappers `gracefulSingleSeedDecryption` and `gracefulMultiSeedDecryption` that
translate keystore exceptions into a `DecryptSeedResult`.

The android implementation delegates to `KeystoreHelper.kt` — 116 lines against
`AndroidKeyStore`, with `KeyGenParameterSpec`, and `setIsStrongBoxBacked(true)`
attempted first and fallen back from when the device has no secure element. The
key material never leaves hardware.

**Desktop JVM has no equivalent.** There is no portable, hardware-backed keystore
on the JVM. The realistic options:

| approach | protects against | cost |
|---|---|---|
| passphrase-derived KEK (Argon2id → AES-GCM) | disk theft, if the passphrase is strong | low; but prompts the user on every launch |
| OS keychain via JNA (Keychain / DPAPI / libsecret) | other users on the machine, at rest | three separate platform integrations, three failure modes |
| JCEKS/PKCS12 file with a fixed key | nothing meaningful | low, and misleading |

This is wallet seed material. The third option is not a stopgap, it is a
liability, and it interacts directly with the plaintext-key finding already open
against this codebase — do not let a desktop build quietly become the weakest
place the seed lives.

**Recommendation for sequencing:** implement the passphrase-derived KEK, mark the
desktop build clearly as unsuitable for real funds, and treat OS-keychain
integration as its own piece of work with its own review. That unblocks Phases 4
and 5 without pretending the security question is answered.

**This phase is the only one in the plan with no honest day estimate**, because
the estimate is a function of which row of that table gets chosen and how much
review it attracts.

---

## Phase 4 — mantra's own actuals

**~1–2 days. Blocked by Phases 1–3.**

Now turn on `jvm()` — [composeApp/build.gradle.kts:46](../composeApp/build.gradle.kts)
and `lightning-kmp-app/library/build.gradle.kts:18` — and let the compiler drive.

Mantra declares 16 expects across 8 files. They split cleanly:

**Six platform basics.** `getPlatform` ([Platform.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/Platform.kt)),
`PlatformContext`, `AppVersion`, `themeColorScheme`
([Theme.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/ui/theme/Theme.kt)),
and `PlatformDatabaseBuilder`'s two functions. The deleted Aux files from Phase 0
are a working skeleton for five of these — repackage to `press.mantra.compose`,
update Room 2 → Room 3 (`androidx.room` → `androidx.room3`), and point at
`MantraDatabase` instead of `AuxDatabase`.

`MantraDatabaseConstructor` needs no hand-written actual; Room's KSP generates it
once Phase 0 wired `kspJvm`.

For `PlatformDatabaseBuilder.getDatabaseBuilder`, use the real application data
directory from Phase 1 — not `java.io.tmpdir`, which is what the old Aux
implementation did and which silently loses the database on reboot on most
systems.

**Nine lightning wrappers.** Four in
[Phoenix.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/extensions/Phoenix.kt)
(`platformStartupLogic`, `schedulePlatformLogic`, `getShowIntroFlow`,
`getGlobalPrefs`) and five declared in
[SovereignWalletViewModel.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/SovereignWalletViewModel.kt)
(`updateBusinessActiveInUI`, `loadAndDecryptSeed`, `getAvailableWalletsMeta`, and
the two `saveAvailableWalletMeta` overloads, plus `platformWriteSeed`) whose
android actuals live in `NavigationViewModel.android.kt`.

These are thin — they mostly forward into the phoenix library. They are thin
*because* Phases 1–3 did the work, which is why they are last.

`schedulePlatformLogic` is the one to look at properly: on android it schedules
background work through WorkManager. On desktop there is no equivalent and no
process that outlives the window. Decide explicitly whether it becomes a no-op or
an in-process coroutine, and write down which.

**Verification:** `./gradlew :composeApp:compileKotlinJvm`. This is the first
point in the plan where the JVM target has to actually resolve, so expect the
dependency-substitution surprises to land here rather than earlier.

---

## Phase 5 — desktop entry point and shakeout

**~1–2 days. Blocked by Phase 4.**

[composeApp/build.gradle.kts:221](../composeApp/build.gradle.kts) already names
`press.mantra.desktop.MainKt` as the desktop main class. **That file does not
exist.** Write it: a `application { Window { ... } }` entry point constructing
`PlatformContext` and handing it to the same root composable android uses.

The UI itself is Compose Multiplatform and should largely come up as-is. What to
expect anyway:

- **Window sizing.** The layouts have only ever been laid out at phone widths.
  Nothing will crash; plenty will look wrong.
- **Back handling.** Android's system back has no desktop counterpart.
- **NFC.** The three `androidMain` NFC files are correctly android-only and are
  not referenced from `commonMain` — but any UI that offers an NFC affordance
  needs to not offer it here.
- **`Dispatchers.IO`.** Used in `getRoomDatabase` and available on JVM, so no
  change; noted because it is not available on all KMP targets and is easy to
  trip over later.

**Verification:** `./gradlew :composeApp:run`.

---

## Estimate

| phase | work | days | blocked by |
|---|---|---|---|
| 0 | build configuration | 0.5 | — |
| 1 | phoenix: mechanical actuals (14) | 1–2 | — |
| 2 | phoenix: drivers + network (4) | 2–3 | — |
| 3 | phoenix: key storage (4) | **decision** | — |
| 4 | mantra actuals (16) | 1–2 | 1, 2, 3 |
| 5 | desktop entry point + shakeout | 1–2 | 4 |

**Roughly one focused week to a launchable desktop build**, assuming Phase 3
takes the passphrase-derived KEK and the build is marked dev-only. Real desktop
key storage is separate work that should not be folded into this estimate, and
should land before anyone holds funds on a desktop Mantra.

Phases 1, 2 and 3 are independent of each other and can go in parallel if more
than one person is on it. Phase 4 cannot start until all three are done, because
it is where the compiler finally checks the whole thing.

## Out of scope

- **`linuxX64()`** — commented out in the phoenix library at line 53. A native
  Linux target is a different problem from a JVM one and buys nothing here.
- **iOS on a Linux host** — still impossible, for the reasons already documented
  in both build files. The JVM target does not change that.
- **Publishing desktop distributables** — the `compose.desktop` block already
  declares Dmg/Msi/Deb formats, but signing, notarisation and update channels are
  untouched by this plan.

## Appendix: Room DAO tests do not need this

Worth stating plainly, because the two questions arrived together and the answer
to one is not the answer to the other.

Room's own [testing guidance](https://developer.android.com/training/data-storage/room/testing-db)
recommends host-machine tests over instrumented ones. We can have those today,
without a JVM target, because `room3-runtime-android:3.0.1` exposes the
no-`Context` builder overload:

```
inMemoryDatabaseBuilder(kotlin.jvm.functions.Function0<? extends T>)
```

and [MantraDatabaseConstructor.kt](../composeApp/src/commonMain/kotlin/press/mantra/compose/database/MantraDatabaseConstructor.kt)
already supplies what it needs. So `Room.inMemoryDatabaseBuilder<MantraDatabase>()`
compiles in `commonTest` and runs under `testDebugUnitTest`.

The one trap is native, and it is the same shape as the secp256k1 problem
documented in the build file — in the opposite direction:

| artifact | ships |
|---|---|
| `sqlite-bundled-android` | `jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsqliteJni.so` |
| `sqlite-bundled-jvm` | `natives/{linux_x64,linux_arm64,osx_*,windows_x64}/` |

A local unit test resolves the **android** variant, whose `.so` files the host JVM
cannot load, so `BundledSQLiteDriver()` fails at construction. Naming
`sqlite-bundled-jvm` on the `androidUnitTest` classpath fixes it.

Robolectric does not help and is not needed — it cannot load android `.so` on the
host either, and Room's guidance advises against it regardless.
