package ac.aux.compose.ui.composable.widgets.feed

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.time.Clock

val DUMMY_EVENTS = listOf(
    Event(
        id = KeyPair().pubKey.toString(),
        pubKey = KeyPair().pubKey.toString(),
        createdAt = Clock.System.now().toEpochMilliseconds(),
        kind = 1,
        tags = emptyArray(),
        content = "This is just a note",
        sig = KeyPair().pubKey.toString(),
    ),
    Event(
        id = KeyPair().pubKey.toString(),
        pubKey = KeyPair().pubKey.toString(),
        createdAt = Clock.System.now().toEpochMilliseconds(),
        kind = 1,
        tags = emptyArray(),
        content = "This is another note",
        sig = KeyPair().pubKey.toString(),
    ),
    Event(
        id = KeyPair().pubKey.toString(),
        pubKey = KeyPair().pubKey.toString(),
        createdAt = Clock.System.now().toEpochMilliseconds(),
        kind = 1,
        tags = emptyArray(),
        content = "This is a better note",
        sig = KeyPair().pubKey.toString(),
    )
)
