# Chapter uploads

Picking files off a device and having a group sign each one into an artifact as a
`MantraChapter` with its chunks — the way
[mantra.press](https://code.sigidli.com/mantra/mantra.press) does it over one
HTTP POST, except that here nobody is authoring anything on their own.

**Not built.** This is a plan. The constraint that shapes every phase is stated
first, because the web app's shape — N files in, N chapters out, one
transaction, one redirect — is the one shape this app cannot have, and every
phase below is a consequence of that rather than a feature.

---

## What mantra.press does

One route, `POST /v/:id/chapters/upload`, in
`server/router/versions/index.js`. A multipart form with `multiple` on the file
input (`server/views/chapters-upload.pug`), and for each file:

```js
const fileText = file.data.toString()
const chunks = md.parse(fileText).filter(t => t.content).map(t => t.content)
db.Chapter.create({
    creatorId: userId,
    name: file.name,
    originalText: fileText,
    wordCount: chunks.reduce(wordCounter, 0),
    characterCount: chunks.reduce(characterCounter, 0),
    index: chapterIndex,
    artifactVersionId: artifactVersion.id,
    chunks: chunks.map((chunk, index) => ({ ... })),
}, { include: [{ association: db.Chapter.Chunks }] })
```

Six decisions in there worth naming, because this plan either keeps or
deliberately drops each one:

| mantra.press | here |
|---|---|
| the chapter's name is the **filename** | kept as the default, editable before proposing (§ Phase 4) |
| the index is `chapters.length + 1`, re-read from the version | allocated once for the whole upload, counting proposals as well as rows (§ Phase 3) |
| chunks are **markdown-it block tokens**, with the block marker stripped | adopted, with three deliberate departures (§ Phase 7, built in § Phase 8) |
| the uploader is the author (`creatorId`) | the **group** is the author, once a quorum signs |
| all files succeed or the request 500s | one session per chapter, each failing on its own (§ The constraint) |
| `file.data.toString()`, whatever the bytes are | UTF-8 decoded strictly, non-text refused (§ Phase 1) |

The web app has two small bugs in that loop that are worth knowing about so they
are not faithfully reproduced. `request.files.chapters.length > 0` is `undefined`
for a single file, so one file always takes the `else` branch — harmless, but it
means the multi-file path has had less use than it looks. And
`Number(chapterIndex) + Number(index)` starts from a `chapterIndex` that is
already `length + 1`, so a second file lands at `length + 2` and the first
upload into an empty version starts at index 1 while the add-chapter form starts
at `length`. Indices here are 0-based (`ChapterEvent.build(index = …)`, written
by `AddChapterViewModel` as `getChaptersForArtifactVersion(...).size`), and this
plan keeps them 0-based.

## What is already here

Nearly all of it, for one chapter at a time. This plan adds no event kind, no
tag and no schema version to the chapter path.

- [`ChapterEvent`](../composeApp/src/commonMain/kotlin/press/mantra/compose/nostr/nip30303/ChapterEvent.kt)
  (30302) carries the whole markdown in an `original_text` tag;
  [`ChunkEvent`](../composeApp/src/commonMain/kotlin/press/mantra/compose/nostr/nip30303/ChunkEvent.kt)
  (30303) carries one paragraph, and `ChunkEvent.splitOf(chapter)` cuts the
  chunks from a chapter *after* it is authored, which is the only place the
  chapter id exists to be named.
- [`AddChapterViewModel.addChapter`](../composeApp/src/commonMain/kotlin/press/mantra/compose/ui/view/model/AddChapterViewModel.kt)
  proposes the chapter and its chunks as one batch via
  `proposeSigningBatch(lead = …, dependents = ChunkEvent::splitOf)`, then opens
  a second session scaffolding the chapter into every translation of the
  version.
- `ChatMessage.applySignedEvent` has both branches (`ChapterEvent.KIND`,
  `ChunkEvent.KIND`), writes `groupSignedEventId` provenance, and
  `GroupSignedEvent` keeps the event as the group signed it.
- [`ChapterBatchProposalJvmTest`](../composeApp/src/jvmTest/kotlin/press/mantra/compose/managers/ChapterBatchProposalJvmTest.kt)
  and `SignedChapterTest` already cover the single-chapter batch end to end.

So the work is **not** "make a chapter group-signed". It is: get bytes off three
platforms, turn a pile of files into a reviewable list of proposals, and deal
with the four things a single hand-typed chapter gets away with and a twelve-file
upload does not.

---

## The constraint

A signing session is **one quorum, all-or-nothing, at most 64 items, and about
64 KiB on the wire**.

Three numbers, from three places, and the plan is shaped by all three:

- `FrostSigningManager.MAX_BATCH_SIZE = 64`, checked in `proposeSigningBatch`
  *and* independently in `acceptProposal` — see
  [frost-batch-signing.md](./frost-batch-signing.md).
- *All-or-nothing*: "a batch is only as available as its worst item. If any
  event cannot be aggregated the session fails and none of them are applied."
- *Bytes*: the proposal carries `k` whole events, which is the term that binds.
  `ChronicleEvent.MAX_PAGE_BYTES = 64 * 1024` is this codebase's measured proxy
  for what a kind:445 clears "with room for the MLS framing and the NIP-44
  expansion that sit outside it".

### The shape that follows: one session per chapter

The web app's single transaction has no analogue. Twelve chapters in one batch
is wrong three times over:

1. **It does not fit.** A chapter takes one item and its paragraphs take the
   rest (`MAX_CHUNKS_PER_CHAPTER = 63`). Two chapters of thirty paragraphs each
   already overflow.
2. **It should not fit.** A batch is only as available as its worst item, so one
   chapter that fails to reach quorum would take the other eleven with it. A
   book is precisely a set of things that do *not* need to arrive together.
3. **It is the wrong prompt.** Every member would be asked to read twelve
   chapters' full text in one approval gate, and the gate's own rule is that it
   "must show every event, not a count".

So: **one upload is N sessions, one per chapter.** That is the honest cost and
it has to be in front of whoever is picking files, not discovered afterwards —
N proposals, each needing a quorum, arriving in the room at once.

What it buys: a chapter that fails is re-proposable on its own, chapters land
and become readable as each quorum arrives rather than all at the end, and the
approval gate stays one chapter long.

---

## Four things the single-chapter path gets away with

Each is a real defect today, each is invisible at N = 1, and each is a phase.

**The index is read from rows, and no chapter is a row until its quorum lands.**
`addChapter` does `getChaptersForArtifactVersion(artifactVersion.id).size` at
propose time. Propose twelve chapters back to back and all twelve read the same
size, so all twelve are signed with the same index. The same collision already
exists between two members adding a chapter at the same moment; an upload just
guarantees it. **Phase 3.**

**There is no byte check at all.** The paragraph-count cap is enforced and shown
as you type; the size of the proposal is not checked anywhere. A proposal
carries the chapter (whole markdown) *plus* a chunk per paragraph (the same
words again), so it is roughly 2× the text before framing — which puts the
ceiling near 28–30 KiB of markdown, well inside the range of a real book
chapter. Today that is an `IllegalArgumentException`-free failure: the proposal
goes out, the group event is over size, and the session stalls. **Phase 2.**

**There is no file picker in the app.** Nothing in `composeApp/src` references
`rememberLauncherForActivityResult`, `OpenDocument`, FileKit or any equivalent;
the only platform `expect` declarations are for the database builder, context,
theme and motion. Three targets need it: `androidTarget`, `jvm`, and the two ios
targets gated behind macOS in `composeApp/build.gradle.kts`. **Phase 1.**

**Translation scaffolding fans out per chapter.** `addChapter` opens a second
session per chapter for the translation chapters. Twelve chapters in a group
with three dialects is twelve chapter sessions plus twelve scaffolding sessions
= twenty-four proposals, where the scaffolding is 36 tiny events that fit in one
batch. `TranslationScaffold.chaptersOf` already takes a *list* of chapters.
**Phase 5.**

---

## Phase 1 — a file in, as text

**Two days, most of it the three actuals. No protocol change, no new event.**

Ship the picker on its own, before anything knows about multiple chapters, and
prove it on all three platforms. It lands as a **"load from file" button on the
existing `AddChapterScreen`** that fills `originalTextFieldState` and the name
field: one file, straight into the form that already works, so the picker is
exercised and reviewable without the batching argument attached to it.

### The API

```kotlin
// commonMain
data class PickedFile(val name: String, val bytes: ByteArray)

@Composable
expect fun rememberFilePicker(onPicked: (List<PickedFile>) -> Unit): FilePicker

interface FilePicker { fun launch(multiple: Boolean) }
```

Returned as bytes rather than a path or a stream. A path is meaningless on iOS
once the picker's security scope closes, and a stream would make the decode
decision below somebody else's problem on a background dispatcher. Chapters are
tens of kilobytes; this is not where to be clever.

### The three actuals, or one dependency

**Built with the three actuals, not FileKit.** The plan recommended the
dependency and the implementation went the other way, for three reasons that
only became clear with the tree open: this is the app's only dialog, the three
calls are a dozen lines each, and the project already keeps four platform seams
of exactly this shape (`PlatformContext`, `PlatformDatabaseBuilder`,
`platformReducedMotion`, `platformThemeContrast`) — so the seam was free and the
library's Compose-version coupling was not. The ios target cannot be compiled on
a linux host either way, so a dependency would not have bought verification
where verification is actually missing.

**The three actuals**, which were a day's work and no mystery:

| target | call |
|---|---|
| `androidMain` | `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())` |
| `jvmMain` | `java.awt.FileDialog` with `isMultipleMode = true` |
| `iosMain` | `UIDocumentPickerViewController(forOpeningContentTypes:)`, `allowsMultipleSelection = true` |

Android needs no manifest permission for `OpenDocument` — it is a SAF handoff.
iOS needs the document types declared; `.md` has no registered UTI, so
`UTTypePlainText` plus `UTTypeData` and sniff afterwards.

Three things the build added that the plan had not thought of:

- **Both android contracts are registered**, `OpenDocument` and
  `OpenMultipleDocuments`, and `launch(multiple)` picks. Registering only the
  multiple one would offer a six-file picker to a form that keeps one file.
- **Every actual stops reading one byte past the cap**, not at it, so an
  over-size file arrives over-size rather than silently truncated — a truncated
  chapter decodes cleanly and would be proposed with its ending missing.
- **The dialog title is a parameter**, read from the catalogue by the caller,
  because `stringResource` is composable and a `FileDialog` is not. Only desktop
  shows it; android's and iOS's pickers title themselves. Taken on all three so
  that the one platform showing text to a reader gets it from the catalogue like
  every other string in the app.

**The ios actual is unverified.** The two ios targets are declared only on macos,
so `FilePicker.ios.kt` is not compiled on a linux host and has never run. It is
written to the shape of the eight other `.ios.kt` actuals beside it and says so
in its own KDoc. A stub returning no files would have been worse: it reads as a
working picker that a reader's chapters vanish into.

### Decoding, which is where the web app is wrong

`file.data.toString()` on a `.docx` produces a chapter of zip header garbage,
and the group would be asked to sign it. Here:

```kotlin
// commonMain
object ChapterText {
    const val MAX_FILE_BYTES = 1 * 1024 * 1024

    sealed interface Result {
        data class Text(val markdown: String) : Result
        data object NotText : Result
        data object TooLarge : Result
    }

    fun decode(bytes: ByteArray): Result
}
```

Four rules, each with a reason:

- **Strict UTF-8.** `decodeToString(throwOnInvalidSequence = true)` and refuse on
  failure. A replacement character signed into an `original_text` tag is a
  permanent `U+FFFD` in the group's record.
- **Strip the BOM.** `U+FEFF` at the head of the first paragraph is invisible in
  the form and changes the chunk's text, so it changes the chunk's id.
- **Normalise CRLF to LF before hashing.** `Markdown.PARAGRAPH_BREAK` is
  `(?:\r?\n){2,}`, so splitting already tolerates CRLF — but the `original_text`
  tag would carry the `\r`s, and the same chapter uploaded from Windows and from
  Linux would be two different events. Normalise once, at the door.
- **Sniff, do not trust the extension.** A NUL byte in the first 8 KiB means
  `NotText`. Cheap, catches every office format and every image.

`MAX_FILE_BYTES` is a crash guard, not the real limit — the real one is Phase 2
and is twenty times tighter.

**Tests** (`commonTest`): a `ChapterTextTest` over valid UTF-8, invalid UTF-8,
BOM, CRLF-vs-LF producing identical text, a NUL-bearing payload, and the size
cap. All pure; no picker involved. 13 of them, plus 9 in a `ChapterNamingTest`
that the plan had folded into Phase 4 and which belongs here — the default name
is derived from the file, so it arrives with the file.

`ChapterNaming` turned out to deserve its own object rather than a line in the
upload screen. The web app has two answers and uses a different one per route —
`file.name` on the upload, `chunks[0].trim()` on the paste form — and both are
right some of the time. The rule built here is: the filename with its extension
and ordinal prefix removed, falling through to the text's first heading and then
its first line when the filename is only an ordinal. `01.md` is not a chapter
title; `03 - The Wreck.md` is.

---

## Phase 2 — the byte cap, in front of the user

**Half a day. A fix to the path that exists, and a prerequisite for Phase 4.**

A chapter whose markdown is over roughly 24 KiB cannot be proposed today and
nothing says so. Make the limit explicit and check it where the paragraph cap is
already checked.

**Built differently, in three ways, and the measurement is why.** Encoding real
proposals gave a per-event overhead of 357–388 bytes across chapters from 1 KiB
to 24 KiB and from 1 to 63 chunks — the plan's ~370 estimate, arrived at for the
wrong reason: a proposal carries *unsigned* events, so there is no 128-character
signature in it, and the figure is instead an id, a pubkey, an empty `sig`, the
JSON structure and, on a chunk, the 64-character chapter id it points at.

What that measurement changed:

**One. A flat `MAX_ORIGINAL_TEXT_BYTES` is wrong by half.** Measured, a
63-chunk chapter fits about 20 KiB of markdown and a 10-chunk chapter fits about
30, because the envelopes are what varies. A single constant safe for the first
refuses a third of the second's text for nothing; one sized for the second
stalls sessions. The chunk count is known as the text is typed, so the limit may
as well be — `ChapterProposal.proposalBytes(text, chunks)` and
`maxOriginalTextBytes(chunkCount)` instead of a constant.

**Two. The limit belongs to the manager, not to the chapter.**
`FrostSigningManager.MAX_PROPOSAL_BYTES = 64 * 1024`, beside `MAX_BATCH_SIZE`,
enforced in `proposeSigningBatch` against the real encoding and *independently*
in `acceptProposal` — before parsing, since that is the cheaper refusal. The
count cap already had exactly this two-sided treatment for exactly this reason:
the proposal is where a remote party decides how much work everybody else does,
and until now its *size* was unbounded. Both propose forms funnel into the
`lead`/`dependents` one, so a single check covers them.

**Three. `ChapterProposal` estimates what the manager measures.** The exact
answer needs the chapter built and the chunks cut, which is one line from
opening a session — too late to tell somebody with a cursor in a text field.
So: a cheap estimate for the form at 448 bytes per event, rounded up from the
measured 388, and a test asserting the estimate never falls under the real
encoding. Over-estimating costs an author a slightly shorter chapter;
under-estimating costs the group a stalled session, so the rounding goes that
way on purpose.

**The number is still provisional until Phase 8 lands.** Block splitting
produces more chunks than blank-line splitting, so the same prose carries more
envelopes; `ChapterProposalSizeTest` pins where the fit runs out (24 KiB over
forty paragraphs), so that phase will move a pinned figure rather than quietly
narrow the limit.

Where it goes:

- `AddChapterViewModel.addChapter` — beside the
  `paragraphs.size > MAX_CHUNKS_PER_CHAPTER` guard, same early `onFailure`.
- `AddChapterScreen` — the counter line already reads
  `"$wordCount words · $characterCount characters · $paragraphCount chunks of N"`;
  it gains the proposal's size against the cap and turns `error`-coloured on the
  same rule the chunk count does. The message compares like with like — what
  proposing would send, against what a group message carries — rather than a
  character count against a byte budget, which was the first draft of it and was
  comparing UTF-16 units to bytes.
- The two existing hardcoded English literals on that screen (the no-shared-key
  message and the too-many-chunks message) move into
  `composeApp/src/commonMain/composeResources/values/strings.xml` while the file
  is open. They are UI text in a composable and `CLAUDE.md`'s budget is zero
  title-case strings with literals reported; a third one added by this work
  would be the wrong direction.

**Tests:** a `ChapterProposalSizeTest` in `commonTest` — pure, since the
estimate and the encoding are both pure — over the grid the overhead was
measured on, asserting the estimate never falls under the real encoding and
stays within a tenth of it. Plus the round trip the form's offer rests on: the
longest text the estimate allows at the worst chunk count really does encode
under the cap. Plus the boundary pinned as a number, so a tag added to either
kind moves a figure in a test rather than producing a quorum that never
arrives. And the byte-vs-character one, in Amharic, because an ASCII-only test
suite would never have caught the UTF-16 slip above.

---

## Phase 3 — an index that counts proposals, not rows

**A day. A fix to the path that exists.**

The index is signed into the event and cannot be recounted on arrival — the
comment in `addChapter` says exactly why: "a device applying it cannot recount
from a version other members may have added to in a different order". So the
number has to be right at proposal time, and "rows in the table" is not it,
because a proposed chapter is not a row for as long as the quorum takes.

The information is already available. `FrostSigningRepository` exposes
`observeSessionsForChatRoom`, `getItems(sessionId)` and `unsignedEvents(items)`,
and the last of these exists precisely for this: "A caller wanting an id … needs
it while the first session is still running, and the id is settled at proposal
time."

**Built as `ChapterIndexing.nextIndex`**, an object beside `TranslationScaffold`
rather than a method on `MantraRepository`, because it needs both repositories —
the table for what is signed and the signing repository for what is proposed —
and a repository method that reaches into another repository is the wrong shape.
It takes both, which also makes it testable without a view model.

Implemented as: rows for the version, plus the unsigned `ChapterEvent`s of every
session in the room whose `artifactVersionId()` matches, and `maxOf(index) + 1`
rather than a count — a count is wrong the moment a gap exists, and after a
chapter fails to reach quorum a gap is exactly what there is.

Three details the build settled that the plan left open:

- **Every session counts, whatever stage it is at.** Filtering to open ones
  would trade a harmless gap (a failed session's index is spent, since a
  re-proposed chapter is a different event) for a possible collision with a
  stalled session that still completes.
- **Items are filtered by kind, which is not optional.** A chapter proposal is a
  batch, and a chunk carries an `index` tag too — its position in the chapter.
  Reading those as chapter indices would put the second chapter of a
  two-paragraph first chapter at 2. There is a test for exactly that.
- **Proposals are not scoped by version and rows are.** The row query takes an
  `artifactVersionId`; `observeSessionsForChatRoom` returns the whole room, so
  the `artifactVersionId` tag is what separates one version's proposals from
  another's. Getting it wrong would push every version's numbering up by the
  whole room's.

Failing soft on purpose: if the proposals cannot be read, the table's answer is
still better than nothing, and refusing to propose a chapter because its number
could not be checked would be the wrong trade.

Then `AddChapterViewModel` calls it instead of `.size`, and the upload allocates
`base + i` for its i-th file **once**, before proposing anything, rather than
re-asking between sessions. Re-asking would work and is worse: it makes the
upload's numbering depend on how fast the sessions are written.

**What this does not fix.** Two *devices* proposing at the same moment still
collide — neither sees the other's proposal until it arrives. That is a
distributed-allocation problem and the honest answer is that index collisions
are tolerable: the index orders reading, two chapters at index 4 sort
arbitrarily between themselves and nothing else breaks. Worth a sentence in the
code so the next reader does not take the local fix for a global one.

**Tests** (`ChapterIndexingJvmTest`, beside `ChapterBatchProposalJvmTest`):
propose three chapters back to back without completing any session, and assert
the three indices are 0, 1, 2 — which is the test that fails today, since
nothing is signed so the table answers 0 three times. Plus the gap, the two
versions, and the chunk-vs-chapter confusion above.

---

## Phase 4 — the upload screen

**Three days. The feature.**

`UploadChaptersRoute` / `UploadChaptersUIState` / `UploadChaptersViewModel` /
`UploadChaptersScreen`, registered in `MantraNavHost`, reached from
`ArtifactDetailScreen` next to the existing "add chapter" `TextButton`
(`ArtifactDetailScreen.kt:165`), with the same `enabled = versions.isNotEmpty()`
condition.

### It is a review step, not a submit button

This is the one place the plan departs visibly from the web app, and the reason
is that a signature is not a submission. mantra.press can afford to post twelve
files and redirect, because what it created is the uploader's and the uploader
can fix it. Here, twelve files become twelve requests for the group's own
signature, and anything wrong in them — a filename for a title, a file that
decoded to nothing, a chapter over the byte cap — is wrong in something a quorum
is about to put its name to.

So picking files does not propose anything. It produces a list, one row per
file:

| shown | from |
|---|---|
| name, **editable**, defaulting to the filename without its extension | `PickedFile.name` |
| the index it will take | `base + i` from Phase 3 |
| chunks, against `MAX_CHUNKS_PER_CHAPTER` | `Markdown.splitParagraphs(...).size` |
| words, characters, bytes against `MAX_ORIGINAL_TEXT_BYTES` | `Markdown`, Phase 2 |
| a per-row problem, when there is one | below |
| a remove action | — |

Four per-row problems, each blocking that row only — a bad file should not
cost the other eleven:

- not text, or not UTF-8 (`ChapterText.Result.NotText`)
- empty after trimming, which is a chapter with nothing to translate
- over the chunk cap, with the advice to split it
- over the byte cap
- **duplicate**: its normalised text matches an existing chapter of this version
  or another row in this list. Not blocking — a book can repeat a page — but
  warned, because the common cause is picking the same directory twice, and two
  chapters with the same text are two different event ids that no later
  de-duplication can merge.

A per-file name field rather than one derived from the text, because the
filename is right often enough to be the default and wrong often enough
(`chapter-01.md`, `ch1_final_v2.md`) that it must be fixable before a quorum
sees it. Where it is empty, fall back to the first markdown heading in the text,
then to the first paragraph trimmed to a line — which is what the web app's
*other* route does (`name: chunks[0].trim()`), and is better than either
extreme.

### Proposing

The FAB — `ExtendedFab`, as every primary action in the app is — reads
"propose N chapters", is disabled when any included row has a blocking problem
or `canSign` is false, and on tap:

```
for each row, in index order:
    propose(chapter + chunks)        // the existing batch, one session per chapter
    record the session id
then:
    propose the scaffolding for all N chapters at once   // Phase 5
then:
    navigate to the proposal list, not to a signing screen
```

**Sequentially, not concurrently.** Each `proposeSigningBatch` writes a session
and publishes group events; N at once is N interleaved MLS epochs, and
[mls-skipped-keys.md](./mls-skipped-keys.md) is about what a group event that
arrives a moment out of order costs. The upload is not in a hurry.

**Partial success is the expected outcome, not an error.** If the fourth
proposal fails, the first three are real proposals that the group can still
sign, and nothing can retract them. So the screen does not roll back and does
not navigate away on a failure: it marks the rows that were proposed, leaves the
rest listed with their reason, and offers "propose the remaining". Phase 3's
`nextChapterIndex` means a retry re-reads the base and does not re-use the
indices already proposed.

**It navigates to `ProposalListScreen`, not to `FrostSigningRoute`.** The single
chapter path goes to the signing screen because there is one session to watch,
and the reasoning in `addChapter` is sound for it ("landing on the list it is not
in would read as a failure"). At N there is no one session, and the list is the
screen that exists for "two proposals need a place that shows both". A snackbar
via `rememberNotifier` carries the count, read above the handler as `CLAUDE.md`
requires.

### Scope, and what it must say out loud

Before the picker opens, the screen says what an upload will cost the room: one
proposal per file, each needing a quorum. This is not a nicety. The app's
approval gate is deliberately per-session and deliberately shows every event,
and twelve of them arriving at once is a real imposition on every other member
that the person picking files is the only one in a position to weigh.

A soft cap belongs here too — refuse to propose more than, say, 20 files in one
go, and say why. Not a protocol limit; a limit on how much of the room's
attention one tap may spend.

### M3, per `CLAUDE.md`

`NavigateBackButton` in the app bar's leading slot (it is a pushed screen);
`readableContent()` on the content root; all four states, with `EmptyState`
carrying its own message for "no files picked yet" and `ErrorState` taking a
real `onRetry`; `ScreenStateTransition` wrapping the `when` as the composable's
whole body; spacing from `MaterialTheme.spacing`; every string in `strings.xml`
in sentence case with `%1$s`/`%1$d` arguments; every icon with a decided
`contentDescription`. Finish with `./gradlew :composeApp:m3Audit`.

---

## Phase 5 — scaffold once, not once per chapter

**Half a day. A fix to the path that exists, and an N-fold saving.**

`AddChapterViewModel.scaffoldIntoTranslations` is right about needing its own
session — a chapter's paragraph budget should not be spent on the group's
dialects — and wrong about needing one *per chapter*, which only ever happens
during an upload.

`TranslationScaffold.chaptersOf(translationArtifactVersionIds, chapters,
createdAt)` already takes a list and already builds the full cross product in
translation-then-reading order. So lift the scaffolding out of the per-chapter
loop into `ChapterScaffolding.propose`, which both view models now call:
collect each chapter's `SourceChapter(id, index)` from its session's unsigned
lead event, and after the last chapter is proposed, propose the whole cross
product in `chunked(MAX_BATCH_SIZE)` batches.

Twelve chapters × three dialects: from 12 scaffolding sessions to 1.

**And it fixes a latent bug that one chapter could never show.** The old code
chunked the *translations* — right by coincidence at one chapter, where the
cross product and the translation list are the same length, and wrong at any
more: 25 chapters in a three-dialect group is 75 rows, which the translation
axis would have put in a single batch of 75 and `proposeSigningBatch` would have
thrown. Chunking the product is the generalisation, and the test for it is the
only one here that would fail against the per-chapter shape.

Two smaller decisions the build had to make:

- **One timestamp for the batch, the latest of the chapters' own.**
  `chaptersOf` stamps every row with one `createdAt` so a scaffolding proposed
  as one act reads as one; with N chapters there are N candidates. The latest is
  the moment the act finished. Reading the clock instead would be a third time
  unrelated to either end of it.
- **A session whose lead event cannot be read is left out, not guessed at.** A
  join row naming the wrong chapter is worse than a missing one, because the
  catch-up can find a missing one.

Keep the existing failure policy verbatim, including its reasoning: scaffolding
failure is logged and swallowed, because the chapters are the thing that was
asked for, and a scaffolding row naming a chapter that never reached quorum
fails a foreign key, is logged by `applySignedEvent`, and never becomes a row.
The catch-up on the translation fills the gap afterwards. At N that argument gets
*stronger*, not weaker — one cross-product batch covering twelve chapters is
only as available as its worst item, and some of those chapters may genuinely
fail.

`AddChapterViewModel` keeps its one-chapter call into the same helper, so there
is one piece of code deciding what a chapter's scaffolding is. The upload calls
it once, after its loop, with only the chapters that actually went — so a
chapter that failed to propose contributes no scaffolding and the ones that
succeeded still get theirs.

**Tests:** `ChapterScaffoldingJvmTest`, six of them, over a real database and
real proposals. The arithmetic (12 × 3 = one session of 36 rows), the ids coming
from the chapters' own sessions, a chapter keeping its own index rather than the
list's, the cross-product chunking at 25 × 3 = 75, and the two empty cases.

---

## Phase 6 — N proposals, in the room

**A day. No new screens.**

The transcript and the proposal list were built for two concurrent sessions —
`ChatMessage.frostSigningSessionId` exists because matching a request against
"any later line of the fulfilling type" broke the moment a chapter and its
scaffolding were open together. Twelve is the same problem at a scale that will
find whatever that fix missed.

What to check rather than assume — and **all three already held**, so this
phase is its tests and this paragraph:

- **The list already groups.** `ProposalListScreen` renders a "Waiting for you"
  header over the sessions `FrostSigningManager.isAwaitingApproval` picks out and
  an "Everything else" header under them, over a query that is already
  `ORDER BY createdAt DESC`. Twelve proposals arrive sorted and the three that
  need the reader are at the top.
- **The summary already names the chapter.** `ProposedEvent.summarize`'s chapter
  branch is `name · N words · N chunks`, not "Chapter" — so twelve rows are
  twelve distinguishable rows.
- **`isAbout` already matches on the session.** `answeredRequests` and
  `settledRequests` compare `frostSigningSessionId` when both rows carry one and
  fall back to the clock only when either does not. Now tested at twelve, with
  the answers given in the opposite order to the asking so that neither the
  clock nor the list order could produce the right answer by accident, plus one
  failure settling only its own row.
- **Twelve approval gates, unbatched.** Unchanged by design: the gate shows every
  event rather than a count, because "a member who cannot scroll the whole list
  should not be able to approve it".

No new `ChatMessage` type, no new set to add to.

### One thing N does make worse, and it is Phase 8's to fix

`observeSessionsForChatRoom` carries every session's items, and a chapter item's
`unsignedEventJson` holds the whole markdown. Twenty open chapter proposals is
roughly 900 KB of JSON parsed and summarised **on every emission** — and
`summarize` then re-splits each chapter's text to count its chunks, which is the
derived count Phase 8 replaces with the session's own item count. Fixing it
there removes the re-split; the parse is inherent to the query and is the price
of the list showing what each proposal signs without one query per row.

---

## Phase 7 — the splitter, which is a decision about translation

**Half a day, and it is a measurement rather than code.**

mantra.press splits with markdown-it block tokens; this app splits on blank
lines.

```js
// mantra.press: one chunk per block-level token that carries content
md.parse(text).filter(t => t.content).map(t => t.content)
```

```kotlin
// here: one chunk per blank-line-separated block
text.split(Regex("(?:\\r?\\n){2,}")).map { it.trim() }.filter { it.isNotEmpty() }
```

They were assumed to agree on ordinary prose and to disagree only on converted
files. Run side by side over a corpus — the reference parser as
mantra.press configures it, against the Kotlin rule — they disagree on **almost
everything**, and on two separate axes:

| input | markdown-it | `splitParagraphs` |
|---|---|---|
| `# Chapter one` ⏎ `First paragraph.` (the converter's shape) | 3 chunks, heading apart | 2 chunks, heading glued to the prose |
| `# Chapter one` ⏎⏎ `First paragraph.` | `"Chapter one"` | `"# Chapter one"` |
| a tight three-item list | 4 chunks — the lead-in and each item | 2 — the lead-in and the whole list |
| `> line one` ⏎ `> line two` | `"line one\nline two"` | `"> line one\n> line two"` |
| a fenced code block | `"code();\n"`, fence markers gone | ``"```\ncode();\n```"`` |
| a 2 × 2 table | 4 chunks — one per cell | 1 — the table's source |
| `---` between two paragraphs | 2 chunks, the rule is not one | 3 — the rule becomes a chunk |
| `Title` ⏎ `=====` (setext) | `"Title"` | `"Title\n====="` |
| a nested list | 2 chunks, one per item | 1 |
| a paragraph with a lazy continuation line | 1 chunk | 1 chunk — agree |

One row in eleven agrees. The table is reproducible in a dozen lines of node
against `mantra.press/node_modules/markdown-it`, and Phase 8 turns that into
checked-in fixtures.

**Axis one is boundaries**: how many chunks, which is the obvious half.
**Axis two is the text itself**, which is the half that was missed: markdown-it's `content`
is the leaf block's inline source with the *block* marker removed — no `#`, no
`> `, no `- `, no fence, no setext underline — while inline markup (`*italic*`,
a link's brackets) is kept verbatim. So even the rows where the counts agree
produce different chunk text, and therefore different chunk ids.

A chunk is the unit a translation is of and the thing the group signs. Axis two
is the one that matters to a translator: handed `"# Chapter one"` they can break
the document by mistyping the `#`, and handed `"Chapter one"` they cannot.

**The decision: adopt markdown-it's block tokens, boundaries and stripped text
both, for one splitter shared by the upload and the paste form.** The
alternative — markdown-it semantics for uploaded files, blank lines for typed
ones — means the same text yields different chunks depending on how it arrived,
which is indefensible the moment two members do it differently.

### Three places to diverge from mantra.press on purpose

Faithful is not the goal; the right unit of translation is. Three of
markdown-it's answers are wrong here, and each should be decided now rather than
inherited.

**Code blocks should not be chunks.** `fence` and `code_block` carry content, so
mantra.press turns `code();` into a chunk — with a trailing `\n` in its text —
and asks a translator to translate it and the group to sign it as prose. Emit no
chunk for either; the code stays in the chapter's `original_text` and the
producer passes it through untouched.

**A table should be one chunk, not one per cell.** Four chunks for a 2 × 2 table
is markdown-it's honest reading — cells do contain prose — but each costs an
event with a 64-character id, a 64-character pubkey and a 128-character
signature to carry one word, and a table's alignment is structure that per-cell
translation cannot preserve anyway. One chunk of table source, and the
translator keeps the pipes.

**A link reference definition should not be a chunk.** mantra.press's parser is
`markdown-it().disable(['link', 'reference'])`. `link` is an *inline* rule, so it
changes neither the block tokens nor an inline token's content; `reference` is a
*block* rule, and disabling it stops `[ref]: https://example.com` being consumed
as a definition, so it falls through to `paragraph` and becomes a translatable
chunk of a URL. That is a side effect of reaching for the wrong rule name, not a
decision. Recognise the form and emit nothing.

### Why the change is safe to make at any time

No device ever re-derives chunks from a chapter's text. `ChunkEvent.splitOf` is
called at proposal time only — `AddChapterViewModel` and tests, nowhere on the
apply path — and the chunks travel as their own signed events;
`ChatMessage.applySignedEvent` writes the rows it is given, and the chronicle's
fallback rebuilds them from rows via `toXEvent()` rather than by splitting
anything. So a device on an older splitter receiving a newer-split chapter
applies the chunks it was handed, and there is no skew to negotiate, no
migration, and nothing to re-sign.

What does *not* change retroactively: chapters already signed keep the chunks
they were signed with. Re-splitting one later means re-proposing it as a new
chapter with new ids. So this lands before the first real upload, not after.

---

## Phase 8 — a block splitter that agrees with markdown-it

**Three days, and the fixtures are most of the value.**

The implementation of Phase 7's decision. One new function, five call sites
moved together, one thing the app has never had, and an oracle instead of an
opinion.

### The function

```kotlin
// press.mantra.compose.text.Markdown
/**
 * The blocks a chapter's markdown splits into, one chunk each.
 *
 * A chunk is a *leaf* block's inline text with its block marker removed: the
 * words a translator is given and the words the group signs. Containers --
 * lists, blockquotes, tables -- contribute nothing of their own; a thematic
 * break and a code block contribute nothing at all.
 *
 * This is markdown-it's block token stream, which is what mantra.press
 * translates against, with the three deliberate departures in
 * docs/chapter-uploads.md. Inline markup is kept verbatim, because emphasis and
 * links are the translator's to carry; block markers are not, because a `#`
 * mistyped into a translation breaks the document.
 *
 * Its output is hashed into event ids a quorum signs, so it is pinned by the
 * golden fixtures in MarkdownBlockFixtures rather than by this comment.
 */
fun splitBlocks(text: String): List<Block>

data class Block(
    val kind: Kind,          // Paragraph, Heading(level), ListItem, Quote, Table
    /** The block marker, kept so a document can be put back together. */
    val markup: String,
    /** The inline source, marker removed. This is the chunk's text. */
    val text: String,
)
```

A `Block` rather than a `String`, because Phase 7's stripping decision means the
`#` and the `- ` have to be kept *somewhere* for the producer below, and the
chapter's `original_text` is the wrong place to look them up from — matching
text back to its marker is the string-equality bug described two sections down.

The block starts to recognise, which is markdown-it's block ruler minus the
rules Phase 7 drops: ATX heading, setext heading, fenced code (skipped),
indented code (skipped), blockquote, bullet and ordered list items including
nesting, thematic break (skipped), table (one block), link reference definition
(skipped), paragraph with lazy continuation.

### Hand-rolled, with the reference implementation as the oracle

`org.jetbrains:markdown` is multiplatform and CommonMark-compliant and would do
the parsing. It is the wrong dependency *here*, for one reason that is specific
to this function: its output is hashed into ids that a quorum signs, so a
version bump that moves one boundary silently re-splits every chapter proposed
afterwards, and nothing in the app would notice. A hand-rolled scanner over the
block starts above is a few hundred lines whose behaviour is ours to pin.

That is only defensible with an oracle, so:

**`MarkdownBlockFixtures` — input/expected pairs generated by markdown-it
itself, checked in as test data.** A script under `docs/scripts/` runs the
reference parser over a corpus (the converter's output, a hand-typed chapter, the
pathological cases in Phase 7's table) and emits the expected blocks; the
`commonTest` suite asserts `splitBlocks` reproduces them, with the three
departures listed as explicit exceptions rather than silently absent. Fidelity
is then measured against the implementation it claims to agree with, which is
the same standard `MAX_PAGE_BYTES` sets for itself.

Being hand-rolled, it is a documented *subset* of CommonMark and should say so:
no HTML blocks (markdown-it's default is `html: false` and the `<div>` in the
corpus comes back as a paragraph, so this matches), no footnotes, no nested
blockquote-in-list beyond one level. Each omission is a fixture asserting what
happens instead, not a gap.

### The producer, which the app has never had

Stripping block markers from chunks is only safe if a translated chapter can be
put back together, and nothing in `composeApp/src` does that today — there is no
equivalent of mantra.press's `lib/markdown-producer/index.js`. It is part of this
phase, because Phase 7's decision is incomplete without it.

```kotlin
object MarkdownProducer {
    /** The chapter's markdown with each chunk's translation in place of its text. */
    fun produce(originalText: String, translations: Map<Int, String>): String
}
```

Walk `splitBlocks(originalText)`, substitute by **index**, and reassemble with
each `Block.markup`. Substituting by index rather than by text is the one place
this is strictly better than the thing it mirrors: mantra.press matches with
`translationChunks.find(tc => tc.text == token.content)`, so a chapter with two
identical paragraphs puts the first one's translation into both. Chunks here
already carry an `IndexTag`, so position is available and exact.

Two outputs, as mantra.press has: markdown, and whatever the reader needs. Only
markdown is in scope here — the app renders chunks itself today — but the walk
is shared.

### Five call sites, moved together

`splitParagraphs` is called from five places and they have to agree, or the
count shown to a member differs from the chunks in the batch they are approving:

| call site | change |
|---|---|
| `ChunkEvent.splitOf` | splits into `Block`s, takes `.text` for the chunk |
| `AddChapterViewModel.addChapter` | the cap check counts blocks |
| `AddChapterScreen` | the live counter counts blocks |
| `ProposedEvent.summarize` | **stops splitting** — see below |
| `ChapterChunkSplitTest` | its parity assertion still holds, against the new function |

`splitParagraphs` goes rather than delegating. A second splitter left in the
codebase is a second answer to "what is a chunk", and the next caller will pick
whichever they find first.

**`ProposedEvent.summarize` has a latent bug this phase should fix rather than
port.** It reports a proposed chapter's chunk count by re-splitting the
chapter's text (`ProposedEvent.kt:77`), so the number a member reads in the
approval gate is derived rather than observed — and across a splitter change it
is simply wrong, since the batch was built by the proposer's splitter and
counted by the reader's. The count is sitting right there: the session's items
minus the lead. Count those.

### What it costs, and the phase it moves

Block splitting produces **more** chunks than blank-line splitting — every
heading and every list item is its own now — so both of Phase 2's limits bind
sooner. A list-heavy chapter can double or triple its chunk count against
`MAX_CHUNKS_PER_CHAPTER = 63`, and every extra chunk adds an event envelope
(~370 bytes before it says anything) to the proposal.

So **`MAX_ORIGINAL_TEXT_BYTES` must be measured after this lands, not before.**
Either take this phase ahead of Phase 2, or treat Phase 2's constant as
provisional and re-run its encoded-proposal measurement here. The second is
cheaper and the measurement is the same test either way.

### Tests

`commonTest`, all of it pure:

- `MarkdownBlockTest` against the generated fixtures — the bulk of it, and the
  part worth reviewing.
- the three departures, asserted as departures: a fenced block and an indented
  block yield no chunk, a table yields one, a reference definition yields none.
- inline markup survives: `*italic*` and `[text](url)` are in the chunk's text.
- `MarkdownProducerTest`: round-trip with no translations reproduces the input
  byte for byte (the assertion that catches every markup-reassembly bug at
  once), and a chapter with two identical paragraphs translated differently
  comes back with both translations in the right places — the case mantra.press
  gets wrong.
- `ChapterChunkSplitTest` keeps its existing round-trip and id-stability
  assertions, now with the converter's output shape as a fixture.

---

## Phase 9 — tests and rollout

**A day, and the list is short because most of it is in the phases.**

New, per phase: `ChapterTextTest` (decode), the byte-cap boundary and the
encoded-proposal measurement in `ChapterBatchProposalJvmTest`, the
three-proposals-in-flight index test, `MarkdownBlockTest` against the generated
fixtures and `MarkdownProducerTest`'s byte-for-byte round trip, and a
`ChapterUploadJvmTest` beside the
existing batch tests that drives the view model over a real in-memory database
and asserts the whole shape: N files in, N sessions out with one lead chapter
each, indices `base..base+N-1`, chunks matching each file's paragraphs, one
scaffolding session for the lot, and — the one that matters — that a failure on
file 4 leaves files 1–3 proposed and recoverable rather than rolled back.

`jvmTest`, not `commonTest`, because it is Room-backed; `./gradlew
:composeApp:jvmTest`, per the project's build notes.

Rollout needs no flag. The chapter and chunk kinds, their tags and their apply
branches are unchanged, so an older build receives an uploaded chapter as the
chapter it is. Two things do change what a peer sees, and neither needs
negotiation: a room will see N proposals where it used to see one (which an
older build handles — it is N ordinary sessions), and Phases 7–8 change chunk
boundaries and chunk text for new chapters only — safely, because nothing
re-derives chunks from a chapter's text: `splitOf` runs at proposal time and the
chronicle rebuilds from rows. The byte cap of Phase 2 is the only
change that makes the app *refuse* something it used to attempt, and what it
used to attempt was a session that stalled.

Order: 1 → 2 → 3 before 4; 5 and 6 after 4; 7 → 8 whenever, but before the
first real upload, since a chapter already signed keeps the chunks it was signed
with. Phases 2, 3, 5 and 8 are each shippable alone and each fix something that
is wrong today at N = 1 — the index collision, the unchecked proposal size, the
scaffolding fan-out and the splitter — which is the argument for landing them as
their own commits rather than inside the feature.

---

## Appendix — considered and rejected

**One session for the whole upload.** The three reasons are in § The constraint.
The size cap alone kills it at two chapters.

**One chapter event per upload, with the files concatenated.** Fits, signs once,
and destroys the thing being modelled: a chapter is the unit of work assigned to
a translator, and `ChapterEvent` carries one index.

**A manifest: sign one event committing to N chapter ids, derive the chapters
from it.** Cheapest option on this page, and already argued down in
[frost-batch-signing.md](./frost-batch-signing.md) — each chapter stops carrying
its own verifiable signature, and the point of
[shared-key-derivation.md](./shared-key-derivation.md) is that a reader holding
one signed thing can check it against the room it was found in without a lookup.
Chapters are read apart; they get their own signatures.

**Derive the chunks on arrival instead of signing them.** This is what the app
did before batch signing, and it was deliberately changed: "a chunk that can be
checked on its own is worth more than one that can only be checked by
re-deriving it". It would double the number of uploadable paragraphs, which is
not worth it. Note the asymmetry, though — it *is* the right answer for the
translation scaffolding, which is why that is a separate session rather than
separate events.

**A remote upload: post the files somewhere and have the group sign a URL.** No.
An artifact's `url` tag is a reference to a published work; the text the group
signs is the text, and a chapter whose words live behind a link is a chapter the
group has not actually agreed to.

**An uploads table to make an interrupted upload resumable.** A schema version
for state that Phase 3 can read off the open sessions instead. The proposals are
the record of what was uploaded; `unsignedEvents` is how to read it. If resume
ever needs more than "re-pick the files and the duplicate check tells you what
already went", revisit — but it will need a reason that is not available today.

**Mirroring mantra.press's xlsx import** (`POST /projects/:id/import`,
`server/router/projects/index.js:1565`) — a spreadsheet of translations rather
than chapter markdown. A different feature on the translation side of the
hierarchy, with the same batching argument and a different parser. Out of scope
here, and the plan above is the shape it would follow.
