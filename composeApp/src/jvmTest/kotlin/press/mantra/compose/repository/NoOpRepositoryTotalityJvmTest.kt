package press.mantra.compose.repository

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every member of every `NO_OP_*` repository answers its call. None is left stubbed out.
 *
 * Every one in `commonMain/.../repository/`, found by reading the directory rather than by
 * naming files -- see [declaration].
 *
 * The rule is worth a test of its own because breaking it does not fail where it is broken.
 * A `TODO()` in one of these objects throws `NotImplementedError`, and the callers are view
 * models that reach their repository from `viewModelScope.launch(Dispatchers.IO)` -- a
 * coroutine nobody awaits. The error escapes to the platform handler, where
 * kotlinx-coroutines-test's `ExceptionCollector` stashes it (`unprocessedExceptions`, because
 * a `runBlocking` test has no `TestScope` registered to take it), and the *next* `runTest` to
 * start drains the stash and rethrows it as `UncaughtExceptionsBeforeTest` -- under that
 * test's name, for a call it never made. The real stack trace survives only as a suppressed
 * cause several frames down.
 *
 * That cost a debugging session once: `queueNegentropySynchronizeRequest` was a `TODO()`,
 * `CeremonyQueueRoutingJvmTest` reached it through `ChatMessageListViewModel.initiate()`, and
 * `NavigationRoutingTest > an account already on the device does not stay on the loading
 * screen` failed four times a run while the test that caused it passed. Seventeen test files
 * build on `NO_OP_NOSTR_REPOSITORY` alone, so one stub is never one test's problem.
 *
 * So this is checked at the only altitude where it can be checked cheaply and totally: the
 * source. Calling all sixty-odd members for real would mean a fixture for every parameter
 * type, and a test that fails because a fixture was wrong tells you less than this one does.
 * A reflective sweep has the same problem -- it cannot tell a body that threw from an argument
 * it could not build.
 *
 * It fails under its own name, which is the whole point.
 */
class NoOpRepositoryTotalityJvmTest {

    /**
     * Every file that declares one, found rather than listed.
     *
     * A list would be wrong the first time a ninth repository is added -- and was: the
     * first version of this test named six files when the tree held eight, having missed
     * `MantraRepository` and `CachingImportRepository` (both total, which is luck rather
     * than cover). Discovery is the point of the test, so it discovers.
     */
    private val declaration = Regex("""\bval\s+NO_OP_[A-Z0-9_]+""")

    /**
     * A floor, in the spirit of `docs/scripts/m3-audit.sh`: the way this test breaks
     * silently is by finding nothing to check -- a moved package, a renamed convention --
     * and then passing. Eight is the count on 2026-10-02. Raise it when one is added.
     */
    private val knownSingletons = 8

    @Test
    fun `no no-op repository leaves a member unimplemented`() {
        val directory = repositoryDirectory()

        val sources = (directory.listFiles() ?: emptyArray())
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.name to it.readText() }
            .filter { (_, source) -> declaration.containsMatchIn(source) }
            .sortedBy { (name, _) -> name }

        assertTrue(
            sources.size >= knownSingletons,
            "Found ${sources.size} no-op repositories in ${directory.path}, expected at " +
                "least $knownSingletons. Either one was removed -- lower the floor in the " +
                "same change -- or this test has stopped finding them and is passing on " +
                "nothing. Found: ${sources.map { it.first }}"
        )

        val offences = sources.flatMap { (name, source) -> stubbedMembers(name, source) }

        assertEquals(
            emptyList(),
            offences,
            "A no-op repository member is stubbed out. Give it the answer a repository with " +
                "nothing behind it would give -- null, an empty collection, flowOf(...) of " +
                "one of those, Unit, 0, false -- and never TODO(). See the contract on " +
                "NostrRepository.NO_OP_NOSTR_REPOSITORY. Stubs found:\n" +
                offences.joinToString("\n") { "  $it" }
        )
    }

    /**
     * `file:line member` for every `TODO(` in code, comments excluded -- the contract on
     * `NO_OP_NOSTR_REPOSITORY` names `TODO()` in its prose, and a doc comment explaining the
     * rule must not trip the check that enforces it.
     */
    private fun stubbedMembers(name: String, source: String): List<String> {
        val found = mutableListOf<String>()
        var inBlockComment = false
        var member = "(before the first declaration)"

        source.lines().forEachIndexed { index, raw ->
            var line = raw
            if (inBlockComment) {
                val end = line.indexOf("*/")
                if (end < 0) return@forEachIndexed
                line = line.substring(end + 2)
                inBlockComment = false
            }
            val blockStart = line.indexOf("/*")
            if (blockStart >= 0) {
                inBlockComment = !line.substring(blockStart).contains("*/")
                line = line.substring(0, blockStart)
            }
            val lineComment = line.indexOf("//")
            if (lineComment >= 0) line = line.substring(0, lineComment)

            if (line.contains("fun ")) member = line.trim()
            if (line.contains("TODO(")) found += "$name:${index + 1}  $member"
        }
        return found
    }

    /**
     * Found by walking up from the working directory rather than taken from it, because the
     * one is the `composeApp` project directory under `:composeApp:jvmTest` and the other is
     * the repository root when the same test is run from the root project. A test about
     * things that fail silently does not get to pass because it could not find its input.
     */
    private fun repositoryDirectory(): File {
        val relative = "composeApp/src/commonMain/kotlin/press/mantra/compose/repository"
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile
        }
        throw AssertionError(
            "Could not find $relative above ${File("").absolutePath} -- this test read nothing."
        )
    }
}
