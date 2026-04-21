package ac.cord.auxiliary.compose.database

import androidx.room.RoomDatabaseConstructor

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object AuxDatabaseConstructor: RoomDatabaseConstructor<AuxDatabase> {

    override fun initialize(): AuxDatabase
}