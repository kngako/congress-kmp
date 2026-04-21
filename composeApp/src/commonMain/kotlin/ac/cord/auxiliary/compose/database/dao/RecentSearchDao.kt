package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.RecentSearch
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentSearchDao {
    @Query("SELECT * FROM RecentSearch WHERE `query` = :query")
    fun observeRecentSearchByQuery(query: String): Flow<RecentSearch?>

    @Query("SELECT * FROM RecentSearch ORDER BY createdAt DESC LIMIT :limit ")
    fun getAllRecentSearches(limit: Int = 21): List<RecentSearch>

    @Delete
    fun deleteRecentSearch(recentSearch: RecentSearch)

    @Upsert
    suspend fun upsert(recentSearch: RecentSearch)
}