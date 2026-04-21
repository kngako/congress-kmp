package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.repository.PostRepository

class DatabasePostRepository(
    private val database: AuxDatabase
): PostRepository {
    
}