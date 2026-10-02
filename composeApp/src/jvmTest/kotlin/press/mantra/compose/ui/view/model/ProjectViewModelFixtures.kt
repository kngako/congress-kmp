package press.mantra.compose.ui.view.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.FrostSigningRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Waits for [read] to stop returning null, or gives up.
 *
 * These view models hold their state in `mutableStateOf` rather than a flow --
 * the convention the rest of this family follows -- so there is nothing to
 * collect, and `initiate` launches on `Dispatchers.IO`, so the call returns
 * before the work is done. Polling in real time for the same reason
 * `NavigationIdentityRoutingJvmTest` does: `runTest` would advance a virtual
 * clock past a real database read rather than waiting for it.
 */
suspend fun <T> awaitValue(timeoutMillis: Long = 10_000, read: () -> T?): T =
    withTimeout(timeoutMillis) {
        var value = read()
        while (value == null) {
            delay(5)
            value = read()
        }
        value
    }

/**
 * The room, the library and the translation every project view model needs
 * before it has anything to do.
 *
 * Shared rather than copied three times: the chain is five foreign keys deep and
 * none of it is what any of these tests are about.
 */
class ProjectRoom(private val db: MantraDatabase) {
    val roomId = "b".repeat(64)
    val signature = "0".repeat(128)

    val member = "a".repeat(64)
    val otherMember = "7".repeat(64)

    val dialectId = "1".repeat(64)
    val artifactId = "2".repeat(64)
    val versionId = "3".repeat(64)
    val secondVersionId = "6".repeat(64)
    val translationId = "5".repeat(64)

    val artifactName = "To Kill a Mockingbird"
    val versionLabel = "1.0"
    val translationName = "Sesotho"

    suspend fun seed() {
        val nostrEventId = "e".repeat(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = member,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = signature,
            )
        )
        db.profileDao().upsert(
            Profile(publicKey = member, userName = "kgothatso", nostrEventId = nostrEventId)
        )
        db.chatRoomDao().upsert(
            ChatRoom(
                id = roomId,
                userPublicKey = member,
                subject = "a translation room",
                description = null,
                mlsGroupState = null,
            )
        )

        db.mantraDialectDao().upsert(
            MantraDialect(
                id = dialectId,
                publicKey = roomId,
                name = "Sesotho",
                country = "ZA",
                language = "st",
                signature = signature,
                chatRoomId = roomId,
            )
        )
        db.mantraArtifactDao().upsert(
            MantraArtifact(
                id = artifactId,
                publicKey = roomId,
                name = artifactName,
                url = "https://harper.com/2-kill-Bird",
                visibility = "private",
                dialectId = dialectId,
                license = "cc",
                chatRoomId = roomId,
                signature = signature,
            )
        )
        listOf(versionId to versionLabel, secondVersionId to "2.0").forEach { (id, label) ->
            db.mantraArtifactVersionDao().upsert(
                MantraArtifactVersion(
                    id = id,
                    artifactId = artifactId,
                    publicKey = roomId,
                    versionLabel = label,
                    signature = signature,
                    chatRoomId = roomId,
                )
            )
        }
        db.mantraTranslationArtifactVersionDao().upsert(
            MantraTranslationArtifactVersion(
                id = translationId,
                publicKey = roomId,
                artifactVersionId = versionId,
                dialectId = dialectId,
                name = translationName,
                visibility = "private",
                license = "cc",
                signature = signature,
                chatRoomId = roomId,
            )
        )
    }

    /** A project already signed into the room, for the add-to flow to add to. */
    suspend fun project(
        id: String = "p".repeat(64),
        name: String = "Gospel of John",
        owners: Set<HexKey> = setOf(roomId),
    ): MantraProject = MantraProject(
        id = id,
        publicKey = roomId,
        name = name,
        description = "The four chapters.",
        creatorPublicKey = member,
        ownerPublicKeys = owners.sorted().joinToString(","),
        chatRoomId = roomId,
        signature = signature,
    ).also { db.mantraProjectDao().upsert(it) }

    suspend fun localChatRoom(): LocalChatRoom =
        db.chatRoomDao().findChatRoomById(roomId) ?: error("the room was not seeded")
}

/**
 * A signing repository that records what it was asked to sign instead of asking
 * a group.
 *
 * Delegates everything else to the no-op, so it keeps compiling when the
 * interface grows and says nothing about methods these tests are not aiming at.
 *
 * The lead-and-dependents overload is emulated rather than stubbed, because that
 * is the part worth checking: the real one signs the lead first -- resolving the
 * room's key and so the lead's id -- and only then asks the caller what refers
 * to it. A fake that handed back an unsigned lead, or never called `dependents`,
 * would let a batch that cannot work pass.
 */
class RecordingFrostSigningRepository(
    private val authorPublicKey: HexKey,
    private val delegate: FrostSigningRepository =
        FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
) : FrostSigningRepository by delegate {

    /** Every event the session was opened over, lead first. */
    var proposed: List<Event> = emptyList()
        private set

    /** Null to make the proposal fail, which is the screens' other path. */
    var session: FrostSigningSession? = FrostSigningSession(
        id = "session-1",
        chatRoomId = "b".repeat(64),
        coordinatorPublicKey = authorPublicKey,
        userPublicKey = authorPublicKey,
        dkgSessionId = "ceremony-1",
        threshold = 2,
        participantCount = 3,
        signerId = 0,
    )

    override suspend fun canSign(chatRoomId: String): Boolean = true

    override suspend fun proposeSigningBatch(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        events: List<EventTemplate<*>>
    ): FrostSigningSession? {
        proposed = events.map { signed(it) }
        return session
    }

    override suspend fun proposeSigningBatch(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        lead: EventTemplate<*>,
        dependents: (lead: Event) -> List<EventTemplate<*>>
    ): FrostSigningSession? {
        val signedLead = signed(lead)
        proposed = listOf(signedLead) + dependents(signedLead).map { signed(it) }
        return session
    }

    /** The template as a finished session leaves it: authored by the room. */
    private fun signed(template: EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = authorPublicKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = authorPublicKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = "f".repeat(128),
    )
}
