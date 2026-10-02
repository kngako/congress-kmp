package press.mantra.compose.database.repository

import press.mantra.compose.database.GENESIS_AT
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.managers.ChronicleManager
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.managers.GroupKeyStateManager
import press.mantra.compose.managers.MarmotGroupCreation
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.ChatRoomMembership
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatMessage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.MarmotReindexReport
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import press.mantra.compose.identity.Identity
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * @param activeIdentity whose shares [canSign] answers about -- see the same
 *   parameter on `DatabaseFrostSigningRepository`, which this one's [canSign]
 *   duplicates for the group detail screen. Defaults to nobody, which can sign
 *   nothing.
 */
class DatabaseChatRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope,
    private val activeIdentity: StateFlow<Identity?> = MutableStateFlow(null),
): press.mantra.compose.repository.ChatRepository {
    val logger = Logger.withTag(TAG)

    /**
     * Who is acting. Every write below is made *as* somebody -- a message has a sender, a
     * membership has an owner -- and that used to be read off `ChatRoom.userPublicKey`,
     * which was the room pretending to know. The room is the group's now, so the question
     * is answered where it always should have been: the profile that is open.
     *
     * Throws rather than returning null because no caller below has a sensible thing to
     * do as nobody, and every one of them is reached from a screen that cannot be drawn
     * without a profile. The repository's own try/catch turns it into a logged failure.
     */
    private val actingPublicKey: HexKey
        get() = activeIdentity.value?.nostrPublicKey
            ?: throw IllegalStateException("No profile is open, so there is nobody to act as")

    /** This room as the acting profile holds it, or null if that profile is not a member. */
    private fun LocalChatRoom.actingMembership(): ChatRoomMembership? =
        membershipOf(actingPublicKey)

    override suspend fun createMarmotGroup(
        groupId: String,
        name: String,
        purpose: String,
        adminPublicKeys: Set<HexKey>,
        userPublicKey: HexKey,
        keyPair: KeyPair,
        path: List<Long>,
    ): MarmotGroupCreation.Outcome = MarmotGroupCreation.create(
        database = database,
        chatRepository = this,
        groupId = groupId,
        name = name,
        purpose = purpose,
        adminPublicKeys = adminPublicKeys,
        userPublicKey = userPublicKey,
        keyPair = keyPair,
        path = path,
    )
    override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<LocalChatRoom>> {
        return database.chatRoomDao().observeChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
        return database.chatRoomDao().getChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
    }

    override suspend fun requestGroupHistoryIfMissing(
        chatRoomId: String,
        userPublicKey: HexKey
    ): Boolean = ChronicleManager.requestIfEmpty(
        database = database,
        chatRoomId = chatRoomId,
        userPublicKey = userPublicKey,
    )

    override suspend fun groupKeyState(chatRoomId: String): GroupKeyState? =
        GroupKeyStateManager.keyStateFor(database, chatRoomId)

    override suspend fun signedGroupKeyStateEvent(chatRoomId: String): GroupSignedEvent? = try {
        GroupKeyStateManager.signedEventFor(database, chatRoomId)
    } catch (e: Throwable) {
        // Reading it walks every key state this device holds through the same
        // checks that let one be believed in the first place, and one that will
        // not derive throws rather than answering. The screen wants to draw
        // either way -- what it loses is the copyable event, not the room.
        logger.e("Error reading the signed key state event for $chatRoomId", e)
        null
    }


    override suspend fun subgroupsOf(chatRoomId: String): List<SubgroupManager.Subgroup> = try {
        SubgroupManager.subgroupsOf(database, chatRoomId)
    } catch (e: Throwable) {
        // A room that shows no subgroups is wrong and survivable; one that will
        // not open because reading them threw is neither.
        logger.e("Error reading the subgroups of $chatRoomId", e)
        emptyList()
    }

    override suspend fun adoptionOffersFor(chatRoomId: String): List<HexKey> = try {
        SubgroupManager.offersTo(database, chatRoomId)
    } catch (e: Throwable) {
        logger.e("Error reading the adoption offers for $chatRoomId", e)
        emptyList()
    }

    override suspend fun adoptingCandidatesFor(
        chatRoomId: String,
        userPublicKey: HexKey
    ): List<LocalChatRoom> = try {
        SubgroupManager.adoptingCandidatesFor(
            database = database,
            childChatRoomId = chatRoomId,
            userPublicKey = userPublicKey
        )
    } catch (e: Throwable) {
        logger.e("Error reading the groups that could adopt $chatRoomId", e)
        emptyList()
    }

    override suspend fun refuseAdoption(
        chatRoomId: String,
        adoptingChatRoomId: String,
        userPublicKey: HexKey
    ): String? = try {
        val adopting = database.chatRoomDao().findChatRoomById(adoptingChatRoomId)
            ?: return "Couldn't load that group"

        SubgroupManager.refuseAdoption(
            database = database,
            adoptingRoom = adopting,
            childChatRoomId = chatRoomId,
            userPublicKey = userPublicKey
        )
    } catch (e: Throwable) {
        logger.e("Error checking whether $adoptingChatRoomId can adopt $chatRoomId", e)
        // A refusal rather than a pass. A guard that could not run has not said
        // yes, and the manager checks again anyway.
        "Couldn't check whether that group can adopt this one"
    }

    override suspend fun proposeAdoption(
        chatRoomId: String,
        adoptingChatRoomId: String,
        userPublicKey: HexKey
    ): Boolean = try {
        val adopting = checkNotNull(database.chatRoomDao().findChatRoomById(adoptingChatRoomId))
        val child = checkNotNull(database.chatRoomDao().findChatRoomById(chatRoomId))

        SubgroupManager.proposeAdoption(
            database = database,
            adoptingRoom = adopting,
            childRoom = child,
            userPublicKey = userPublicKey
        )
        true
    } catch (e: Throwable) {
        logger.e("Error asking $adoptingChatRoomId to adopt $chatRoomId", e)
        false
    }

    override suspend fun acceptParent(
        chatRoomId: String,
        parentChatRoomId: HexKey,
        userPublicKey: HexKey
    ): Boolean = try {
        val child = checkNotNull(database.chatRoomDao().findChatRoomById(chatRoomId))

        SubgroupManager.proposeAcceptance(
            database = database,
            childRoom = child,
            userPublicKey = userPublicKey,
            parentChatRoomId = parentChatRoomId
        )
        true
    } catch (e: Throwable) {
        logger.e("Error asking $chatRoomId to accept $parentChatRoomId as a parent", e)
        false
    }

    override suspend fun parentsOf(chatRoomId: String): List<SubgroupManager.Parent> = try {
        SubgroupManager.parentGroupsOf(database, chatRoomId)
    } catch (e: Throwable) {
        logger.e("Error reading the parents of $chatRoomId", e)
        emptyList()
    }

    override suspend fun groupSignedEvent(chatRoomId: String, id: HexKey): GroupSignedEvent? = try {
        database.groupSignedEventDao().getById(id)?.takeIf { it.chatRoomId == chatRoomId }
    } catch (e: Throwable) {
        logger.e("Error reading the signed event $id of $chatRoomId", e)
        null
    }

    override suspend fun canSign(chatRoomId: String): Boolean = try {
        activeIdentity.value?.nostrPublicKey?.let { userPublicKey ->
            FrostSigningManager.canSign(database, chatRoomId, userPublicKey)
        } ?: false
    } catch (e: Throwable) {
        logger.e("Error reading whether $chatRoomId can sign", e)
        false
    }

    override suspend fun refuseSubgroup(
        parentChatRoomId: String,
        adminPublicKeys: Set<HexKey>,
        coordinatorPublicKey: HexKey,
    ): String? = try {
        val parentRoom = database.chatRoomDao().findChatRoomById(parentChatRoomId)
            ?: return "Couldn't load this group."

        SubgroupManager.refuseSubgroup(
            database = database,
            parentRoom = parentRoom,
            adminPublicKeys = adminPublicKeys,
            coordinatorPublicKey = coordinatorPublicKey,
        )
    } catch (e: Throwable) {
        // A refusal that cannot be computed has to refuse. The alternative is
        // opening a ceremony whose guards never ran.
        logger.e("Error checking whether $parentChatRoomId can take a subgroup", e)
        "Couldn't check this subgroup. Please try again."
    }
    override suspend fun getAllParticipantsWithPubKey(publicKey: HexKey): List<Participant> {
        return database.participantDao().findParticipantByPublicKey(publicKey)
    }

    override suspend fun findDirectMessageRoom(userPublicKey: HexKey, peerPublicKey: HexKey): LocalChatRoom? {
        val theTwoOfUs = setOf(userPublicKey, peerPublicKey)

        for (participant in database.participantDao().findParticipantByPublicKey(peerPublicKey)) {
            val localChatRoom = database.chatRoomDao().findChatRoomById(participant.chatRoomId) ?: continue
            val members = localChatRoom.localParticipants.map { it.participant.participantPublicKey }.toSet()
            if (members == theTwoOfUs) return localChatRoom
        }

        return null
    }
    override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
        return database.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
    }

    override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
        return database.chatMessageDao().observeChatMessagesByChatRoomId(chatRoomId)
    }


    override suspend fun getOrCreateChatRoom(
        chatRoomId: String,
        activeUserPublicKey: HexKey,
        relayHint: String?,
        defaultSubject: String?,
        description: String?,
        mlsGroupState: String?
    ): LocalChatRoom? = try {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            chatRoomId = chatRoomId,
            activeUserPublicKey = activeUserPublicKey,
            relayHint = relayHint,
            defaultSubject = defaultSubject,
            description = description,
            mlsGroupState = mlsGroupState
        )
    } catch (e: Throwable) {
        logger.e("Error getting or creating chat", e)
        return null
    }

    override suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent? {
        // TODO: Implement a UserChatMessageRelayList model
        return database.nostrEventDao().getAuthoredNostrEvents(
            kinds = arrayOf(
                ChatMessageRelayListEvent.KIND
            ),
            authors = arrayOf(
                publicKey
            ),
            since = GENESIS_AT,
            limit = 5
        ).firstOrNull()?.let { nostrEvent ->
            if (nostrEvent.kind != ChatMessageRelayListEvent.KIND) {
                logger.e("getAuthoredNostrEvents returned invalid ChatMessageRelayListEvent for ${publicKey}: $nostrEvent")
                null
            } else {
                ChatMessageRelayListEvent(
                    id = nostrEvent.id,
                    tags = nostrEvent.tags,
                    pubKey = nostrEvent.pubKey,
                    content = nostrEvent.content,
                    createdAt = nostrEvent.createdAt.epochSeconds,
                    sig = nostrEvent.sig
                )
            }
        }
    }

    override suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage? {
        return database.marmotKeyPackageDao().getMarmotKeyPackageForPublicKey(publicKey)
    }

    override suspend fun observeMarmotKeyPackageForPublicKey(publicKey: HexKey): Flow<MarmotKeyPackage?> {
        return database.marmotKeyPackageDao().observeMarmotKeyPackageForPublicKey(publicKey)
    }

    override suspend fun createNip17ChatRoom(
        userPublicKey: HexKey,
        participantPublicKeys: List<HexKey>,
        subject: String?,
        description: String?
    ): LocalChatRoom? = try {
        database.nostrNip17Dao().createNip17ChatRoom(
            userPublicKey = userPublicKey,
            participantPublicKeys = participantPublicKeys,
            subject = subject,
            description = description
        )
    } catch (e: Throwable) {
        logger.e("Error creating nip-17 chat room", e)
        null
    }

    override suspend fun createMlsDirectMessage(
        name: String?,
        description: String?,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage
    ): String {
        return database.marmotOutboundDao().createMlsDirectMessageChatRoom(
            name = name,
            description = description,
            userPublicKey = userPublicKey,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage
        )
    }

    override suspend fun inviteMember(
        localChatRoom: LocalChatRoom,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage
    ) {
        try {
            database.marmotOutboundDao().inviteMemberToChatRoom(
                localChatRoom = localChatRoom,
                userPublicKey = actingPublicKey,
                peerPublicKey = peerPublicKey,
                peerKeyPackage = peerKeyPackage
            )
        } catch (e: Throwable) {
            // Outside the transaction that just went down, which is the point: the
            // invite line `inviteMember` writes went with it, so an invite refused
            // for want of MLS state or over a mismatched credential left the room
            // with nothing at all to show. The caller still gets the throw and
            // still puts its own message on the invite screen -- this is the copy
            // that is still there tomorrow.
            logger.e("Failed to invite $peerPublicKey to ${localChatRoom.chatRoom.id}", e)
            runCatching {
                database.marmotOutboundDao().announceInviteFailed(
                    chatRoomId = localChatRoom.chatRoom.id,
                    userPublicKey = actingPublicKey,
                    peerPublicKey = peerPublicKey,
                    reason = e.message,
                )
            }.onFailure { logger.e("Failed to record the refused invite:", it) }
            throw e
        }
    }
    override suspend fun addMembers(
        localChatRoom: LocalChatRoom,
        peers: List<Pair<HexKey, MarmotKeyPackage>>
    ): List<HexKey> = database.marmotOutboundDao().addMembersToChatRoom(
        localChatRoom = localChatRoom,
        userPublicKey = actingPublicKey,
        peers = peers
    )

    override suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind,
        directMessageRecipientPublicKey: HexKey?
    ) {
        logger.i("sendChatMessage($text): $localChatRoom")
        val mlsGroup = localChatRoom.toMlsGroup(actingPublicKey)

        if (mlsGroup != null) {
            // Resolved before anything is written. A recipient outside the room cannot be
            // sent to -- the wrap would be undecryptable by every member including them,
            // while the group still saw that a private message had gone somewhere -- and
            // the room is also where the relay hint for the `p` tag comes from.
            val directMessageRecipient = directMessageRecipientPublicKey?.let { recipient ->
                require(recipient != actingPublicKey) {
                    "Cannot send a direct message to yourself: the wrap's throwaway key means it could never be read back"
                }

                localChatRoom.localParticipants
                    .map { it.participant }
                    .firstOrNull { it.participantPublicKey == recipient }
                    ?: throw IllegalArgumentException(
                        "Cannot send a direct message to $recipient: not a member of ${localChatRoom.chatRoom.id}"
                    )
            }

            // Create ChatRumor...
            val createdAt = Clock.System.now().epochSeconds
            val marmotInnerEventKind = when {
                // A direct message is a NIP-17 chat message that happens to travel inside
                // the group, so it keeps kind:14 instead of being mapped down to the
                // group's kind:9. That is also what tells the two apart on the way back in.
                directMessageRecipient != null -> ChatMessageEvent.KIND
                messageType == ChatMessageEvent.KIND -> ChatEvent.KIND // nip-17 chatMessageEvent.kind is in MLS chatMessage.kind
                else -> messageType
            }

            val marmotInnerEventTags = directMessageRecipient?.let { recipient ->
                arrayOf(
                    PTag.assemble(
                        recipient.participantPublicKey,
                        recipient.relayHint?.let { NormalizedRelayUrl(it) }
                    )
                )
            } ?: emptyArray()

            // For a direct message the queued row IS the rumor the wrap will carry, so
            // this id is the one the recipient arrives at after unwrapping -- and, on the
            // way out, the one the notary uses to find this chat message again. The wrap
            // built around it has its own id, which is why that lookup keys on the row.
            val marmotInnerEventId = EventHasher.hashId(
                pubKey = actingPublicKey,
                createdAt = createdAt,
                tags = marmotInnerEventTags,
                content = text,
                kind = marmotInnerEventKind
            )

            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = marmotInnerEventId,
                    publicKey = actingPublicKey,
                    createdAt = Instant.fromEpochSeconds(createdAt),
                    tags = marmotInnerEventTags,
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    kind = marmotInnerEventKind,
                    directMessageRecipientPublicKey = directMessageRecipient?.participantPublicKey
                )
            )

            database.chatMessageDao().upsert(
                ChatMessage(
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    senderPublicKey = actingPublicKey,
                    isUserMessage = true,
                    giftWrapPayloadId = null,
                    marmotGroupEventId = null,
                    marmotInnerEventId = marmotInnerEventId,
                    // The sender's only copy: the wrap is sealed with a key that is
                    // discarded, so nothing can reconstruct these words from the
                    // transcript later, on this device or any other.
                    messageType = if (directMessageRecipient != null) {
                        ChatMessage.TYPE_DIRECT_MESSAGE
                    } else {
                        "message"
                    },
                    directMessageRecipientPublicKey = directMessageRecipient?.participantPublicKey
                )
            )
        } else {
            // Send GiftWrapMessage
            val receiverTags = localChatRoom.localParticipants.filter { participant ->
                participant.participant.participantPublicKey != actingPublicKey // TODO: Allow this where it's notes to self...
            }.map { localParticipant ->
                PTag.assemble(
                    localParticipant.participant.participantPublicKey,
                    localParticipant.participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            }

            val createdAt = Clock.System.now().epochSeconds
            val giftWrapPayloadId = EventHasher.hashId(
                pubKey = actingPublicKey,
                createdAt = createdAt,
                tags = receiverTags.toTypedArray(),
                content = text,
                kind = messageType
            )

            database.giftWrapPayloadDao().upsert(
                GiftWrapPayload(
                    id = giftWrapPayloadId,
                    kind = messageType,
                    tags = receiverTags.toTypedArray(),
                    createdAt = Instant.fromEpochSeconds(createdAt),
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    publicKey = actingPublicKey
                )
            )

            database.chatMessageDao().upsert(
                ChatMessage(
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    senderPublicKey = actingPublicKey,
                    isUserMessage = true,
                    giftWrapPayloadId = giftWrapPayloadId,
                    marmotGroupEventId = null,
                    marmotInnerEventId = null
                )
            )
        }
    }

    override fun updateChatRoomSubject(
        publicKey: String,
        subject: String
    ): press.mantra.compose.database.model.ChatRoom? {
        // TODO: Update chatRoom with subject...
        return null
    }

    override suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<List<GiftWrapPayload>> {
        return database.giftWrapPayloadDao().observeUnsealedGiftWrapPayloads(publicKey)
    }

    override suspend fun sealGiftWrapPayload(
        giftWrapPayload: GiftWrapPayload,
        nostrSignerSync: NostrSignerSync
    ) {
        // An MLS room must never produce a NIP-17 gift wrap of its *contents*. Its messages
        // already travel inside kind:445, and its direct messages are real, correctly signed
        // NIP-59 wraps -- indistinguishable from something this path would be right to
        // publish. The only thing keeping one off a relay is that it never becomes a
        // GiftWrapPayload, so refuse here too rather than trusting every future caller to
        // know that. See docs/marmot-direct-messages.md.
        //
        // The Welcome is the one thing an MLS room is *supposed* to publish this way, and
        // the reason it reaches this path at all. MIP-02 addresses kind:444 to a joiner who
        // holds no group state yet: they cannot read a kind:445, so a relay-borne gift wrap
        // is the only way to reach them, and `MarmotOutboundDao.deliveryWelcome` queues one
        // here deliberately. Every Welcome carries its room's nostrGroupId, so a refusal
        // keyed on `mlsGroupState` alone catches all of them and no invite is ever
        // delivered. The refusal below no longer strands the rest of the queue behind it
        // -- the notary is handed the whole backlog and refuses one payload at a time --
        // but a refused Welcome is still an invite that never arrives, so the exception
        // for it stays.
        if (giftWrapPayload.kind != WelcomeEvent.KIND) {
            val chatRoom = database.chatRoomDao().findChatRoomById(giftWrapPayload.chatRoomId)
            if (chatRoom?.mlsGroupStateOf(actingPublicKey) != null) {
                logger.e(
                    "Refusing to seal payload ${giftWrapPayload.id}: ${giftWrapPayload.chatRoomId} is an MLS room, " +
                        "and sealing would broadcast it to relays"
                )
                return
            }
        }

        database.participantDao().findParticipantsByChatRoomId(giftWrapPayload.chatRoomId).forEach { participant ->
            if (giftWrapPayload.kind == WelcomeEvent.KIND) {
                logger.i("Only giftWrap welcomeEvent payload (${giftWrapPayload.id}) to the participant who published the related keyPackage")
                val welcomeEvent = WelcomeEvent(
                    id = giftWrapPayload.id,
                    pubKey = nostrSignerSync.pubKey,
                    tags = giftWrapPayload.tags,
                    content = giftWrapPayload.content,
                    createdAt = giftWrapPayload.createdAt.epochSeconds,
                    sig = ""
                )

                val shouldBeGiftWrapped: Boolean = welcomeEvent.keyPackageEventId()?.let { keyPackageEventId ->
                    val marmotKeyPackage = database.marmotKeyPackageDao().getMarmotKeyPackageById(
                        keyPackageEventId
                    )

                    logger.i("MarmoteKeyPackage published by ${marmotKeyPackage?.publicKey}")

                    marmotKeyPackage?.publicKey == participant.participantPublicKey
                } ?: false

                if (!shouldBeGiftWrapped) {
                    logger.w("Participant ${participant.participantPublicKey} didn't publish key package for this welcome event")

                    return@forEach
                }
            }

            val wrapperKeyPair = KeyPair()
            val wrapperSigner = NostrSignerSync(
                keyPair = wrapperKeyPair
            )

            // TODO: Don't sign the payload... use event template instead...
            val giftWrapPayloadRumor = RumorAssembler.assembleRumor(
                pubKey = nostrSignerSync.pubKey,
                ev = EventTemplate(
                    createdAt = giftWrapPayload.createdAt.epochSeconds,
                    kind = giftWrapPayload.kind,
                    tags = giftWrapPayload.tags,
                    content = giftWrapPayload.content
                )
            )
            logger.d("GiftWrap Payload Rumor: ${giftWrapPayloadRumor.toJson()}")

            logger.d("Seal to $participant")
            val payload = giftWrapPayloadRumor.toJson()

            val sealedRumorEvent = nostrSignerSync.signNormal<SealedRumorEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = SealedRumorEvent.KIND,
                tags = emptyArray(),
                content = nostrSignerSync.nip44Encrypt(
                    plaintext = payload,
                    toPublicKey = participant.participantPublicKey
                )
            )
            logger.d("SealedRumorEvent: ${sealedRumorEvent.toJson()}")

            val giftWrapEvent = wrapperSigner.signNormal<GiftWrapEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = GiftWrapEvent.KIND,
                tags = arrayOf(
                    PTag.assemble(participant.participantPublicKey, participant.relayHint?.let { NormalizedRelayUrl(it) })
                ),
                content = wrapperSigner.nip44Encrypt(
                    plaintext = sealedRumorEvent.toJson(),
                    toPublicKey = participant.participantPublicKey
                )
            )
            logger.d("giftWrapEvent: ${giftWrapEvent.toJson()}")

            database.nostrNip17Dao().persistAndBroadcastGiftWrap(
                giftWrapPayload = giftWrapPayload,
                sealedRumorEvent = sealedRumorEvent,
                giftWrapEvent = giftWrapEvent,
                receiverPublicKey = PTag(
                    pubKey = participant.participantPublicKey,
                    relayHint = participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            )
        }
    }

    override suspend fun leaveChatRoom(
        localChatRoom: LocalChatRoom,
        onCompletion: () -> Unit
    ) {
        // The acting profile's membership, not the room: the group goes on existing, and
        // the other profile on this device has not left it.
        localChatRoom.actingMembership()?.let { membership ->
            database.chatRoomDao().upsert(membership.copy(leftGroupAt = Clock.System.now()))
        }

        // TODO: Process this in the notary...
        sendChatMessage(
            text = "Left group",
            localChatRoom = localChatRoom,
            // TODO: Add Information/LeaveChatGroup Message Type...
        )

        onCompletion.invoke()
    }

    /**
     * Archives the room for the acting profile.
     *
     * One person tidying their chat list is not the group being deleted, and it is not the
     * other profile's list changing either -- so this marks the membership rather than the
     * room. A room with no membership left archived is still the group's, and the
     * twenty-two tables hanging off it are untouched.
     */
    override suspend fun softDeleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    ) {
        database.chatRoomDao()
            .findMembership(chatRoom.id, actingPublicKey)
            ?.let { database.chatRoomDao().upsert(it.copy(deletedAt = Clock.System.now())) }

        onCompletion.invoke()
    }

    override suspend fun deleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    ) {
        database.chatRoomDao().delete(chatRoom)

        onCompletion.invoke()
    }

    /**
     * Only the public key is needed: reading a group event is done with the MLS
     * group's own secrets, held in the room's stored state, and the identity is
     * used for nothing but deciding whether this device is a member and whether a
     * line is its own. So a read-only [KeyPair] is enough, and the nostr private
     * key stays where it is.
     */
    override suspend fun reindexMarmotGroupEvents(
        chatRoomId: String,
        userPublicKey: HexKey
    ): MarmotReindexReport =
        database.nostrDao().reindexMarmotGroupEvents(
            chatRoomId = chatRoomId,
            activeKeyPair = KeyPair(pubKey = userPublicKey.hexToByteArray()),
        )

    companion object {
        const val TAG = "DatabaseChatRepository"
    }
}