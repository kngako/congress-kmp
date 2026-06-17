package at.torch.compose.managers

import at.torch.compose.database.builder.getRoomDatabase

class AuxDatabaseManager(
    torchGlobal: at.torch.compose.TorchGlobal
) {
    val auxDatabase by lazy {
        _root_ide_package_.ac.cord.compose.database.builder.getRoomDatabase(
            _root_ide_package_.at.torch.compose.database.builder.PlatformDatabaseBuilder.getDatabaseBuilder(
                torchGlobal.platformContext
            )
        )
    }

    /**
     * Dynamic data like user feeds and the likes will be in a in-memory db.
     *
     * We may also decide to just use the normal DB and manage what's saved better...
     */
    val inMemoryAuxDatabase by lazy {
        _root_ide_package_.ac.cord.compose.database.builder.getRoomDatabase(
            _root_ide_package_.at.torch.compose.database.builder.PlatformDatabaseBuilder.getInMemoryDatabaseBuilder(
                torchGlobal.platformContext
            )
        )
    }
}