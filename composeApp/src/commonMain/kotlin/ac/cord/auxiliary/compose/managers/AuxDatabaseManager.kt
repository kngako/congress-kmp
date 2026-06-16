package ac.cord.auxiliary.compose.managers

import ac.cord.auxiliary.compose.AuxGlobal
import ac.cord.auxiliary.compose.database.builder.PlatformDatabaseBuilder
import ac.cord.auxiliary.compose.database.builder.getRoomDatabase

class AuxDatabaseManager(
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