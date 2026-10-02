package press.mantra.compose.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import press.mantra.compose.text.ChapterText
import java.io.InputStream

/**
 * The Storage Access Framework, which needs no manifest permission at all.
 *
 * `OpenDocument` and `OpenMultipleDocuments` are a handoff to the system
 * picker: the app never sees the file system, and the grant it comes back with
 * covers exactly the documents the reader chose. `READ_EXTERNAL_STORAGE` and
 * its API 33 replacements are for apps that browse storage themselves, which
 * this does not.
 *
 * **Both contracts are registered, not just the multiple one.** Registering
 * only `OpenMultipleDocuments` and ignoring the flag would work, and would mean
 * that the one-file case -- loading a chapter into the add form -- offers a
 * picker that lets the reader select six. Asking for one thing and accepting
 * six is how a form ends up quietly discarding five of them.
 *
 * The mime types are deliberately loose. Markdown has no registered type:
 * providers report `.md` as `text/markdown`, `text/plain`, `application/octet-stream`
 * or nothing at all depending on the provider and the Android version, so a
 * narrow filter hides the very files this is for. Filtering on `text/*` plus
 * `*/*` and letting `ChapterText.decode` be the judge is both more permissive
 * at the picker and stricter about what becomes a chapter.
 *
 * `title` is unused. `Intent.EXTRA_TITLE` is honoured by `CreateDocument`, where
 * it is the suggested filename, and ignored by the open contracts -- the system
 * picker titles itself. Nothing is lost by taking the parameter; something would
 * be lost by making desktop's title a literal to avoid it.
 */
@Composable
actual fun rememberFilePicker(
    title: String,
    onPicked: (List<PickedFile>) -> Unit,
): FilePicker {
    val context = LocalContext.current
    val resolver = remember(context) { context.contentResolver }

    val single = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        onPicked(listOfNotNull(uri?.let { resolver.readPickedFile(it) }))
    }

    val multiple = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onPicked(uris.mapNotNull { resolver.readPickedFile(it) })
    }

    return remember(single, multiple) {
        FilePicker { wantsMultiple ->
            if (wantsMultiple) multiple.launch(MIME_TYPES) else single.launch(MIME_TYPES)
        }
    }
}

private val MIME_TYPES = arrayOf("text/*", "*/*")

/**
 * Reads a picked document, stopping one byte past the cap.
 *
 * One byte *past*, so an over-size file comes back over-size rather than
 * truncated to exactly the limit -- a truncated chapter would decode cleanly
 * and be proposed with its ending missing.
 *
 * A failure here is a document that went away between being picked and being
 * read, or a provider that threw; logged and dropped, because the alternative
 * is failing the whole selection over one file.
 */
private fun ContentResolver.readPickedFile(uri: Uri): PickedFile? =
    runCatching {
        val bytes = openInputStream(uri)?.use { it.readAtMost(ChapterText.MAX_FILE_BYTES + 1) }
            ?: return null
        PickedFile(name = displayName(uri) ?: uri.lastPathSegment.orEmpty(), bytes = bytes)
    }.onFailure { error ->
        Logger.withTag(TAG).e("Could not read the picked document $uri", error)
    }.getOrNull()

private fun ContentResolver.displayName(uri: Uri): String? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var filled = 0
    while (filled < limit) {
        val read = read(buffer, filled, limit - filled)
        if (read < 0) break
        filled += read
    }
    return buffer.copyOf(filled)
}

private const val TAG = "FilePicker"
