package press.mantra.compose.database.builder

import androidx.room3.Room
import androidx.room3.RoomDatabase
import press.mantra.compose.PlatformContext
import press.mantra.compose.database.TorchDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual object PlatformDatabaseBuilder {
    actual fun getDatabaseBuilder(platformContext: PlatformContext): RoomDatabase.Builder<TorchDatabase> {
        val dbFilePath = documentDirectory() + "/aux.db"
        return Room.databaseBuilder<TorchDatabase>(
            name = dbFilePath,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun documentDirectory(): String {
        val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = false,
            error = null,
        )
        return requireNotNull(documentDirectory?.path)
    }

    actual fun getInMemoryDatabaseBuilder(platform: PlatformContext): RoomDatabase.Builder<TorchDatabase> {
        return Room.inMemoryDatabaseBuilder()
    }

}