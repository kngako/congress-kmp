package at.torch.compose.database.builder

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

expect object PlatformDatabaseBuilder {
    fun getDatabaseBuilder(platformContext: at.torch.compose.PlatformContext): RoomDatabase.Builder<at.torch.compose.database.TorchDatabase>

    fun getInMemoryDatabaseBuilder(platform: at.torch.compose.PlatformContext): RoomDatabase.Builder<at.torch.compose.database.TorchDatabase>
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<at.torch.compose.database.TorchDatabase>
): at.torch.compose.database.TorchDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}