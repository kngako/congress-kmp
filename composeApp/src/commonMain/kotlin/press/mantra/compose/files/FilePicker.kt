package press.mantra.compose.files

import androidx.compose.runtime.Composable

/**
 * A file the reader picked, as its name and its bytes.
 *
 * Bytes rather than a path or an open stream, and that is a decision rather
 * than laziness. A path is meaningless on iOS the moment the picker's security
 * scope closes, so a path would be a handle that works on two platforms and
 * expires on the third. A stream would push the size cap and the decode into
 * whichever coroutine happened to read it. Chapters are tens of kilobytes;
 * `ChapterText.MAX_FILE_BYTES` bounds the read and the whole file fits in hand.
 *
 * [name] is for naming the chapter, not for deciding what the file is -- that
 * is `ChapterText.decode`'s job, by looking at the bytes. On android the name
 * comes from the document provider and can be absent or a lie; it is a default
 * a person edits, never a check.
 */
data class PickedFile(
    val name: String,
    val bytes: ByteArray,
) {
    // ByteArray makes the generated equals/hashCode identity-based, which is
    // wrong for a data class that will sit in a list in UI state and be
    // compared across recompositions.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return name == other.name && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/** Opens the platform's file chooser. */
fun interface FilePicker {
    fun launch(multiple: Boolean)
}

/**
 * The platform's file chooser, wired to hand [onPicked] what came back.
 *
 * `expect` rather than a dependency. The obvious alternative is FileKit, which
 * covers all three targets from common code -- but this is the only thing in
 * the app that needs a dialog, the three calls below are a dozen lines each,
 * and the project already keeps four platform seams of exactly this shape
 * (`PlatformContext`, `PlatformDatabaseBuilder`, `platformReducedMotion`,
 * `platformThemeContrast`). A dependency whose Compose version has to track
 * ours is a poor trade for that.
 *
 * **Nothing is read beyond `ChapterText.MAX_FILE_BYTES`.** Each actual stops
 * there, so picking a video by mistake costs a bounded read rather than the
 * whole file, and the over-size file still arrives -- with more bytes than the
 * cap allows -- so that `decode` can say `TooLarge` instead of the picker
 * silently dropping it.
 *
 * Cancelling hands back an empty list, which is how a caller tells "no files"
 * from "files that would not decode".
 *
 * [title] is the dialog's own title, read from the string catalogue by the
 * caller because `stringResource` is composable and the dialog is not. Only
 * desktop uses it: android's Storage Access Framework and iOS's document
 * browser each title themselves and offer no way to say otherwise. Taken on all
 * three anyway, so that the one platform which shows text to the reader gets it
 * from the catalogue like every other string in the app.
 */
@Composable
expect fun rememberFilePicker(
    title: String,
    onPicked: (List<PickedFile>) -> Unit,
): FilePicker
