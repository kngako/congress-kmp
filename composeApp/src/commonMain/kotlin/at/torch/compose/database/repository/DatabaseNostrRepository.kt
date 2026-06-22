package at.torch.compose.database.repository

import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.UnsignedNostrEvent
import at.torch.compose.nostr.Relays
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRotationManager.Companion.KEY_PACKAGE_LIFETIME_SECONDS
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageUtils
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.crypto.X25519
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Capabilities
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNode
import com.vitorpamplona.quartz.marmot.mls.tree.LeafNodeSource
import com.vitorpamplona.quartz.marmot.mls.tree.Lifetime
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.hashtags
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.references
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.RelaySet
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.content.findHashtags
import com.vitorpamplona.quartz.nip10Notes.content.findNostrUris
import com.vitorpamplona.quartz.nip10Notes.content.findURLs
import com.vitorpamplona.quartz.nip10Notes.tags.markedETags
import com.vitorpamplona.quartz.nip10Notes.tags.notify
import com.vitorpamplona.quartz.nip10Notes.tags.prepareETagsAsReplyTo
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.quotes
import com.vitorpamplona.quartz.nip21UriScheme.toNostrUri
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

class DatabaseNostrRepository(
    private val database: at.torch.compose.database.TorchDatabase,
    private val scope: CoroutineScope
): at.torch.compose.repository.NostrRepository, at.torch.compose.repository.RelayRepository {
    companion object {
        const val TAG = "DatabaseNostrRepository"

        private val storeNostrEventMutex = Mutex()
    }

    val logger = Logger.withTag(TAG)

    override suspend fun observeProfile(publicKey: HexKey): Flow<at.torch.compose.database.model.intermdiate.LocalAccount?> {
        return database.unsignedNostrEventDao().observeProfile(publicKey)
    }

    override suspend fun observeRelation(
        publicKey: HexKey,
        activePublicKey: HexKey
    ): Flow<List<at.torch.compose.database.model.Connection>> {
        return database.connectionDao().observeRelation(
            alicePublicKey = publicKey,
            bobPublicKey = activePublicKey
        )
    }

    override suspend fun deleteConnection(connection: at.torch.compose.database.model.Connection) {
        database.connectionDao().delete(connection)
    }

    override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?> {
        return database.unsignedNostrEventDao().observeUnsignedNostrEvents(publicKey)
    }

    override suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest>> {
        return database.broadcastNostrEventRequestDao().observeBroadcastNostrEventRequestsByStatus("pending")
    }

    override suspend fun observePendingSynchronizeNostrEventRequests(): Flow<at.torch.compose.database.model.SynchronizeNostrEventRequest?> {
        return database.synchronizeNostrEventRequestDao().observeSynchronizeNostrEventRequestsByStatus("pending")
    }

    override suspend fun observePendingNegentropySynchronizeRequests(): Flow<at.torch.compose.database.model.NegentropySynchronizeRequest?> {
        return database.negentropySynchronizeRequestDao().observeNegentropySynchronizeRequestsByStatus("pending")
    }

    override suspend fun createNewProfile(
        publicKey: HexKey,
        name: String?,
        biography: String?
    ): KeyPackageBundle {
        val profileEventTemplate = MetadataEvent.createNew(
            name = name,
            about = biography,
        )

        val unsignedNostrEvents = mutableListOf(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = profileEventTemplate.kind,
                createdAt = Instant.fromEpochSeconds(profileEventTemplate.createdAt),
                tags = profileEventTemplate.tags,
                content = profileEventTemplate.content,
                signedAt = null
            )
        )

        val followUsers = listOf(ContactTag(publicKey, null, null))
        val contactListTagList = listOf(AltTag.assemble(ContactListEvent.ALT)) +
                followUsers.map { it.toTagArray() }

        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = ContactListEvent.KIND,
                tags = contactListTagList.toTypedArray(),
                content = RelaySet.assemble(
                    emptyMap()
                )
            )
        )

        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = ChatMessageRelayListEvent.KIND,
                tags = ChatMessageRelayListEvent.createTagArray(
                    Relays.DefaultDMRelayList
                ),
                content = ""
            )
        )

        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = KeyPackageRelayListEvent.KIND,
                tags = Relays.DefaultDMRelayList.map { arrayOf("relay", it.url) }
                    .plusElement(AltTag.assemble(KeyPackageRelayListEvent.ALT_DESCRIPTION))
                    .toTypedArray(),
                content = ""
            )
        )



        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = SearchRelayListEvent.KIND,
                tags = Relays.DefaultSearchRelayList.map {
                    RelayTag.assemble(
                        it
                    )
                }
                    .plus(
                        SearchRelayListEvent.ALT_TAG
                    ).toTypedArray(),
                privateTags = emptyArray(),
                content = ""
            )
        )

        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = RelayFeedsListEvent.KIND,
                tags = arrayOf(
                    AltTag.assemble(RelayFeedsListEvent.ALT)
                ),
                privateTags = Relays.DefaultGlobalRelays.map {
                    RelayTag.assemble(
                        it
                    )
                }.toTypedArray(),
                content = ""
            )
        )

        // KeyPackage...
        KeyPackageUtils.PRIMARY_SLOT
        val dTag = KeyPackageUtils.generateRandomDTag() // TODO: Persist this...
        val identity = publicKey.hexToByteArray()
        val bundle = generateKeyPackage(identity, dTag)

        val keyPackageBytes = bundle.keyPackage.toTlsBytes()
        val keyPackageBase64 = Base64.encode(keyPackageBytes)
        val keyPackageRef = bundle.keyPackage.reference().toHexKey()

        val keyPackageTemplate = KeyPackageEvent.build(
            keyPackageBase64 = keyPackageBase64,
            dTagSlot = dTag,
            keyPackageRef = keyPackageRef,
            relays = Relays.DefaultDMRelayList,
        )

        unsignedNostrEvents.add(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = keyPackageTemplate.kind,
                tags = keyPackageTemplate.tags,
                content = keyPackageTemplate.content
            )
        )

        saveUnsignedNostrEvents(unsignedNostrEvents)

        return bundle
    }

    private fun generateKeyPackage(
        identity: ByteArray,
        dTagSlot: String = KeyPackageUtils.PRIMARY_SLOT,
    ): KeyPackageBundle {
        val initKp = X25519.generateKeyPair()
        val encKp = X25519.generateKeyPair()
        val sigKp = Ed25519.generateKeyPair()

        val leafNode =
            buildKeyPackageLeafNode(
                encryptionKey = encKp.publicKey,
                signatureKey = sigKp.publicKey,
                identity = identity,
                signingKey = sigKp.privateKey,
            )

        val unsigned =
            MlsKeyPackage(
                initKey = initKp.publicKey,
                leafNode = leafNode,
                // LastResort (0x000A) marks this as a last-resort KeyPackage per MLS Extensions draft.
                // MDK always marks KeyPackages as last-resort via .mark_as_last_resort().
                extensions = listOf(Extension(extensionType = 0x000A, extensionData = ByteArray(0))),
                signature = ByteArray(0),
            )
        val keyPackage =
            unsigned.copy(
                signature =
                    MlsCryptoProvider.signWithLabel(
                        sigKp.privateKey,
                        "KeyPackageTBS",
                        unsigned.encodeTbs(),
                    ),
            )

        val bundle = KeyPackageBundle(keyPackage, initKp.privateKey, encKp.privateKey, sigKp.privateKey)
        // TODO: Persist this...

        return bundle
    }


    private fun buildKeyPackageLeafNode(
        encryptionKey: ByteArray,
        signatureKey: ByteArray,
        identity: ByteArray,
        signingKey: ByteArray,
    ): LeafNode {
        val now = TimeUtils.now()
        val unsigned =
            LeafNode(
                encryptionKey = encryptionKey,
                signatureKey = signatureKey,
                credential = Credential.Basic(identity),
                capabilities =
                    Capabilities(
                        extensions =
                            listOf(
                                0x000A, // LastResort (required by OpenMLS validation)
                                0xF2EE, // NostrGroupData (required by group's RequiredCapabilities)
                            ),
                        proposals =
                            listOf(
                                0x000A, // SelfRemove (required by group's RequiredCapabilities)
                            ),
                    ),
                leafNodeSource = LeafNodeSource.KEY_PACKAGE,
                lifetime = Lifetime(notBefore = now, notAfter = now + KEY_PACKAGE_LIFETIME_SECONDS),
                extensions = emptyList(),
                signature = ByteArray(0),
            )

        val tbs = unsigned.encodeTbs(groupId = null, leafIndex = null)
        val signature = MlsCryptoProvider.signWithLabel(signingKey, "LeafNodeTBS", tbs)
        return unsigned.copy(signature = signature)
    }

    override suspend fun signInToProfile(
        publicKey: HexKey
    ) {
        val profileEventTemplate = MetadataEvent.createNew()

        saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = profileEventTemplate.kind,
                createdAt = Instant.fromEpochSeconds(profileEventTemplate.createdAt),
                tags = profileEventTemplate.tags,
                content = profileEventTemplate.content,
                signedAt = _root_ide_package_.at.torch.compose.database.GENESIS_AT // This will keep us from trying to sign this event... but we should automatically sync the npub profile
            )
        )
    }
    override suspend fun createNewTextNote(
        publicKey: HexKey,
        mentionedPublicKeys: List<HexKey>,
        textInput: String,
        inReplyToNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
        quotedNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
        onError: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val quotedNostrURI = quotedNostrEvent?.let {
            // TODO: Convert qoutedNostrEvent to nostrURI
            val quotedTextNoteEvent = it.nostrEvent.toTextNoteEvent()

            val relayHint = null // TODO: get relyhint from where ever the nostrEvent was synced from...

            "\n${quotedTextNoteEvent.toNostrUri(relayHint)}"
        } ?: ""
        val text: String = textInput + quotedNostrURI

        // TODO: Handle mentioned profile tags...
        val textNote = TextNoteEvent.build(
            note = text,
        ) {
            inReplyToNostrEvent?.let {
                val replyToTextNoteEvent = it.nostrEvent.toTextNoteEvent()

                val replyingTo = EventHintBundle(
                    replyToTextNoteEvent
                )

                val tags = prepareETagsAsReplyTo(replyingTo)

                markedETags(
                    tags
                )

                it.profile?.let { profile ->
                    val publicKeys = mutableSetOf<HexKey>()
                    publicKeys.addAll(
                        mentionedPublicKeys.toSet()
                    )

                    publicKeys.add(profile.publicKey)

                    val ptags = publicKeys.map { publicKey ->
                        PTag(
                            publicKey,
                            // TODO: Add relayHints...
                        )
                    }

                    notify(ptags)
                }
            }

            if (inReplyToNostrEvent == null || inReplyToNostrEvent.profile == null) {
                val pTags = mentionedPublicKeys.toSet().map {
                    PTag(
                        pubKey = it
                    )
                }

                notify(pTags)
            }

            quotedNostrEvent?.let {
                it.profile?.let { profile ->
                    val ptags = listOf(
                        PTag(
                            profile.publicKey,
                            // TODO: Add relayHints...
                        ),
                        // Other profiles tagged in the event...
                    )

                    add(
                        QEventTag.assemble(
                            eventId = quotedNostrEvent.nostrEvent.id,
                            relay = null, // TODO: Add relayHints...
                            author = profile.publicKey
                        )
                    )
                    notify(ptags)

                    // TODO: ZapForwarding support...
                }
            }

            hashtags(findHashtags(text))
            references(findURLs(text))
            quotes(findNostrUris(text))
        }


        val unsignedNostrEvent = saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = textNote.kind,
                createdAt = Instant.fromEpochSeconds(textNote.createdAt),
                tags = textNote.tags,
                content = textNote.content
            )
        )

        if (unsignedNostrEvent == null) {
           onError.invoke()
        } else {
            if (inReplyToNostrEvent != null) {
                // TODO: Schedule inReplyToNostrEvent broadcast
            }

            if (quotedNostrEvent != null) {
                // TODO: Schedule quotedNostrEvent broadcast
            }

            onSuccess.invoke()
        }
    }

    override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? = try {
        logger.d("Save unsigned: ${unsignedNostrEvent.kind}")
        // TODO: We can actually check the unsignedNostrEvent.pubKey for support... if it's writeable
        if (unsignedNostrEvent.kind == MetadataEvent.KIND){
            delay(2_100) // Just to make it look like we are doing serious work...
        }

        unsignedNostrEvent.copy(
            id = database.unsignedNostrEventDao().upsert(unsignedNostrEvent)
        )
    } catch (e: Throwable) {
        logger.e("Failed to saveUnsignedNostrEvent", e)
        null
    }

    override suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<UnsignedNostrEvent>) {
        logger.d("saveUnsignedNostrEvents: $unsignedNostrEvents")
        delay(2_100)

        database.unsignedNostrEventDao().insert(
            unsignedNostrEvents
        )
    }

    override suspend fun wipeDatabase() {
        database.wipeData()
    }

    override suspend fun publishNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        relayURLs: List<String>,
        activeKeyPair: KeyPair
    ) {
        database.nostrDao().publishNostrEvent(
            unsignedNostrEvent,
            nostrEvent,
            relayURLs,
            activeKeyPair = activeKeyPair
        )
    }

    override suspend fun broadcastProcessed(
        broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest,
        status: String
    ) {
        database.broadcastNostrEventRequestDao().upsert(
            broadcastNostrEventRequest.copy(
                status = status
            )
        )

        if (status == "published") {
            val broadcastNostrEventReceiptId = database.broadcastNostrEventReceiptDao().upsert(
                _root_ide_package_.at.torch.compose.database.model.BroadcastNostrEventReceipt(
                    nostrEventId = broadcastNostrEventRequest.nostrEventId,
                    unsignedNostrEventId = broadcastNostrEventRequest.unsignedNostrEventId,
                    isAccepted = true,
                    relayURL = broadcastNostrEventRequest.relayURL
                )
            )

            database.chatMessageBroadcastNostrEventRequestRelationDao().getChatMessageBroadcastNostrEventRequestRelationByBroadcastRequest(
                broadcastNostrEventRequestId = broadcastNostrEventRequest.id
            )?.let { chatMessageBroadcastNostrEventRequestRelation ->
                // This is actually a chatMessage related broadcast event...
                database.chatMessageBroadcastNostrEventReceiptRelationDao().upsert(
                    _root_ide_package_.at.torch.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation(
                        chatMessageId = chatMessageBroadcastNostrEventRequestRelation.chatMessageId,
                        broadcastNostrEventReceiptId = broadcastNostrEventReceiptId
                    )
                )
            }
        }
    }

    override suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest) {
        database.synchronizeNostrEventRequestDao().upsert(
            synchronizeNostrEventRequest.copy(
                status = "sent",
                updatedAt = Clock.System.now()
            )
        )
    }

    override suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest) {
        database.negentropySynchronizeRequestDao().upsert(
            negentropySynchronizeRequest.copy(
                status = "sent",
                updatedAt = Clock.System.now()
            )
        )
    }

    override suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: at.torch.compose.database.model.BroadcastNostrEventReceipt) {
        database.broadcastNostrEventReceiptDao().upsert(
            broadcastNostrEventReceipt
        )
    }

    override suspend fun saveNostrEvent(
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest,
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair,
    ) {
        storeNostrEventMutex.withLock {
            logger.d("saveNostrEvent: $nostrEvent")
            try {
                database.nostrDao().storeNostrEvent(
                    nostrEvent.copy(
                        broadcastedAt = nostrEvent.createdAt,
                    ),
                    synchronizationRelayURLs = synchronizationRelayURLs,
                    relayURL = synchronizeNostrEventRequest.relayURL,
                    level = synchronizeNostrEventRequest.level,
                    activeKeyPair = activeKeyPair
                )

                database.synchronizeNostrEventRequestDao().upsert(
                    synchronizeNostrEventRequest.copy(
                        nostrEventId = nostrEvent.id,
                        status = "processed"
                    )
                )
            } catch (e: Throwable) {
                logger.e("Failed to save nostrEvent ${nostrEvent.id}", e)
            }

        }
    }

    override suspend fun saveNostrEvent(
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest,
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair
    ) {
        logger.d("saveNostrEvent: $nostrEvent")

        try {
            database.nostrDao().storeNostrEvent(
                nostrEvent,
                relayURL = negentropySynchronizeRequest.relayURL,
                synchronizationRelayURLs = synchronizationRelayURLs,
                level = negentropySynchronizeRequest.level,
                activeKeyPair = activeKeyPair
            )

            database.negentropySynchronizeRequestDao().upsert(
                negentropySynchronizeRequest.copy(
                    status = "processed"
                )
            )
        } catch (e: Throwable) {
            logger.e("Failed to save nostr event $nostrEvent.id", e)
        }



    }

    override suspend fun queueSynchronizeNostrEvent(
        synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>,
    ) {
        database.synchronizeNostrEventRequestDao().insert(
            synchronizeNostrEventRequests
        )
    }

    override suspend fun queueNegentropySynchronizeRequest(negentropySynchronizeRequests: List<at.torch.compose.database.model.NegentropySynchronizeRequest>) {
        database.negentropySynchronizeRequestDao().insert(
            negentropySynchronizeRequests
        )
    }

    override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> {
        return database.synchronizeNostrEventRequestDao().observeSynchronizeNostrEventRequestByPurposeAndStatusCount(
            "feed",
            listOf("pending", "processing")
        )
    }

    override suspend fun getNostrEvent(publicKey: HexKey, kind: Kind): at.torch.compose.database.model.NostrEvent? {
        return database.nostrEventDao().getNostrEventByPublicKeyAndKind(
            publicKey = publicKey,
            kind = kind
        )
    }

    override suspend fun observeNostrFeed(): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>> {
        return database.nostrEventDao().observeNostrEvents(
            kinds = arrayOf(
                TextNoteEvent.KIND,
                RepostEvent.KIND,
                ReactionEvent.KIND,
                LnZapEvent.KIND
            )
        )
    }

    override suspend fun observeNostrFeed(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>> {
        return when {
            synchronizationFilter.kinds != null && synchronizationFilter.search != null -> {
                logger.d("observeFilteredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeFilteredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    search = synchronizationFilter.search
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.ids != null -> {
                logger.d("observeFilteredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeFilteredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    ids = synchronizationFilter.ids
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.authors != null -> {
                logger.d("observeAuthoredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeAuthoredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    authors = synchronizationFilter.authors
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.since != null &&  synchronizationFilter.tags?.contains("e") == true && synchronizationFilter.tags["e"]?.first()?.isNotEmpty() == true -> {
                logger.d("observePublicKeyMentionedNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeNostrEventReplies(
                    kinds = synchronizationFilter.kinds,
                    eventId = synchronizationFilter.tags["e"]?.first()!!,
                    since = synchronizationFilter.since
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.tags?.contains("p") == true && synchronizationFilter.tags["p"]?.first()?.isNotEmpty() == true -> {
                logger.d("observePublicKeyMentionedNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observePublicKeyMentionedNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    publicKey = synchronizationFilter.tags["p"]?.first()!!
                )
            }
            else -> {
                logger.d("observeNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeNostrEvents(
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                    )
                )
            }
        }
    }

    override suspend fun getNostrFeedIds(
        synchronizationFilters: Array<at.torch.compose.database.model.types.SynchronizationFilter>,
        applyLimits: Boolean
    ): List<at.torch.compose.database.model.NostrEvent> {
        if (synchronizationFilters.size == 1) {
            val synchronizationFilter = synchronizationFilters.first()

            return when {
                synchronizationFilter.kinds != null && synchronizationFilter.search != null -> {
                    logger.d("getFilteredNostrEvents($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getFilteredNostrEvents(
                        kinds = synchronizationFilter.kinds,
                        search = synchronizationFilter.search,
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                synchronizationFilter.kinds != null && synchronizationFilter.ids != null -> {
                    logger.d("getFilteredNostrEvents($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getFilteredNostrEvents(
                        kinds = synchronizationFilter.kinds,
                        ids = synchronizationFilter.ids,
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                synchronizationFilter.ids != null -> {
                    logger.d("getNostrEventsByIds($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getNostrEventsByIds(
                        ids = synchronizationFilter.ids,
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                synchronizationFilter.kinds != null && synchronizationFilter.authors != null -> {
                    logger.d("getAuthoredNostrEvents($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getAuthoredNostrEvents(
                        kinds = synchronizationFilter.kinds,
                        authors = synchronizationFilter.authors,
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                synchronizationFilter.kinds != null && synchronizationFilter.since != null &&  synchronizationFilter.tags?.contains("e") == true && synchronizationFilter.tags["e"]?.first()?.isNotEmpty() == true -> {
                    logger.d("getNostrEventReplies($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getNostrEventReplies(
                        kinds = synchronizationFilter.kinds,
                        eventId = synchronizationFilter.tags["e"]?.first()!!,
                        since = synchronizationFilter.since,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                synchronizationFilter.kinds != null && synchronizationFilter.tags?.contains("p") == true && synchronizationFilter.tags["p"]?.first()?.isNotEmpty() == true -> {
                    logger.d("getPublicKeyMentionedNostrEvents($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getPublicKeyMentionedNostrEvents(
                        kinds = synchronizationFilter.kinds,
                        publicKey = synchronizationFilter.tags["p"]?.first()!!,
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
                else -> {
                    logger.d("getNostrEvents($applyLimits): $synchronizationFilter")
                    database.nostrEventDao().getNostrEvents(
                        kinds = arrayOf(
                            TextNoteEvent.KIND,
                        ),
                        since = synchronizationFilter.since ?: _root_ide_package_.at.torch.compose.database.GENESIS_AT,
                        limit = if (applyLimits) {
                            synchronizationFilter.limit ?: 50
                        } else {
                            -1
                        }
                    )
                }
            }
        } else {
            logger.w("Multi synchronizationFilters not yet supported")
            return emptyList()
        }
    }

    override suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<at.torch.compose.database.model.intermdiate.LocalNostrEvent?> {
        return database.nostrEventDao().observeNostrEventById(nostrEventId)
    }

    override suspend fun searchableProfiles(): List<at.torch.compose.database.model.Profile> {
        return database.profileDao().getAllProfiles()
    }

    override suspend fun filterProfiles(searchTerm: String): List<at.torch.compose.database.model.intermdiate.LocalProfile> {
        return database.profileDao().filterProfiles(searchTerm = searchTerm)
    }


    override suspend fun observeProfileWithFollowers(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers?> {
        return database.profileDao().observeProfileWithFollowersByPublicKey(publicKey)
    }

    override suspend fun observeProfileWithFollowing(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing?> {
        return database.profileDao().observeProfileWithFollowingByPublicKey(publicKey)
    }

    override suspend fun getRecentSearches(): List<at.torch.compose.database.model.RecentSearch> {
        return database.recentSearchDao().getAllRecentSearches()
    }

    override suspend fun removeRecentSearch(recentSearch: at.torch.compose.database.model.RecentSearch) {
        database.recentSearchDao().deleteRecentSearch(recentSearch)
    }

    override suspend fun saveSearchQuery(
        query: String,
        synchronizationFilterArray: at.torch.compose.database.model.typealiases.SynchronizationFilterArray?
    ) {
        database.recentSearchDao().upsert(
            _root_ide_package_.at.torch.compose.database.model.RecentSearch(
                query = query,
                synchronizationFilters = synchronizationFilterArray
            )
        )
    }

    override suspend fun observePublicKeyRelays(publicKey: String): Flow<List<at.torch.compose.database.model.Relay>> {
        return database.relayDao().observePublicKeyRelays(publicKey)
    }

    override suspend fun scheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<at.torch.compose.database.model.BroadcastNostrEventRequest>) {
        database.broadcastNostrEventRequestDao().insert(
            broadcastNostrEventRequests
        )
    }
}
