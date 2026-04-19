package ac.aux.compose.managers

import ac.aux.compose.PlatformContext
import androidx.datastore.core.Storage
import io.ktor.client.plugins.cache.storage.FileStorage

actual fun computeDatastoreStorage(
    platformContext: PlatformContext,
    dataStoreFileName: String
): Storage<Set<CredentialsManager.Credential>> {
    FileStorage(
        directory = ""
    )
    TODO("Not yet implemented")
}