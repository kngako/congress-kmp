package press.mantra.compose.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase
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
import press.mantra.compose.database.dao.DkgSessionDao
import press.mantra.compose.database.dao.FrostSigningSessionDao
import press.mantra.compose.database.dao.GiftWrapMessageDao
import press.mantra.compose.database.dao.GiftWrapPayloadDao
import press.mantra.compose.database.dao.GiftWrapSealDao
import press.mantra.compose.database.dao.GroupKeyStateDao
import press.mantra.compose.database.dao.GroupSignedEventDao
import press.mantra.compose.database.dao.InReplyToRelationDao
import press.mantra.compose.database.dao.MantraArtifactDao
import press.mantra.compose.database.dao.MantraArtifactVersionDao
import press.mantra.compose.database.dao.MantraChapterDao
import press.mantra.compose.database.dao.MantraChunkDao
import press.mantra.compose.database.dao.MantraDao
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
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
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
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
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
        DkgParticipantMessage::class,
        DkgSession::class,
        FrostSignerMessage::class,
        FrostSigningItem::class,
        FrostSigningSession::class,
        GiftWrapMessage::class,
        GiftWrapSeal::class,
        GiftWrapPayload::class,
        GroupKeyState::class,
        GroupSignedEvent::class,
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
    version = 15,
    autoMigrations = [
        // v2 only adds the DkgSession/DkgParticipantMessage tables, so Room can
        // generate the migration itself — nothing existing changes shape.
        AutoMigration(from = 1, to = 2),
        // v3 adds the three nullable approval timestamps to DkgSession. Nullable
        // additions need no default and drop no data, so Room generates this one
        // too. Rituals already in flight come back with all three null, which reads
        // as "not approved yet" and simply asks the member for each step.
        AutoMigration(from = 2, to = 3),
        // v4 changes no schema at all -- it rewrites `dkgApprovalNeeded` chat rows
        // into one type per ritual step. Data, not shape, so it is a manual
        // migration passed to the builder rather than an entry here. See
        // MIGRATION_3_4.
        //
        // v5 adds the nullable MarmotInnerEvent.payloadEventId, which names the
        // nip30303 event a SubmissionEvent rumor carries. Rumors queued before
        // this come back null, which reads as "not a submission" -- correct,
        // since none of them were.
        AutoMigration(from = 4, to = 5),
        // v6 adds the FrostSigningSession/FrostSignerMessage tables and the
        // nullable DkgSession.publicShares. New tables and a nullable column are
        // both shapes Room can migrate itself. A ceremony that completed before
        // this reads back null, and signing falls back to not cross-checking
        // shares rather than refusing to run.
        AutoMigration(from = 5, to = 6),
        // v7 adds the GroupKeyState table, which records what shared key a room
        // signs with instead of leaving it to be rederived. A new table is a
        // shape Room migrates itself. Rooms created before this have no row and
        // fall back to the rederivation scan in FrostSigningManager.completedKey,
        // which is why that scan stays.
        AutoMigration(from = 6, to = 7),
        // v8 adds a nullable direct message recipient to MarmotInnerEvent and
        // ChatMessage. Nullable additions need no default and drop no data, so Room
        // generates this one. Rows written before it come back null, which reads as
        // "not a direct message" -- the only answer that is true of all of them.
        AutoMigration(from = 7, to = 8),
        // v9 adds the nullable FrostSigningSession.derivationPath, which says
        // what key off the ceremony's the session signs as. Sessions started
        // before it read back null, meaning the untweaked threshold key -- which
        // is what they were signing as, so one caught mid-flight finishes the way
        // it began rather than switching keys between two of its own rounds.
        AutoMigration(from = 8, to = 9),
        // v10 moves FrostSigningSession's five per-event columns onto the new
        // FrostSigningItem table, so one session can sign a batch. It copies
        // before it drops, which no AutoMigration can express -- see
        // MIGRATION_9_10, and the note there on why an in-flight session losing
        // its nonce seed would be worse than losing the session.
        //
        // v11 adds the nullable ChatMessage.frostSigningSessionId, which says
        // which signing session a transcript line is about. Lines written before
        // it read back null and are read by the clock, the way they always were:
        // a room that could only propose one thing at a time cannot have written
        // two sessions' lines into the same stretch of transcript.
        AutoMigration(from = 10, to = 11),
        // v12 adds the nullable ChatRoom.archiveRequestedAt -- renamed to
        // chronicleRequestedAt in v14 -- which stops a device with no signed work
        // in a room asking the group for its history on every launch while an
        // answer is in flight. Rooms written before it read back null, meaning
        // "never asked" -- true of all of them, and harmless: the request only
        // goes out for a room that holds no signed work, and a room that holds
        // some will not ask.
        AutoMigration(from = 11, to = 12),
        // v13 adds the GroupSignedEvent table and the nullable
        // `groupSignedEventId` on every row a signed event turns into. A new
        // table and nullable additions are both shapes Room migrates itself.
        // Rows written before it read back null, meaning "this device does not
        // hold the event behind this row" -- true of all of them, since nothing
        // kept it. They are not backfilled: the events are gone, and inventing
        // an id for one would point a row at a signature nobody can produce.
        AutoMigration(from = 12, to = 13),
        // v14 changes one column name and three stored strings, all of which say
        // "archive" about the group's signed record. The word was wanted back for
        // the ordinary thing a user does to a chat, and a column called
        // `archiveRequestedAt` sitting on ChatRoom is exactly where the two
        // meanings would have met. Room can rename a column and cannot rewrite the
        // rows in the same breath, so this is a manual migration passed to the
        // builder rather than an entry here. See MIGRATION_13_14.
        //
        // v15 adds an index on ChatMessage.chatRoomId. No column changes and no
        // rows move -- the chat list now looks up each room's newest line, and
        // without the index that lookup reads every message on the device. Room
        // creates an index on its own.
        AutoMigration(from = 14, to = 15),
    ]
)
@ColumnTypeConverters(MantraConverters::class)
abstract class MantraDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao

    abstract fun chatMessageDao(): ChatMessageDao

    abstract fun chatMessageBroadcastNostrEventRequestRelationDao(): ChatMessageBroadcastNostrEventRequestRelationDao

    abstract fun chatMessageBroadcastNostrEventReceiptRelationDao(): ChatMessageBroadcastNostrEventReceiptRelationDao

    abstract fun chatMessageNostrEventRelationDao(): ChatMessageNostrEventRelationDao

    abstract fun chatRoomDao(): ChatRoomDao

    abstract fun dkgSessionDao(): DkgSessionDao

    abstract fun frostSigningSessionDao(): FrostSigningSessionDao

    abstract fun groupKeyStateDao(): GroupKeyStateDao

    abstract fun groupSignedEventDao(): GroupSignedEventDao

    abstract fun connectionDao(): ConnectionDao

    abstract fun giftWrapMessageDao(): GiftWrapMessageDao

    abstract fun giftWrapSealDao(): GiftWrapSealDao

    abstract fun giftWrapPayloadDao(): GiftWrapPayloadDao

    abstract fun inReplyToRelationDao(): InReplyToRelationDao

    abstract fun mantraDao(): MantraDao

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