package press.mantra.compose.database.builder

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import press.mantra.compose.database.migrations.MIGRATION_3_4
import press.mantra.compose.database.migrations.MIGRATION_9_10
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
        // Everything else Room generates itself. These two move data rather than
        // only changing shape, which an AutoMigration cannot express: 3->4 rewrites
        // chat rows, and 9->10 copies a session's per-event columns onto the items
        // table before dropping them.
        .addMigrations(MIGRATION_3_4, MIGRATION_9_10)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}