package press.mantra.compose.database.builder

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import press.mantra.compose.database.migrations.MIGRATION_3_4
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
        // Everything else Room generates itself; this one rewrites rows rather than
        // changing shape, which an AutoMigration cannot express.
        .addMigrations(MIGRATION_3_4)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}