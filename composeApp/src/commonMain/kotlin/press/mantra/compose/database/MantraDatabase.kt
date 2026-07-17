package press.mantra.compose.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.TypeConverters
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import press.mantra.compose.database.converters.MantraConverters
import press.mantra.compose.database.dao.BroadcastNostrEventReceiptDao
import press.mantra.compose.database.dao.BroadcastNostrEventRequestDao
import press.mantra.compose.database.dao.ChatMessageBroadcastNostrEventReceiptRelationDao
import press.mantra.compose.database.dao.ChatMessageBroadcastNostrEventRequestRelationDao
import press.mantra.compose.database.dao.ChatMessageDao
import press.mantra.compose.database.dao.ChatMessageNostrEventRelationDao
import press.mantra.compose.database.dao.ChatRoomDao
import press.mantra.compose.database.dao.ConnectionDao
import press.mantra.compose.database.dao.GiftWrapMessageDao
import press.mantra.compose.database.dao.GiftWrapPayloadDao
import press.mantra.compose.database.dao.GiftWrapSealDao
import press.mantra.compose.database.dao.InReplyToRelationDao
import press.mantra.compose.database.dao.MantraArtifactDao
import press.mantra.compose.database.dao.MantraArtifactVersionDao
import press.mantra.compose.database.dao.MantraChapterDao
import press.mantra.compose.database.dao.MantraChunkDao
import press.mantra.compose.database.dao.MantraDialectDao
import press.mantra.compose.database.dao.MantraTranslationArtifactVersionDao
import press.mantra.compose.database.dao.MantraTranslationArtifactVersionContributorDao
import press.mantra.compose.database.dao.MantraTranslationChapterDao
import press.mantra.compose.database.dao.MantraTranslationChapterContributorDao
import press.mantra.compose.database.dao.MantraTranslationChunkDao
import press.mantra.compose.database.dao.MantraTranslationDao
import press.mantra.compose.database.dao.MantraTranslationContributorDao
import press.mantra.compose.database.dao.MarmotCommitResultDao
import press.mantra.compose.database.dao.MarmotOutboundDao
import press.mantra.compose.database.dao.MarmotGroupEventDao
import press.mantra.compose.database.dao.MarmotInnerEventDao
import press.mantra.compose.database.dao.MarmotKeyPackageBundleDao
import press.mantra.compose.database.dao.MarmotKeyPackageDao
import press.mantra.compose.database.dao.MarmotRetainedEpochSecretDao
import press.mantra.compose.database.dao.MentionDao
import press.mantra.compose.database.dao.NegentropySynchronizeRequestDao
import press.mantra.compose.database.dao.NegentropySynchronizeResultDao
import press.mantra.compose.database.dao.NostrDao
import press.mantra.compose.database.dao.NostrEventDao
import press.mantra.compose.database.dao.NostrEventRelayDao
import press.mantra.compose.database.dao.NostrNip17Dao
import press.mantra.compose.database.dao.ParticipantDao
import press.mantra.compose.database.dao.PostDao
import press.mantra.compose.database.dao.ProfileDao
import press.mantra.compose.database.dao.QuotedRelationDao
import press.mantra.compose.database.dao.ReactionDao
import press.mantra.compose.database.dao.RecentSearchDao
import press.mantra.compose.database.dao.RelayDao
import press.mantra.compose.database.dao.RepostedRelationDao
import press.mantra.compose.database.dao.SynchronizeNostrEventRequestDao
import press.mantra.compose.database.dao.SynchronizeNostrEventResultDao
import press.mantra.compose.database.dao.UnsignedNostrEventDao
import press.mantra.compose.database.dao.ZapDao
import press.mantra.compose.database.model.BroadcastNostrEventReceipt
import press.mantra.compose.database.model.BroadcastNostrEventRequest
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation
import press.mantra.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import press.mantra.compose.database.model.ChatMessageNostrEventRelation
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.Connection
import press.mantra.compose.database.model.GiftWrapMessage
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.GiftWrapSeal
import press.mantra.compose.database.model.InReplyToRelation
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslation
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationArtifactVersionContributor
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.MantraTranslationChapterContributor
import press.mantra.compose.database.model.MantraTranslationChunk
import press.mantra.compose.database.model.MantraTranslationContributor
import press.mantra.compose.database.model.MarmotCommitResult
import press.mantra.compose.database.model.MarmotGroupEvent
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.MarmotKeyPackageBundle
import press.mantra.compose.database.model.MarmotRetainedEpochSecret
import press.mantra.compose.database.model.Mention
import press.mantra.compose.database.model.NegentropySynchronizeRequest
import press.mantra.compose.database.model.NegentropySynchronizeResult
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.NostrEventRelay
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.Post
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.QuotedRelation
import press.mantra.compose.database.model.Reaction
import press.mantra.compose.database.model.RecentSearch
import press.mantra.compose.database.model.Relay
import press.mantra.compose.database.model.RepostedRelation
import press.mantra.compose.database.model.SynchronizeNostrEventRequest
import press.mantra.compose.database.model.SynchronizeNostrEventResult
import press.mantra.compose.database.model.UnsignedNostrEvent
import press.mantra.compose.database.model.Zap
import kotlin.time.Instant

val GENESIS_AT = Instant.fromEpochMilliseconds(1231006505000L)

@Database(
    entities = [
        BroadcastNostrEventReceipt::class,
        BroadcastNostrEventRequest::class,
        Connection::class,
        ChatMessage::class,
        ChatMessageBroadcastNostrEventRequestRelation::class,
        ChatMessageBroadcastNostrEventReceiptRelation::class,
        ChatMessageNostrEventRelation::class,
        ChatRoom::class,
        GiftWrapMessage::class,
        GiftWrapSeal::class,
        GiftWrapPayload::class,
        InReplyToRelation::class,
        MantraArtifact::class,
        MantraArtifactVersion::class,
        MantraChapter::class,
        MantraChunk::class,
        MantraDialect::class,
        MantraTranslation::class,
        MantraTranslationArtifactVersion::class,
        MantraTranslationArtifactVersionContributor::class,
        MantraTranslationChapter::class,
        MantraTranslationChapterContributor::class,
        MantraTranslationChunk::class,
        MantraTranslationContributor::class,
        MarmotCommitResult::class,
        MarmotGroupEvent::class,
        MarmotInnerEvent::class,
        MarmotKeyPackage::class,
        MarmotKeyPackageBundle::class,
        MarmotRetainedEpochSecret::class,
        Mention::class,
        NegentropySynchronizeRequest::class,
        NegentropySynchronizeResult::class,
        NostrEvent::class,
        NostrEventRelay::class,
        Participant::class,
        Post::class,
        Profile::class,
        QuotedRelation::class,
        Reaction::class,
        RecentSearch::class,
        Relay::class,
        RepostedRelation::class,
        SynchronizeNostrEventRequest::class,
        SynchronizeNostrEventResult::class,
        UnsignedNostrEvent::class,
        Zap::class
    ],
    version = 1
)
@TypeConverters(MantraConverters::class)
abstract class MantraDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun chatMessageBroadcastNostrEventRequestRelationDao(): ChatMessageBroadcastNostrEventRequestRelationDao

    abstract fun chatMessageBroadcastNostrEventReceiptRelationDao(): ChatMessageBroadcastNostrEventReceiptRelationDao

    abstract fun chatMessageNostrEventRelationDao(): ChatMessageNostrEventRelationDao

    abstract fun chatRoomDao(): ChatRoomDao

    abstract fun connectionDao(): ConnectionDao

    abstract fun giftWrapMessageDao(): GiftWrapMessageDao

    abstract fun giftWrapSealDao(): GiftWrapSealDao

    abstract fun giftWrapPayloadDao(): GiftWrapPayloadDao

    abstract fun inReplyToRelationDao(): InReplyToRelationDao

    abstract fun mantraArtifactDao(): MantraArtifactDao

    abstract fun mantraArtifactVersionDao(): MantraArtifactVersionDao

    abstract fun mantraChapterDao(): MantraChapterDao

    abstract fun mantraChunkDao(): MantraChunkDao

    abstract fun mantraDialectDao(): MantraDialectDao

    abstract fun mantraTranslationArtifactVersionDao(): MantraTranslationArtifactVersionDao

    abstract fun mantraTranslationArtifactVersionContributorDao(): MantraTranslationArtifactVersionContributorDao

    abstract fun mantraTranslationChapterDao(): MantraTranslationChapterDao

    abstract fun mantraTranslationChapterContributorDao(): MantraTranslationChapterContributorDao

    abstract fun mantraTranslationChunkDao(): MantraTranslationChunkDao

    abstract fun mantraTranslationDao(): MantraTranslationDao

    abstract fun mantraTranslationContributorDao(): MantraTranslationContributorDao

    abstract fun marmotCommitResultDao(): MarmotCommitResultDao

    abstract fun marmotGroupEventDao(): MarmotGroupEventDao

    abstract fun marmotInnerEventDao(): MarmotInnerEventDao

    abstract fun marmotKeyPackageBundleDao(): MarmotKeyPackageBundleDao

    abstract fun marmotKeyPackageDao(): MarmotKeyPackageDao

    abstract fun marmotOutboundDao(): MarmotOutboundDao

    abstract fun marmotRetainedEpochSecretDao(): MarmotRetainedEpochSecretDao

    abstract fun mentionDao(): MentionDao

    abstract fun negentropySynchronizeRequestDao(): NegentropySynchronizeRequestDao

    abstract fun negentropySynchronizeResultDao(): NegentropySynchronizeResultDao

    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao

    abstract fun nostrEventRelayDao(): NostrEventRelayDao

    abstract fun nostrNip17Dao(): NostrNip17Dao

    abstract fun participantDao(): ParticipantDao

    abstract fun postDao(): PostDao

    abstract fun profileDao(): ProfileDao

    abstract fun quotedRelationDao(): QuotedRelationDao

    abstract fun reactionDao(): ReactionDao

    abstract fun recentSearchDao(): RecentSearchDao

    abstract fun relayDao(): RelayDao

    abstract fun repostedRelationDao(): RepostedRelationDao

    abstract fun synchronizeNostrEventRequestDao(): SynchronizeNostrEventRequestDao

    abstract fun synchronizeNostrEventResultDao(): SynchronizeNostrEventResultDao

    abstract fun unsignedNostrEventDao(): UnsignedNostrEventDao
    abstract fun zapDao(): ZapDao

    suspend fun wipeData() {
        useWriterConnection { transactor ->
            transactor.immediateTransaction {
                // TODO: Actually delete the data...
            }
        }
    }
}