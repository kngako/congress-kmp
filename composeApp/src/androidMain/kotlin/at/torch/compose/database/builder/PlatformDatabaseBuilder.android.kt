package at.torch.compose.database.builder

import at.torch.compose.PlatformContext
import at.torch.compose.database.TorchDatabase
import androidx.room3.Room
import androidx.room3.RoomDatabase

actual object PlatformDatabaseBuilder {
    actual fun getDatabaseBuilder(platformContext: PlatformContext): RoomDatabase.Builder<TorchDatabase> {
        val appContext = platformContext.applicationContext

        val dbFile = appContext.getDatabasePath("aux.db")
        return Room.databaseBuilder<TorchDatabase>(
            context = appContext,
            name = dbFile.absolutePath
        )
    }

    actual fun getInMemoryDatabaseBuilder(platform: PlatformContext): RoomDatabase.Builder<TorchDatabase> {
        return Room.inMemoryDatabaseBuilder(
            context = platform.applicationContext
        )
    }
}