package at.torch.compose.database.repository

class DatabasePostRepository(
    private val database: at.torch.compose.database.TorchDatabase
): at.torch.compose.repository.PostRepository