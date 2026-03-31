package ac.aux.compose.database.builder

import ac.aux.compose.PlatformContext
import ac.aux.compose.database.AuxDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

expect object PlatformDatabaseBuilder {
    fun getDatabaseBuilder(platformContext: PlatformContext): RoomDatabase.Builder<AuxDatabase>

    fun getInMemoryDatabaseBuilder(platform: PlatformContext): RoomDatabase.Builder<AuxDatabase>
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AuxDatabase>
): AuxDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}