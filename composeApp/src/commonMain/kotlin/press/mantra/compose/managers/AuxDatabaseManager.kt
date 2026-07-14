package press.mantra.compose.managers

class AuxDatabaseManager(
    mantraGlobal: press.mantra.compose.MantraGlobal
) {
    val auxDatabase by lazy {
        _root_ide_package_.press.mantra.compose.database.builder.getRoomDatabase(
            press.mantra.compose.database.builder.PlatformDatabaseBuilder.getDatabaseBuilder(
                mantraGlobal.platformContext
            )
        )
    }

    /**
     * Dynamic data like user feeds and the likes will be in a in-memory db.
     *
     * We may also decide to just use the normal DB and manage what's saved better...
     */
    val inMemoryAuxDatabase by lazy {
        _root_ide_package_.press.mantra.compose.database.builder.getRoomDatabase(
            press.mantra.compose.database.builder.PlatformDatabaseBuilder.getInMemoryDatabaseBuilder(
                mantraGlobal.platformContext
            )
        )
    }
}