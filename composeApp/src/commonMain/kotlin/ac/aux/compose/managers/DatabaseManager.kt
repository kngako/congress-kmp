package ac.aux.compose.managers

import ac.aux.compose.AuxGlobal
import ac.aux.compose.database.AuxDatabaseConstructor
import ac.aux.compose.database.builder.PlatformDatabaseBuilder
import ac.aux.compose.database.builder.getRoomDatabase

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
}