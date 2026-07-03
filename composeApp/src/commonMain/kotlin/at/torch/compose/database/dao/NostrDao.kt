package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Transaction
import at.torch.compose.database.GENESIS_AT
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.ChatMessage
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.Connection
import at.torch.compose.database.model.Mention
import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.NostrEventRelay
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.Post
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.types.SynchronizationFilter
import at.torch.compose.exceptions.GiftWrapImpersonationException
import at.torch.compose.exceptions.GiftWrapSealDecryptionException
import at.torch.compose.exceptions.GiftWrapUnsealException
import at.torch.compose.exceptions.MarmotMissingChatGroupException
import at.torch.compose.exceptions.MarmotMissingKeyPackageBundleException
import at.torch.compose.exceptions.MarmotMissingNostrGroupDataExtension
import at.torch.compose.exceptions.MarmotNotMemberOfChatGroupException
import at.torch.compose.exceptions.MarmotUnsupportedWireFormatException
import at.torch.compose.exceptions.MarmotWelcomeEventMissingKeyPackageEventIdException
import at.torch.compose.extensions.exporterSecret
import at.torch.compose.extensions.toHex
import at.torch.compose.managers.MarmotInboundManager
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.events.taggedEvents
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
abstract class NostrDao(
    private val database: at.torch.compose.database.TorchDatabase,
) {
    val logger = Logger.withTag("NostrDao")

    val profileEventKinds = arrayOf(
        MetadataEvent.KIND,
//        ContactListEvent.KIND, // TODO: Might want to get contacts for the social graph...
        ChatMessageRelayListEvent.KIND,
        KeyPackageEvent.KIND,
        KeyPackageRelayListEvent.KIND,
//        AdvertisedRelayListEvent.KIND,
//        SearchRelayListEvent.KIND,
//        IndexerRelayListEvent.KIND,
//        ChannelListEvent.KIND,
//        RelayFeedsListEvent.KIND
    )

    @Transaction
    open suspend fun publishNostrEvent(
        unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent,
        nostrEvent: NostrEvent,
        relayURLs: List<String>,
        activeKeyPair: KeyPair
    ) {
        logger.i("Publish Nostr Event: $nostrEvent ($relayURLs)")
        // Update unsignedEvent with signedTime time...
        database.unsignedNostrEventDao().upsert(
            unsignedNostrEvent.copy(
                signedAt = Clock.System.now()
            )
        )

        database.nostrEventDao().upsert(nostrEvent)

        indexNostrEvent(
            nostrEvent = nostrEvent,
            relayURL = relayURLs.first(),
            synchronizationRelayURLs = relayURLs,
            level = 0,
            activeKeyPair = activeKeyPair
        )

        relayURLs.forEach { relayURL ->
            database.broadcastNostrEventRequestDao().upsert(
                BroadcastNostrEventRequest(
                    nostrEventId = nostrEvent.id,
                    unsignedNostrEventId = nostrEvent.unsignedNostrEventId,
                    relayURL = relayURL
                )
            )
        }
    }

    @Transaction
    open suspend fun storeNostrEvent(
        nostrEvent: NostrEvent,
        relayURL: String,
        synchronizationRelayURLs: List<String>,
        level: Int,
        activeKeyPair: KeyPair
    ) {
        val storedNostrEvent = database.nostrEventDao().getNostrEventById(nostrEvent.id)

        if (storedNostrEvent == null) {
            // Add new nostr event
            logger.i("Store Nostr Event: $nostrEvent")
            database.nostrEventDao().insert(nostrEvent)


        } else if (nostrEvent.createdAt > storedNostrEvent.createdAt) {
            // Update nostr event`
            logger.i("Update Nostr Event: $nostrEvent")
            database.nostrEventDao().upsert(nostrEvent)
        } else {
            // Nothing else needs to be done
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)
            if (profile?.createdAt == GENESIS_AT && nostrEvent.kind == MetadataEvent.KIND) {
                logger.i("This is a placeholder profile might need to get synced...: ${profile.publicKey}")
                // Setup the sync here...
            } else {
                // TODO: Check if event got indexed...
                return
            }
        }

        database.nostrEventRelayDao().upsert(
            NostrEventRelay(
                relayURL = relayURL,
                nostrEventId = nostrEvent.id
            )
        )

        // Index nostrEvent
        logger.i("Index Nostr Event: ${nostrEvent.id}")
        indexNostrEvent(
            nostrEvent = nostrEvent,
            relayURL = relayURL,
            synchronizationRelayURLs = synchronizationRelayURLs,
            level = level,
            activeKeyPair = activeKeyPair
        )
    }

    private suspend fun indexNostrEvent(
        nostrEvent: NostrEvent,
        relayURL: String,
        synchronizationRelayURLs: List<String>,
        level: Int,
        activeKeyPair: KeyPair
    ) {
        val profilePublicKeysToSync = mutableMapOf<String, MutableSet<String>>()
        val eventIdsToSync = mutableMapOf<String, MutableSet<String>>()

        if (nostrEvent.unsignedNostrEventId == null && nostrEvent.kind != GiftWrapEvent.KIND) {
            // Find or create profile with the pubKey... if not found submit a sync request...
            val profile = database.profileDao().getProfileByPublicKey(nostrEvent.pubKey)

            if (profile == null) {
                val placeHolderProfile = Profile(
                    displayName = "LOADING...",
                    publicKey = nostrEvent.pubKey,
                    createdAt = GENESIS_AT,
                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                )
                database.profileDao().insertPlaceholderProfile(placeHolderProfile)
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(nostrEvent.pubKey)
            } else if (profile.createdAt == GENESIS_AT && level == 0) {
                logger.i("This is a placeholder profile... that might need to get synced...: $profile")
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(nostrEvent.pubKey)
            }
        } else {
            logger.w("We somehow have an unsignedNostrEvent/giftwrap event: $nostrEvent")
        }

        // Index reposts first because they have the contained posts...
        nostrEvent.toRepostedRelationWithContainedPost()?.let { (repostedRelation, containedPost) ->
            // Find or create placeHolder note that is being reposted
            val repostedNostrEvent = database.nostrEventDao().getNostrEventById(repostedRelation.repostedNostrEventId)

            val repostedProfilePublicKey = if (repostedNostrEvent == null) {
                // Sync event...
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(repostedRelation.repostedNostrEventId)

                database.nostrEventDao().insert(
                    if (containedPost != null) {
                        NostrEvent(
                            id = containedPost.id,
                            content = containedPost.content,
                            createdAt = Instant.fromEpochSeconds(containedPost.createdAt),
                            pubKey = containedPost.pubKey,
                            sig = containedPost.sig,
                            kind = containedPost.kind,
                            tags = containedPost.tags
                        )
                    } else {
                        NostrEvent(
                            id = repostedRelation.repostedNostrEventId,
                            content = "Pending Sync",
                            createdAt = GENESIS_AT,
                            pubKey = nostrEvent.pubKey,
                            sig = "",
                            kind = nostrEvent.kind,
                            tags = emptyArray()
                        )
                    }
                )

                repostedRelation.repostedProfilePublicKey
            } else {
                repostedRelation.repostedProfilePublicKey ?: repostedNostrEvent.pubKey
            }

            val repostedProfile = repostedProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (repostedProfilePublicKey != null && repostedProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = repostedProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }
            database.repostedRelationDao().upsert(
                repostedRelation.copy(
                    repostedProfilePublicKey = repostedProfilePublicKey
                )
            )
        }


        // Sync tagged events and authors...
        nostrEvent.tags.taggedEvents().take(4).forEach { taggedEvent -> // We only look at the first 3 tagged events...
            val taggedNostrEvent = database.nostrEventDao().getNostrEventById(taggedEvent.eventId)
            if (taggedNostrEvent == null) {
                database.nostrEventDao().insert(
                    NostrEvent(
                        id = taggedEvent.eventId,
                        content = "Pending Sync... ",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                val recommendRelayUrl = taggedEvent.relay?.url

                if (recommendRelayUrl != null) {
                    if (eventIdsToSync[recommendRelayUrl] == null) {
                        eventIdsToSync[recommendRelayUrl] = mutableSetOf()
                    }
                    eventIdsToSync[recommendRelayUrl]?.add(taggedEvent.eventId)
                } else {
                    if (eventIdsToSync[relayURL] == null) {
                        eventIdsToSync[relayURL] = mutableSetOf()
                    }
                    eventIdsToSync[relayURL]?.add(taggedEvent.eventId)

                }
            }

        }
        nostrEvent.tags.taggedUsers().take(5).forEach { taggedUser -> // 5 tagged users...
            val recommendedRelay = taggedUser.relayHint?.url

            val taggedProfile = database.profileDao().getProfileByPublicKey(taggedUser.pubKey)

            if (taggedProfile == null) {
                // Save a placeholder
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = taggedUser.pubKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            database.mentionDao().insert(
                Mention(
                    mentionedPublicKey = taggedUser.pubKey,
                    mentioningNostrEventId = nostrEvent.id,
                    relayUrl = taggedUser.relayHint?.url
                )
            )
            if (recommendedRelay != null) {
                if (profilePublicKeysToSync[recommendedRelay] == null) {
                    profilePublicKeysToSync[recommendedRelay] = mutableSetOf()
                }
                profilePublicKeysToSync[recommendedRelay]?.add(taggedUser.pubKey)
            } else {
                if (profilePublicKeysToSync[relayURL] == null) {
                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                }
                profilePublicKeysToSync[relayURL]?.add(
                    taggedUser.pubKey
                )
            }
        }

        nostrEvent.toProfile()?.let { profile ->
            database.profileDao().upsert(profile)
        }

        // TODO: Support MLSGroupEvent handling...
        nostrEvent.toGroupEvent(
            userPublicKey = activeKeyPair.pubKey.toHex()
        )?.let { groupEvent ->

            groupEvent.groupId()?.let { chatRoomId ->
                val localChatRoom = database.chatRoomDao().findChatRoomById(chatRoomId)

                if (localChatRoom != null) {
                    val mlsGroupStateByteArray = localChatRoom.chatRoom.mlsGroupState?.hexToByteArray()

                    if (mlsGroupStateByteArray != null) {
                        val mlsGroup = MlsGroup.restore(
                            MlsGroupState.decodeTls(
                                mlsGroupStateByteArray
                            )
                        )

                        val memberPubkeys = mlsGroup.members().mapNotNull { (leafIndex, leafNode) ->
                            val pubkey = when (val cred = leafNode.credential) {
                                is Credential.Basic -> cred.identity.toHexKey()
                                else -> null
                            }

                            pubkey
                        }

                        if (memberPubkeys.contains(activeKeyPair.pubKey.toHex())) {
                            // Ingest message...
                            val groupEventResult = MarmotInboundManager.processGroupEvent(
                                database = database,
                                activeKeyPair = activeKeyPair,
                                localChatRoom = localChatRoom,
                                mlsGroup = mlsGroup,
                                groupEvent = groupEvent
                            )?.let { chatMessage ->
                                database.chatMessageDao().upsert(
                                    chatMessage
                                )
                            }

                            // Save groupEventResult as chatMessage...


                        } else {
                            throw MarmotNotMemberOfChatGroupException("We are not a member of the chat room ${localChatRoom.chatRoom.id}")
                        }
                    } else {
                        throw MarmotMissingNostrGroupDataExtension("Couldn't find chatRoom for $nostrEvent")
                    }
                } else {
                    throw MarmotMissingChatGroupException("Couldn't find chatRoom for $nostrEvent")
                }
            }
        }

        nostrEvent.toGiftWrapMessageWithReceiverPTag()?.let { giftWrapMessage ->
            logger.d("giftWrapMessage: $giftWrapMessage")
            val giftWrapReceiverProfile = database.profileDao().getProfileByPublicKey(giftWrapMessage.receiverPublicKey)

            if (giftWrapReceiverProfile == null) {
                // Save a placeholder
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = giftWrapMessage.receiverPublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )

                val recommendRelayUrl = giftWrapMessage.receiverRelayHit

                if (recommendRelayUrl != null) {
                    if (profilePublicKeysToSync[recommendRelayUrl] == null) {
                        profilePublicKeysToSync[recommendRelayUrl] = mutableSetOf()
                    }
                    profilePublicKeysToSync[recommendRelayUrl]?.add(giftWrapMessage.receiverPublicKey)
                } else {
                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(
                        giftWrapMessage.receiverPublicKey
                    )
                }
            }


            database.giftWrapMessageDao().upsert(
                giftWrapMessage
            )

            giftWrapMessage.decryptGiftWrapSeal(
                activeKeyPair
            ).let { giftWrapSeal ->
                if (giftWrapSeal == null) {
                    throw GiftWrapUnsealException("Failed to unseal ${nostrEvent.id} from the giftWrap")
                } else {
                    database.giftWrapSealDao().upsert(
                        giftWrapSeal
                    )

                    giftWrapSeal.decryptGiftWrapPayload(
                        activeKeyPair.privKey!!
                    ).let { decryptedGiftWrapPayload ->
                        if (decryptedGiftWrapPayload == null) {
                            throw GiftWrapSealDecryptionException(
                                "Failed to decrypt sealed ${giftWrapSeal.id} payload"
                            )
                        } else {
                            if (giftWrapSeal.publicKey.lowercase() != decryptedGiftWrapPayload.publicKey.lowercase()) {
                                val giftWrapImpersonation =
                                    GiftWrapImpersonationException(
                                        "Seal pubkey (${giftWrapSeal.publicKey}) and payload pubkey (${decryptedGiftWrapPayload.publicKey}) needs to be the same for the giftWrap ${nostrEvent.id}"
                                    )
                                logger.e("Impersonation", giftWrapImpersonation)
                                throw giftWrapImpersonation // Once this is thrown the database transaction fails and nothing gets saved to the DB...
                            }

                            database.giftWrapPayloadDao().upsert(
                                decryptedGiftWrapPayload
                            )

                            if (decryptedGiftWrapPayload.kind == ChatMessageEvent.KIND) {
                                logger.d("chatMessageEvent: $decryptedGiftWrapPayload")
                                // Handle normal nip-17 messages...
                                val localChatRoom = database.chatRoomDao().findChatRoomById(
                                    decryptedGiftWrapPayload.chatRoomId
                                )

                                if (localChatRoom == null) {
                                    val userPublicKey = activeKeyPair.pubKey.toHex()
                                    database.chatRoomDao().upsert(
                                        ChatRoom(
                                            id = decryptedGiftWrapPayload.chatRoomId,
                                            userPublicKey = userPublicKey,
                                            subject = decryptedGiftWrapPayload.parseSubject(),
                                            createdAt = decryptedGiftWrapPayload.createdAt,
                                            initialGiftWrapPayloadId = decryptedGiftWrapPayload.id,
                                            mlsGroupState = null
                                        )
                                    )

                                    // Add participants...
                                    val participants = decryptedGiftWrapPayload.participantPTags(
                                        NormalizedRelayUrl(relayURL) // TODO: Get relayURL for publicKey profile...
                                    ).map {
                                        Participant(
                                            participantPublicKey = it.pubKey,
                                            chatRoomId = decryptedGiftWrapPayload.chatRoomId,
                                            relayHint = it.relayHint?.url
                                        )
                                    }

                                    // Sync missing participant profiles...
                                    participants.forEach { participant ->
                                        val giftWrapParticipantProfile =
                                            database.profileDao()
                                                .getProfileByPublicKey(participant.participantPublicKey)

                                        if (giftWrapParticipantProfile == null) {
                                            // Save a placeholder
                                            database.profileDao().insertPlaceholderProfile(
                                                Profile(
                                                    displayName = "LOADING...",
                                                    publicKey = participant.participantPublicKey,
                                                    createdAt = GENESIS_AT,
                                                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                                                )
                                            )

                                            val recommendRelayUrl =
                                                giftWrapMessage.receiverRelayHit

                                            if (recommendRelayUrl != null) {
                                                if (profilePublicKeysToSync[recommendRelayUrl] == null) {
                                                    profilePublicKeysToSync[recommendRelayUrl] =
                                                        mutableSetOf()
                                                }
                                                profilePublicKeysToSync[recommendRelayUrl]?.add(
                                                    participant.participantPublicKey
                                                )
                                            } else {
                                                if (profilePublicKeysToSync[relayURL] == null) {
                                                    profilePublicKeysToSync[relayURL] =
                                                        mutableSetOf()
                                                }
                                                profilePublicKeysToSync[relayURL]?.add(
                                                    participant.participantPublicKey
                                                )
                                            }
                                        }
                                    }

                                    database.participantDao().upsert(
                                        participants
                                    )

                                    participants.filter { it.participantPublicKey != userPublicKey }
                                        .forEach { participant ->
                                            val publicKey = participant.participantPublicKey

                                            val chatMessageRelayListEvent =
                                                database.nostrEventDao()
                                                    .getAuthoredNostrEvents(
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

                                            if (chatMessageRelayListEvent != null) {
                                                // Sync messages from this relay that were sent by us
                                                val synchronizationFilter =
                                                    SynchronizationFilter(
                                                        kinds = arrayOf(
                                                            GiftWrapEvent.KIND,
                                                        ),
                                                        authors = arrayOf(
                                                            userPublicKey
                                                        ),
                                                        tags = mapOf(
                                                            Pair(
                                                                "p",
                                                                listOf(participant.participantPublicKey)
                                                            )
                                                        ),
                                                        limit = 50
                                                    )
                                                database.negentropySynchronizeRequestDao()
                                                    .insert(
                                                        chatMessageRelayListEvent.relays()
                                                            .map { normalizedRelayUrl ->
                                                                NegentropySynchronizeRequest(
                                                                    id = NegentropySynchronizeRequest.computeId(
                                                                        relayURL = normalizedRelayUrl.url,
                                                                        synchronizationFilter = synchronizationFilter
                                                                    ),
                                                                    purpose = "sent-messages",
                                                                    synchronizationFilter = synchronizationFilter,
                                                                    relayURL = normalizedRelayUrl.url,
                                                                    level = 0
                                                                )
                                                            }
                                                    )
                                            } else {
                                                logger.w("We don't have a chatMessageRelayListEvent for the pubkey $publicKey")

                                                // Sync ChatMessageRelayListEvent publicKey...
                                                // TODO: Get relayHint form participant...
                                                if (profilePublicKeysToSync.containsKey(
                                                        relayURL
                                                    )
                                                ) {
                                                    profilePublicKeysToSync[relayURL] =
                                                        mutableSetOf()
                                                }
                                                profilePublicKeysToSync[relayURL]?.add(
                                                    participant.participantPublicKey
                                                )
                                            }
                                        }
                                } else {
                                    decryptedGiftWrapPayload.parseSubject()?.let { subject ->
                                        logger.d("Subject: $subject")
                                        database.chatRoomDao().upsert(
                                            localChatRoom.chatRoom.copy(
                                                subject = subject,
                                                updatedAt = decryptedGiftWrapPayload.createdAt
                                            )
                                        )
                                    }
                                }

                            } else if (decryptedGiftWrapPayload.kind == WelcomeEvent.KIND) {

                                val welcomeEvent = WelcomeEvent(
                                    id = decryptedGiftWrapPayload.id, // TODO: Need the ID as string in decryptedGiftWrapPayload...
                                    pubKey = decryptedGiftWrapPayload.publicKey,
                                    content = decryptedGiftWrapPayload.content,
                                    createdAt = decryptedGiftWrapPayload.createdAt.epochSeconds,
                                    tags = decryptedGiftWrapPayload.tags,
                                    sig = ""
                                )
                                logger.d("welcomeEvent: ${welcomeEvent.toJson()}")

                                // TODO: Find keypackage and decrypt welcome...
                                // TODO: Get keyPackage from
                                val keyPackageEventId = welcomeEvent.keyPackageEventId() ?: throw MarmotWelcomeEventMissingKeyPackageEventIdException(
                                    "Couldn't find keyPackageEventId for $welcomeEvent"
                                )
                                logger.d("keyPackageEventId: $keyPackageEventId")
                                val marmotKeyPackageBundle = database.marmotKeyPackageBundleDao().getMarmotKeyPackageBundleById(
                                    keyPackageEventId
                                ) ?: throw MarmotMissingKeyPackageBundleException(
                                    "Couldn't find marmotKeyPackageBundle for $welcomeEvent"
                                )
                                logger.d("marmotKeyPackageBundle: $marmotKeyPackageBundle")

                                val welcomeBytes = Base64.decode(
                                    welcomeEvent.welcomeBase64()
                                )

                                val initPrivateKey = marmotKeyPackageBundle.ncryptsecInitPrivateKey.hexToByteArray() // TODO: Decrypt
                                val signaturePrivateKey = marmotKeyPackageBundle.ncryptsecSignaturePrivateKey.hexToByteArray() // TODO: Decrypt
                                val encryptionPrivateKey = marmotKeyPackageBundle.ncryptsecEncryptionPrivateKey.hexToByteArray() // TODO: Decrypt

                                val tlsReader = TlsReader(
                                    data = marmotKeyPackageBundle.tlsEncodedMarmotKeyPackage
                                )

                                val group = MlsGroup.processWelcome(
                                    welcomeBytes,
                                    bundle = KeyPackageBundle(
                                        keyPackage = MlsKeyPackage.decodeTls(
                                            tlsReader
                                        ),
                                        initPrivateKey = initPrivateKey,
                                        signaturePrivateKey = signaturePrivateKey,
                                        encryptionPrivateKey = encryptionPrivateKey
                                    )
                                )
                                logger.d("Group: $group")

                                val nostrGroupId = group.currentMarmotData()?.nostrGroupId ?: throw MarmotMissingNostrGroupDataExtension("Didn't find group data for $group")

                                logger.d("nostrGroupId: $nostrGroupId")

                                val localChatRoom = database.chatRoomDao().findChatRoomById(
                                    nostrGroupId
                                )

                                if (localChatRoom == null) {
                                    logger.d("processWelcome: welcomeBytes=${welcomeBytes.size}B")
                                    logger.d("Marmot Data: ${group.currentMarmotData()}")


                                    // TODO: Save the chat room We can now s
                                    val userPublicKey = activeKeyPair.pubKey.toHex()
                                    database.chatRoomDao().upsert(
                                        ChatRoom(
                                            id = nostrGroupId,
                                            userPublicKey = userPublicKey,
                                            subject = group.currentMarmotData()?.name?.ifBlank { null },
                                            initialGiftWrapPayloadId = decryptedGiftWrapPayload.id,
                                            createdAt = decryptedGiftWrapPayload.createdAt,
                                            mlsGroupState = group.saveState().encodeTls().toHex(),
                                        )
                                    )

                                    database.marmotKeyPackageBundleDao().upsert(
                                        marmotKeyPackageBundle.copy(
                                            consumed = true
                                        )
                                    )

                                    val normalizedRelayUrl = NormalizedRelayUrl(relayURL)


                                    val participants = group.members().mapNotNull { (leafIndex, leafNode) ->
                                        val pubkey = when (val cred = leafNode.credential) {
                                            is Credential.Basic -> cred.identity.toHexKey()
                                            else -> null
                                        }

                                        if (pubkey != null) {
                                            Participant(
                                                participantPublicKey = pubkey,
                                                chatRoomId = nostrGroupId,
                                                relayHint = group.currentMarmotData()?.relays?.firstOrNull() ?: normalizedRelayUrl.url
                                            )
                                        } else {
                                            null
                                        }
                                    }

                                    logger.d("Participants: $participants")
                                    // Sync missing participant profiles...
                                    participants.forEach { participant ->
                                        val giftWrapParticipantProfile = database.profileDao().getProfileByPublicKey(participant.participantPublicKey)

                                        if (giftWrapParticipantProfile == null) {
                                            // Save a placeholder
                                            database.profileDao().insertPlaceholderProfile(
                                                Profile(
                                                    displayName = "LOADING...",
                                                    publicKey = participant.participantPublicKey,
                                                    createdAt = GENESIS_AT,
                                                    nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                                                )
                                            )

                                            val recommendRelayUrl = giftWrapMessage.receiverRelayHit

                                            if (recommendRelayUrl != null) {
                                                if (profilePublicKeysToSync[recommendRelayUrl] == null) {
                                                    profilePublicKeysToSync[recommendRelayUrl] = mutableSetOf()
                                                }
                                                profilePublicKeysToSync[recommendRelayUrl]?.add(participant.participantPublicKey)
                                            } else {
                                                if (profilePublicKeysToSync[relayURL] == null) {
                                                    profilePublicKeysToSync[relayURL] = mutableSetOf()
                                                }
                                                profilePublicKeysToSync[relayURL]?.add(
                                                    participant.participantPublicKey
                                                )
                                            }
                                        }
                                    }

                                    database.participantDao().upsert(
                                        participants
                                    )

                                    // Save ChatRoom Relays...
                                    participants.filter { it.participantPublicKey != userPublicKey }.forEach { participant ->
                                        val publicKey = participant.participantPublicKey

                                        val chatMessageRelayListEvent = database.nostrEventDao().getAuthoredNostrEvents(
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

                                        if (chatMessageRelayListEvent != null) {
                                            // Sync messages from this relay that were sent by us
                                            val synchronizationFilter = SynchronizationFilter(
                                                kinds = arrayOf(
                                                    GiftWrapEvent.KIND,
                                                ),
                                                authors = arrayOf(
                                                    userPublicKey
                                                ),
                                                tags = mapOf(
                                                    Pair("p", listOf(participant.participantPublicKey))
                                                ),
                                                limit = 50
                                            )
                                            database.negentropySynchronizeRequestDao().insert(
                                                chatMessageRelayListEvent.relays().map { normalizedRelayUrl ->
                                                    NegentropySynchronizeRequest(
                                                        id = NegentropySynchronizeRequest.computeId(
                                                            relayURL = normalizedRelayUrl.url,
                                                            synchronizationFilter = synchronizationFilter
                                                        ),
                                                        purpose = "sent-messages",
                                                        synchronizationFilter = synchronizationFilter,
                                                        relayURL = normalizedRelayUrl.url,
                                                        level = 0
                                                    )
                                                }
                                            )
                                        } else {
                                            logger.w("We don't have a chatMessageRelayListEvent for the pubkey $publicKey")

                                            // Sync ChatMessageRelayListEvent publicKey...
                                            // TODO: Get relayHint form participant...
                                            if (profilePublicKeysToSync.containsKey(relayURL)) {
                                                profilePublicKeysToSync[relayURL] = mutableSetOf()
                                            }
                                            profilePublicKeysToSync[relayURL]?.add(participant.participantPublicKey)
                                        }
                                    }

                                    // Where is the decoded welcome message...
                                    // TODO: Sync GroupEvents/Messages...
//                                    database.chatMessageDao().upsert(
//                                        ChatMessage(
//                                            giftWrapPayloadId = decryptedGiftWrapPayload.id,
//                                            senderPublicKey = decryptedGiftWrapPayload.publicKey,
//                                            isUserMessage = activeKeyPair.pubKey.toHex() == decryptedGiftWrapPayload.publicKey,
//                                            chatRoomId = nostrGroupId,
//                                            createdAt = decryptedGiftWrapPayload.createdAt,
//                                            content = "Coming soon.", // TODO: Figure out what to do here...
//                                        )
//                                    )
                                } else {
                                    logger.w("Chat Room already exists for $nostrGroupId")
                                }
                                // TODO: Check encoding...
                            } else {
                                logger.w("Unsupported event: $decryptedGiftWrapPayload")
                            }
                        }
                    }
                }
            }
        }

        nostrEvent.toPost()?.let { post ->
            if (post.replyToId != null) {
                // Find or create placeHolder note that is replyTo
                val replyingToPost = database.postDao().getPostById(post.replyToId)

                if (replyingToPost == null) {
                    // Persist PlaceHolder and sync
                    // TODO: Get replyingToAuthorPublicKey
                    database.postDao().insert(
                        Post(
                            id = post.replyToId,
                            createdAt = GENESIS_AT,
                            nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                            content = post.replyToId,
                            profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                        )
                    )
                }
            }

            database.postDao().upsert(post)
        }

        nostrEvent.toReaction()?.let { reaction ->
            val replyingToPost = database.postDao().getPostById(reaction.replyToId)

            if (replyingToPost == null) {
                // Persist PlaceHolder and sync
                database.postDao().insert(
                    Post(
                        id = reaction.replyToId,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync handled by tagged events...
                        content = reaction.replyToId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync handled by tagged events...
                    )
                )
            }
            database.reactionDao().upsert(reaction)
        }

        nostrEvent.toQuotedRelation()?.let { quotedRelation ->
            val quotedNostrEvent = database.nostrEventDao().getNostrEventById(quotedRelation.quotedNostrEventId)

            val quotedProfilePublicKey = if (quotedNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(quotedRelation.quotedNostrEventId)

                database.nostrEventDao().insert(
                    NostrEvent(
                        id = quotedRelation.quotedNostrEventId,
                        content = "Pending Sync",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                quotedRelation.quotedProfilePublicKey
            } else {
                quotedNostrEvent.pubKey
            }

            val quotedProfile = quotedProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (quotedProfilePublicKey != null && quotedProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = quotedProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            // TODO: Might need to store placeholders
            database.quotedRelationDao().upsert(
                quotedRelation.copy(
                    quotedProfilePublicKey = quotedProfilePublicKey
                )
            )
        }

        nostrEvent.toInReplyToRelation()?.let { inReplyToRelation ->

            val inReplyToNostrEvent = database.nostrEventDao().getNostrEventById(inReplyToRelation.inReplyToNostrEventId)

            val inReplyToProfilePublicKey = if (inReplyToNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(inReplyToRelation.inReplyToNostrEventId)

                database.nostrEventDao().insert(
                    NostrEvent(
                        id = inReplyToRelation.inReplyToNostrEventId,
                        content = "Pending Sync",
                        createdAt = GENESIS_AT,
                        pubKey = nostrEvent.pubKey,
                        sig = "",
                        kind = nostrEvent.kind,
                        tags = emptyArray()
                    )
                )

                inReplyToRelation.inReplyToProfilePublicKey
            } else {
                inReplyToNostrEvent.pubKey
            }

            val inReplyToRootNostrEvent = inReplyToRelation.inReplyToRootNostrEventId?.let { database.nostrEventDao().getNostrEventById(it) }

            val inReplyToRootProfilePublicKey = if (inReplyToRootNostrEvent == null) {
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(inReplyToRelation.inReplyToNostrEventId)

                inReplyToRelation.inReplyToRootProfilePublicKey
                // Might need to save place holder...
            } else {
                inReplyToRootNostrEvent.pubKey
            }

            val inReplyToProfile = inReplyToRootProfilePublicKey?.let { database.profileDao().getProfileByPublicKey(it) }

            if (inReplyToRootProfilePublicKey != null && inReplyToProfile == null) {
                database.profileDao().insertPlaceholderProfile(
                    Profile(
                        displayName = "LOADING...",
                        publicKey = inReplyToRootProfilePublicKey,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                    )
                )
            }

            database.inReplyToRelationDao().upsert(
                inReplyToRelation.copy(
                    inReplyToRootProfilePublicKey = inReplyToRootProfilePublicKey,
                    inReplyToProfilePublicKey = inReplyToProfilePublicKey
                )
            )
        }

        nostrEvent.toZap()?.let { zap ->
            // Find or create placeHolder note that is being reposted
            val zappedPost = database.postDao().getPostById(zap.postId)

            if (zappedPost == null) {
                // Persist PlaceHolder and sync
                database.postDao().insert(
                    Post(
                        id = zap.postId,
                        createdAt = GENESIS_AT,
                        nostrEventId = nostrEvent.id, // Will get overwriting by sync,
                        content = zap.postId,
                        profilePublicKey = nostrEvent.pubKey // Will get overwriting by sync,
                    )
                )

                // Request a sync for the post being replied to
                if (eventIdsToSync[relayURL] == null) {
                    eventIdsToSync[relayURL] = mutableSetOf()
                }
                eventIdsToSync[relayURL]?.add(zap.postId)
            }

            database.zapDao().upsert(zap)
        }

        if (nostrEvent.kind == ContactListEvent.KIND) {
            val contactListEvent = ContactListEvent(
                id = nostrEvent.id,
                tags = nostrEvent.tags,
                pubKey = nostrEvent.pubKey,
                createdAt = nostrEvent.createdAt.epochSeconds,
                content = nostrEvent.content,
                sig = nostrEvent.sig
            )
            logger.i("Store contact list: $contactListEvent")

            contactListEvent.verifiedFollowKeySet().forEach { followedHexKEys ->

                // Find or create profile with the pubKey... if not found submit a sync request...
                val profile = database.profileDao().getProfileByPublicKey(followedHexKEys)

                if (profile == null) {
                    val placeHolderProfile =
                        Profile(
                            displayName = "LOADING...",
                            publicKey = followedHexKEys,
                            createdAt = GENESIS_AT,
                            nostrEventId = nostrEvent.id,  // Will get overwriting by sync,
                        )
                    database.profileDao().insertPlaceholderProfile(placeHolderProfile)
                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(followedHexKEys)
                } else if (profile.createdAt == GENESIS_AT && level == 0) {
                    logger.i("${level} This is a placeholder profile... that might need to get synced...: $profile")

                    if (profilePublicKeysToSync[relayURL] == null) {
                        profilePublicKeysToSync[relayURL] = mutableSetOf()
                    }
                    profilePublicKeysToSync[relayURL]?.add(followedHexKEys)
                }

                // Save profile connections...
                database.connectionDao().upsert(
                    Connection(
                        sourcePublicKey = nostrEvent.pubKey,
                        destinationPublicKey = followedHexKEys,
                        nostrEventId = nostrEvent.id
                    )
                )
            }
            // TODO: Delete connections where sourcePublicKey == nostr.pubKey but destinationPublickKey !in verifiedFollowKeySet
        }

        if (level == 0) {
            val negentropySynchronizeRequests = mutableListOf<NegentropySynchronizeRequest>()
            profilePublicKeysToSync.forEach { relayUrlProfilePublicKey ->
                val synchronizationFilter =
                    SynchronizationFilter(
                        authors = relayUrlProfilePublicKey.value.toTypedArray(),
                        kinds = profileEventKinds
                    )
                negentropySynchronizeRequests.add(
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayUrlProfilePublicKey.key,
                            synchronizationFilter
                        ),
                        purpose = "synchronization",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = relayUrlProfilePublicKey.key,
                        level = level + 1
                    )
                )
            }

            eventIdsToSync.forEach { relayUrlEventIds ->
                // TODO: Hash relayUrl + synchronicationFilter + ephocMinutes
                val synchronizationFilter =
                    SynchronizationFilter(
                        ids = relayUrlEventIds.value.toTypedArray(),
                    )
                Clock.System.now().toEpochMilliseconds()
                negentropySynchronizeRequests.add(
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayUrlEventIds.key,
                            synchronizationFilter
                        ),
                        purpose = "synchronization",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = relayUrlEventIds.key,
                        level = level + 1
                    )
                )
            }

            if (negentropySynchronizeRequests.isNotEmpty()) {
                database.negentropySynchronizeRequestDao().insert(
                    negentropySynchronizeRequests
                )
            }
        }
    }
}