package ac.aux.compose.database

import ac.aux.compose.database.converters.Converters
import ac.aux.compose.database.dao.BroadcastNostrEventReceiptDao
import ac.aux.compose.database.dao.BroadcastNostrEventRequestDao
import ac.aux.compose.database.dao.NostrDao
import ac.aux.compose.database.dao.NostrEventDao
import ac.aux.compose.database.dao.PostDao
import ac.aux.compose.database.dao.ProfileDao
import ac.aux.compose.database.dao.ReactionDao
import ac.aux.compose.database.dao.RepostDao
import ac.aux.compose.database.dao.ZapDao
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.Reaction
import ac.aux.compose.database.model.Repost
import ac.aux.compose.database.model.Zap
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NostrEvent::class,
        Post::class,
        Profile::class,
        Reaction::class,
        Repost::class,
        Zap::class
    ],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AuxDatabase: RoomDatabase() {
    abstract fun broadcastNostrEventReceiptDao(): BroadcastNostrEventReceiptDao
    abstract fun broadcastNostrEventRequestDao(): BroadcastNostrEventRequestDao
    abstract fun nostrDao(): NostrDao

    abstract fun nostrEventDao(): NostrEventDao
    abstract fun postDao(): PostDao
    abstract fun profileDao(): ProfileDao
    abstract fun reactionDao(): ReactionDao
    abstract fun repostDao(): RepostDao
    abstract fun zapDao(): ZapDao
}