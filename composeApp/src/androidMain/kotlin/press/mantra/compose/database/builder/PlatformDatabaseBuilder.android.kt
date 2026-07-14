package press.mantra.compose.database.builder

import androidx.room3.Room
import androidx.room3.RoomDatabase
import press.mantra.compose.PlatformContext
import press.mantra.compose.database.TorchDatabase

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