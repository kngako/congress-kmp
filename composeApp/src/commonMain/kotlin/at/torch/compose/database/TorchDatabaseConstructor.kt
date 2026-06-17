package at.torch.compose.database

import androidx.room3.RoomDatabaseConstructor

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object TorchDatabaseConstructor: RoomDatabaseConstructor<TorchDatabase> {

    override fun initialize(): TorchDatabase
}