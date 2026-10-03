package press.mantra.compose.database.builder

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import press.mantra.compose.database.migrations.MIGRATION_3_4
import press.mantra.compose.database.migrations.MIGRATION_9_10
import press.mantra.compose.database.migrations.MIGRATION_13_14
import press.mantra.compose.database.migrations.MIGRATION_14_15
import press.mantra.compose.database.migrations.MIGRATION_20_21
import press.mantra.compose.database.migrations.MIGRATION_23_24
import press.mantra.compose.database.migrations.MIGRATION_24_25
import press.mantra.compose.database.migrations.MIGRATION_25_26
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

expect object PlatformDatabaseBuilder {
    fun getDatabaseBuilder(platformContext: press.mantra.compose.PlatformContext): RoomDatabase.Builder<press.mantra.compose.database.MantraDatabase>

    fun getInMemoryDatabaseBuilder(platform: press.mantra.compose.PlatformContext): RoomDatabase.Builder<press.mantra.compose.database.MantraDatabase>
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<press.mantra.compose.database.MantraDatabase>
): press.mantra.compose.database.MantraDatabase {
    return builder
        // Everything else Room generates itself. These eight move data rather than
        // only changing shape, which an AutoMigration cannot express: 3->4 rewrites
        // chat rows, 9->10 copies a session's per-event columns onto the items
        // table before dropping them, 13->14 renames a column and rewrites the
        // chat rows that named it the old way, 14->15 adds a column and deletes
        // the chat rows written for messages from before this device joined, and
        // 20->21 copies each room's single parentage onto the table that can hold
        // several before dropping the columns it came from, and 21->22 rebuilds the
        // two ritual tables to key them by profile, and 22->23 splits a profile's
        // membership of a group off the group itself, and 24->25 puts the sender's
        // identity on a transcript line in place of a boolean about one reader.
        .addMigrations(
            MIGRATION_3_4,
            MIGRATION_9_10,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_20_21,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26,
        )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}