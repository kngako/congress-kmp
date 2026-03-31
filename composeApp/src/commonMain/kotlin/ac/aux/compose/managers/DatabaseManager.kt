package ac.aux.compose.managers

import ac.aux.compose.AuxGlobal
import ac.aux.compose.database.AuxDatabaseConstructor
import ac.aux.compose.database.builder.PlatformDatabaseBuilder
import ac.aux.compose.database.builder.getRoomDatabase
import androidx.room.Room

class DatabaseManager(
    auxGlobal: AuxGlobal
) {
    val auxDatabase by lazy {
        getRoomDatabase(
            PlatformDatabaseBuilder.getDatabaseBuilder(
                auxGlobal.platformContext
            )
        )
    }

    /**
     * Dynamic data like user feeds and the likes will be in a in-memory db.
     *
     * We may also decide to just use the normal DB and manage what's saved better...
     */
    val inMemoryAuxDatabase by lazy {
        getRoomDatabase(
            PlatformDatabaseBuilder.getInMemoryDatabaseBuilder(
                auxGlobal.platformContext
            )
        )
    }
}