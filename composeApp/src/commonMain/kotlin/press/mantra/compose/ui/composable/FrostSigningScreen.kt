package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.composable.widgets.profile.ProfileAvatar
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.FrostSigningViewModel
import press.mantra.compose.ui.view.state.FrostSigningUIState

/**
 * One signing session, and the member's decision about it.
 *
 * A ceremony gets three approval screens because it asks three different
 * questions. Signing asks one — sign this or do not — so there is one screen,
 * and it has to carry the whole case for the answer: what is being signed, who
 * else has agreed, and what the group is still waiting on.
 *
 * It stays useful after the decision. A session cannot finish until enough
 * members take part, so a member who has already signed still needs to see
 * whose door to knock on, and the ladder is the only place that says.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostSigningScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    sessionId: String?,
    initialFrostSigningUIState: FrostSigningUIState = FrostSigningUIState.Loading,
    chatRepository: ChatRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateBack: () -> Unit,
) {
    val frostSigningViewModel: FrostSigningViewModel = viewModel(
        factory = FrostSigningViewModel.factory(
            chatRoomId = chatRoomId,
            sessionId = sessionId,
            activeUserPublicKey = activeUserPublicKey,
            initialFrostSigningUIState = initialFrostSigningUIState,
            chatRepository = chatRepository,
            frostSigningRepository = frostSigningRepository
        )
    )

    // Nothing loads the room or starts watching the session until this runs.
    LaunchedEffect(true) {
        if (initialFrostSigningUIState == FrostSigningUIState.Loading) {
            frostSigningViewModel.initiate()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sign with the group's key",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = frostSigningViewModel.frostSigningUIState) {
            is FrostSigningUIState.Loading -> Loading(padding)

            is FrostSigningUIState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = state.message, textAlign = TextAlign.Center)
            }

            is FrostSigningUIState.Loaded -> {
                // Loaded means the room is loaded, not the session: initiate() sets
                // this state and only then starts collecting, so the first emission
                // always has a null session. Reading that as "no such session" would
                // flash an error on the way in.
                val session = state.session ?: return@Scaffold Loading(padding)

                val proposed = frostSigningViewModel.proposedEvents(state.items)

                // Every event of the batch has to be readable before any of it can
                // be signed. A member cannot check what they cannot see, and a
                // batch is all-or-nothing: agreeing to the two that rendered would
                // be agreeing to the third as well.
                val readable = state.items.isNotEmpty() && proposed.size == state.items.size

                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    WhatIsBeingSigned(proposed, state.items.size)

                    HorizontalDivider()

                    Text(
                        text = statusOf(session),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    session.failureReason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Members",
                        style = MaterialTheme.typography.labelMedium
                    )

                    // Named rather than counted, for the same reason the ceremony's
                    // ladder names people: "1 of 2" does not tell anyone whose door
                    // to knock on, and a session stalls until somebody knocks.
                    state.localChatRoom.localParticipants
                        .distinctBy { it.participant.participantPublicKey }
                        .forEach { localParticipant ->
                            val member = localParticipant.participant.participantPublicKey

                            Card {
                                ListItem(
                                    leadingContent = {
                                        ProfileAvatar(
                                            publicKey = member,
                                            profile = localParticipant.profile
                                        )
                                    },
                                    trailingContent = {
                                        when {
                                            member in state.signed -> Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Signed",
                                                tint = MaterialTheme.colorScheme.primary
                                            )

                                            member in state.offeredNonce -> Icon(
                                                Icons.Default.HourglassEmpty,
                                                contentDescription = "Ready to sign"
                                            )

                                            else -> Icon(
                                                Icons.Default.RadioButtonUnchecked,
                                                contentDescription = "Not yet",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    headlineContent = {
                                        Text(
                                            text = localParticipant.profile
                                                ?.humanReadableNameOrPubkey()
                                                ?: member
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = when {
                                                member in state.signed -> "Signed their part"
                                                member in state.offeredNonce -> "Ready to sign"
                                                else -> "Has not taken part yet"
                                            }
                                        )
                                    }
                                )
                            }
                        }

                    // Asked rather than re-derived: the manager owns when a session
                    // is still waiting on its owner, and a second copy of that rule
                    // here is a second copy to keep in step.
                    if (FrostSigningManager.isAwaitingApproval(session, state.items)) {
                        HorizontalDivider()

                        Text(
                            text = "Nothing has been published from this device yet. Signing " +
                                "puts your share behind " +
                                (if (proposed.size > 1) "all ${proposed.size} of these events" else "this event") +
                                "; it cannot be taken back.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // A batch can hide an event below the fold in a way one
                        // event cannot: what is off-screen is not further detail
                        // about the thing on screen, it is a different thing the
                        // member would also be signing. So a batch's Sign button
                        // waits until the list has been read to the end. maxValue
                        // is Int.MAX_VALUE until the first layout, and 0 when
                        // everything already fits.
                        val seenEverything = proposed.size <= 1 ||
                            (scrollState.maxValue != Int.MAX_VALUE && scrollState.value >= scrollState.maxValue)

                        if (readable && !seenEverything) {
                            Text(
                                text = "Read to the end of the list to sign.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                enabled = readable && seenEverything &&
                                    !frostSigningViewModel.isActionPending.value,
                                onClick = { frostSigningViewModel.approve(onNavigateBack) }
                            ) {
                                Icon(Icons.Default.Draw, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Sign")
                            }

                            // Declining stays available whatever the screen could
                            // not show. A member who cannot check what they are
                            // being asked to sign should still be able to say no,
                            // and saying nothing is indistinguishable from a phone
                            // in a pocket -- which leaves the group waiting.
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                enabled = !frostSigningViewModel.isActionPending.value,
                                onClick = { frostSigningViewModel.decline(onNavigateBack) }
                            ) {
                                Text("Don't sign")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Loading(padding: androidx.compose.foundation.layout.PaddingValues) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(padding).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(50.dp))
        CircularProgressIndicator()
    }
}

/**
 * What the group is being asked to put its name to.
 *
 * Shown as the thing rather than as an event: a member deciding whether to sign
 * is deciding about a dialect or an artifact, and "kind 30304" answers a
 * question nobody asked. The raw kind stays for anything not recognised, since
 * refusing to describe an event is better than describing it wrongly.
 */
@Composable
private fun WhatIsBeingSigned(events: List<Event>, expected: Int) {
    if (expected == 0 || events.size != expected) {
        Text(
            text = if (expected <= 1) {
                "This session's event could not be read, so there is nothing to check " +
                    "before signing. Don't sign it."
            } else {
                "Only ${events.size} of this session's $expected events could be read, so " +
                    "there is no way to check what you would be signing. Don't sign it."
            },
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        if (events.size > 1) {
            Text(
                text = "${events.size} events, signed together",
                style = MaterialTheme.typography.labelMedium
            )
        }

        events.forEachIndexed { index, event ->
            if (index > 0) HorizontalDivider()

            OneThingBeingSigned(event)
        }

        Text(
            text = "Signed by the group, not by you. Once enough members sign, " +
                (if (events.size > 1) "these are" else "this is") +
                " published under the group's shared key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One event of the batch, described as the thing it is. */
@Composable
private fun OneThingBeingSigned(event: Event) {
    val (label, detail) = when (event.kind) {
        DialectEvent.KIND -> "New dialect" to DialectEvent(
            event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
        ).let { dialect ->
            listOfNotNull(dialect.name(), dialect.country(), dialect.language())
                .joinToString(" · ")
        }

        ArtifactEvent.KIND -> "New artifact" to ArtifactEvent(
            event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
        ).let { artifact ->
            // The url is the substance of an artifact -- signing one is putting
            // the group's name to what it points at -- so it goes next to the
            // name rather than being left for the detail screen afterwards.
            listOfNotNull(event.content, artifact.versionLabel(), artifact.url())
                .joinToString(" · ")
        }

        ChapterEvent.KIND -> "New chapter" to ChapterEvent(
            event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
        ).let { chapter ->
            // The text is the substance of a chapter -- signing one is putting
            // the group's name to what everybody will translate from -- but it
            // is a whole chapter, so its size stands in for it here. The chunk
            // count says how many of the batch's other items came out of this
            // text, which is the rest of what is being signed.
            val chunkCount = chapter.originalText()?.let { Markdown.splitParagraphs(it).size }

            listOfNotNull(
                chapter.name(),
                chapter.wordCount()?.let { "$it words" },
                chunkCount?.let { "$it ${if (it == 1) "chunk" else "chunks"}" }
            ).joinToString(" · ")
        }

        TranslationArtifactVersionEvent.KIND -> "New translation" to TranslationArtifactVersionEvent(
            event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
        ).let { translation ->
            // Named after the dialect it is into, which is the whole of what is
            // being decided: everything else in the batch follows from it.
            listOfNotNull(translation.name(), translation.visibility(), translation.license())
                .joinToString(" · ")
        }

        TranslationChapterEvent.KIND -> "Chapter of the translation" to TranslationChapterEvent(
            event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
        ).let { chapter ->
            // A translation chapter is a place for translated chunks to hang
            // off, so there is nothing in it to read -- only where in the work
            // it sits. Counted from one, the way the chapter list reads.
            chapter.index()?.let { "Chapter ${it + 1}" } ?: "Position unknown"
        }

        // The one thing a group signs that is about the group rather than about
        // its work, and the only one a member sees before the room has done
        // anything. Shown as the path and the ceremony rather than as the key:
        // the key is the room's own id, which the member is already looking at.
        GroupKeyStateEvent.KIND -> "This group's shared key" to listOfNotNull(
            GroupKeyStateEvent.parsePath(event.tags)?.let(SharedKeyDerivation::formatPath),
            GroupKeyStateEvent.parseDkgSessionId(event.tags)?.take(12)
        ).joinToString(" · ")

        else -> "Event of kind ${event.kind}" to event.content
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)

        Text(text = detail, style = MaterialTheme.typography.titleMedium)
    }
}

private fun statusOf(session: FrostSigningSession): String = when (session.stage) {
    FrostSigningStage.COLLECTING_NONCES ->
        "Waiting for ${session.threshold} of ${session.participantCount} members to take part."

    FrostSigningStage.COLLECTING_PARTIAL_SIGNATURES ->
        if (session.isSigner()) {
            "You are one of the signers. Waiting on the rest of them."
        } else {
            "Enough members took part without needing you. Waiting on them to sign."
        }

    FrostSigningStage.COMPLETE -> "Signed."

    FrostSigningStage.FAILED -> "Abandoned. Nothing was signed, and it is safe to ask again."
}

@Preview
@Composable
private fun FrostSigningScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            FrostSigningScreen(
                activeUserPublicKey = "",
                chatRoomId = "chatRoomId",
                sessionId = "sessionId",
                initialFrostSigningUIState = FrostSigningUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "chatRoomId",
                            userPublicKey = "",
                            subject = "Group (#admins)",
                            description = null,
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        )
                    )
                ),
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                onNavigateBack = {}
            )
        }
    }
}
