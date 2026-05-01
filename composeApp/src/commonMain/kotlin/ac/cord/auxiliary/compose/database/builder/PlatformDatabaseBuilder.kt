package ac.cord.auxiliary.compose.database.builder

import ac.cord.auxiliary.compose.PlatformContext
import ac.cord.auxiliary.compose.database.AuxDatabase
import androidx.room3.RoomDatabase
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