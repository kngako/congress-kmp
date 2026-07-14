package press.mantra.compose.database

import androidx.room3.RoomDatabaseConstructor

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object MantraDatabaseConstructor: RoomDatabaseConstructor<MantraDatabase> {

    override fun initialize(): MantraDatabase
}