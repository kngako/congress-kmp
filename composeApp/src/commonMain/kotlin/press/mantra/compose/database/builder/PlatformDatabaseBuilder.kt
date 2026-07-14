package press.mantra.compose.database.builder

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

expect object PlatformDatabaseBuilder {
    fun getDatabaseBuilder(platformContext: press.mantra.compose.PlatformContext): RoomDatabase.Builder<press.mantra.compose.database.TorchDatabase>

    fun getInMemoryDatabaseBuilder(platform: press.mantra.compose.PlatformContext): RoomDatabase.Builder<press.mantra.compose.database.TorchDatabase>
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<press.mantra.compose.database.TorchDatabase>
): press.mantra.compose.database.TorchDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}