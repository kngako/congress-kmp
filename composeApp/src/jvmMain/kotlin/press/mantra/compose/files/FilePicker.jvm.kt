package press.mantra.compose.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import press.mantra.compose.text.ChapterText
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.InputStream

/**
 * AWT's `FileDialog`, which is the native chooser on all three desktops.
 *
 * `JFileChooser` was the alternative and is worse here: it is Swing's own
 * dialog, so it looks like Swing rather than like the host, and on macos it is
 * notably not the file panel every other application shows. `FileDialog` maps
 * to the platform's own in each case.
 *
 * **It blocks the calling thread**, which on desktop Compose is the UI thread,
 * so showing it from a click handler freezes the window until it closes -- and
 * reading the files afterwards would freeze it again. Both go to
 * [Dispatchers.IO] and the result comes back through [onPicked] on the main
 * dispatcher, which is the same shape the android launcher has by construction.
 *
 * The scope is the composition's. A dialog whose composable has left is a
 * dialog whose result has nowhere to go, and cancelling the read is the right
 * behaviour rather than a leak to avoid.
 */
@Composable
actual fun rememberFilePicker(
    title: String,
    onPicked: (List<PickedFile>) -> Unit,
): FilePicker {
    val scope = rememberCoroutineScope()

    return remember(scope, title, onPicked) {
        FilePicker { multiple ->
            scope.launch {
                val picked = withContext(Dispatchers.IO) { chooseFiles(title, multiple) }
                onPicked(picked)
            }
        }
    }
}

private fun chooseFiles(title: String, multiple: Boolean): List<PickedFile> {
    // A null parent is allowed and gives an unowned dialog. It is what a Compose
    // desktop window gets us without reaching for the AWT window behind it,
    // which `ComposeWindow` exposes but only from a `FrameWindowScope` that the
    // app's navigation graph does not thread through.
    val dialog = object : FileDialog(null as Frame?, title, LOAD) {}
    dialog.isMultipleMode = multiple
    dialog.isVisible = true

    val chosen = if (multiple) {
        dialog.files?.toList().orEmpty()
    } else {
        listOfNotNull(dialog.directory?.let { dir -> dialog.file?.let { File(dir, it) } })
    }

    return chosen.mapNotNull(::readPickedFile)
}

/** As on android: one byte past the cap, so an over-size file reads as over-size. */
private fun readPickedFile(file: File): PickedFile? =
    runCatching {
        PickedFile(
            name = file.name,
            bytes = file.inputStream().use { it.readAtMost(ChapterText.MAX_FILE_BYTES + 1) },
        )
    }.onFailure { error ->
        Logger.withTag(TAG).e("Could not read the picked file ${file.path}", error)
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
