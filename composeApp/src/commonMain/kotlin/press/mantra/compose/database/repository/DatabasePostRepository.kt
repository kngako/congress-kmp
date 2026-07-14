package press.mantra.compose.database.repository

class DatabasePostRepository(
    private val database: press.mantra.compose.database.TorchDatabase
): press.mantra.compose.repository.PostRepository