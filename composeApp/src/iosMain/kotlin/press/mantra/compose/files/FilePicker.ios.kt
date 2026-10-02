package press.mantra.compose.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject
import press.mantra.compose.text.ChapterText

/**
 * `UIDocumentPickerViewController`, presented over whatever is on screen.
 *
 * **Unverified on the host this was written on.** The two ios targets are
 * declared only on macos -- see the gate in `composeApp/build.gradle.kts` and
 * the reason in `docs/jvm-target.md` -- so this file is not compiled on linux
 * and has never been run. It is written to the same shape as the other eight
 * `.ios.kt` actuals in this source set and should be read on a mac before it is
 * trusted. The alternative, a stub that hands back no files, would be worse:
 * it reads as a working picker that the reader's chapters vanish into.
 *
 * `forOpeningContentTypes` rather than the deprecated `initWithDocumentTypes`,
 * which takes UTI strings and was removed from the iOS 14 API surface in all
 * but name.
 *
 * **The types are `UTTypePlainText` and `UTTypeData`**, for the reason android's
 * are loose: markdown has no registered UTI, so `.md` resolves to
 * `public.plain-text` on some devices and to `public.data` on others depending
 * on what else is installed. Offering both means the files this exists for are
 * selectable; `ChapterText.decode` is what decides whether they are chapters.
 *
 * **The delegate is held by the composition, not by the view controller.** UIKit
 * holds its delegate weakly, so a delegate created inline is collected before
 * the reader finishes choosing and the callback never fires -- the classic
 * version of this bug. `remember` is what keeps it alive, and is why it is
 * built here rather than inside `launch`.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(
    title: String,
    onPicked: (List<PickedFile>) -> Unit,
): FilePicker {
    val delegate = remember(onPicked) { PickerDelegate(onPicked) }

    return remember(delegate) {
        FilePicker { multiple ->
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypePlainText, UTTypeData),
                asCopy = true,
            )
            picker.allowsMultipleSelection = multiple
            picker.delegate = delegate

            val presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
            if (presenter == null) {
                Logger.withTag(TAG).e("No root view controller to present the document picker from")
                onPicked(emptyList())
            } else {
                presenter.topmost().presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

/**
 * `asCopy = true` above puts the chosen files in the app's own temporary
 * directory, which is what makes reading them afterwards legal without
 * `startAccessingSecurityScopedResource`. It is also why [PickedFile] carries
 * bytes rather than a url: the copy is the app's to read now and the system's
 * to delete whenever it likes.
 */
@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(
    private val onPicked: (List<PickedFile>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        onPicked(didPickDocumentsAtURLs.filterIsInstance<NSURL>().mapNotNull(::readPickedFile))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(emptyList())
    }
}

/** As on the other two platforms: one byte past the cap, so over-size reads as over-size. */
@OptIn(ExperimentalForeignApi::class)
private fun readPickedFile(url: NSURL): PickedFile? =
    runCatching {
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        PickedFile(
            name = url.lastPathComponent ?: "",
            bytes = data.toByteArray(limit = ChapterText.MAX_FILE_BYTES + 1),
        )
    }.onFailure { error ->
        Logger.withTag(TAG).e("Could not read the picked document $url", error)
    }.getOrNull()

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(limit: Int): ByteArray {
    val size = minOf(length.toInt(), limit)
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), bytes, size.toULong())
    }
    return out
}

/**
 * The controller actually on screen.
 *
 * Presenting from the root controller while a sheet is already up throws, and
 * this app reaches the add-chapter form through navigation that may have put one
 * there. Walking `presentedViewController` is the documented way to find the
 * one that can present.
 */
private fun UIViewController.topmost(): UIViewController =
    presentedViewController?.topmost() ?: this

private const val TAG = "FilePicker"
