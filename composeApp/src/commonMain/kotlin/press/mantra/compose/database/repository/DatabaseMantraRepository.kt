package press.mantra.compose.database.repository

import kotlinx.coroutines.CoroutineScope
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.repository.MantraRepository

class DatabaseMantraRepository(
    val database: MantraDatabase,
    val scope: CoroutineScope
): MantraRepository {
    override fun addArtifact(name: String, url: String, versionLabel: String) {
        TODO("Implement add artifact")
    }
}