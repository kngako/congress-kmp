package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import androidx.room3.Dao
import androidx.room3.Transaction

@Dao
abstract class NostrNip17Dao(
    private val database: AuxDatabase
) {

    @Transaction
    open suspend fun getOrCreateChatRoom(publicKey: String): LocalChatRoom? {

        return null
    }
}