package ac.aux.compose.database.builder

import ac.aux.compose.PlatformContext
import ac.aux.compose.database.AuxDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual object PlatformDatabaseBuilder {
    actual fun getDatabaseBuilder(platformContext: PlatformContext): RoomDatabase.Builder<AuxDatabase> {
        val dbFile = File(System.getProperty("java.io.tmpdir"), "aux.db") // TODO: Stop using tmpDir on desktop...
        return Room.databaseBuilder<AuxDatabase>(
            name = dbFile.absolutePath,
        )
    }
}