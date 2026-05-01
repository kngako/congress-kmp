package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalPost
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface MentionDao {

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(mention: Mention)

    @Upsert
    suspend fun upsert(mention: Mention)
}