package ac.aux.compose.managers

import ac.aux.compose.PlatformContext
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun computeDatastoreStorage(
    platformContext: PlatformContext,
    dataStoreFileName: String
): Storage<Set<CredentialsManager.Credential>> {
    return OkioStorage(
        fileSystem = FileSystem.SYSTEM,
        serializer = OkioSerializer<Set<CredentialsManager.Credential>>,
        producePath = {
            val documentDirectory: NSURL? = NSFileManager.defaultManager.URLsForDirectory(
                directory = NSDocumentDirectory,
                inDomains = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null
            )

            (requireNotNull(documentDirectory).path + "/$dataStoreFileName").toPath()
        }
    )
}