package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.RecentSearch
import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
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