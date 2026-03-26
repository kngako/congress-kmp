package ac.aux.compose.database.repository

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalProfile
import ac.aux.compose.repository.NostrRepository
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

class DatabaseNostrRepository(
    private val database: AuxDatabase
): NostrRepository {
    override suspend fun observeProfile(publicKey: HexKey): Flow<LocalProfile?> {
        return database.profileDao().observeProfile(publicKey)
    }

    override suspend fun createNewProfile(
        publicKey: HexKey,
        name: String,
        biography: String,
        onError: () -> Unit,
        onUnsignProfile: (UnsignedNostrEvent) -> Unit,
        onUnbroadcastedProfile: (NostrEvent) -> Unit,
        onProfileReady: (Profile) -> Unit
    ) {
        val profileEventTemplate = MetadataEvent.createNew(
            name = name,
            about = biography,
        )

        val unsignedNostrEvent = saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = profileEventTemplate.kind,
                createdAt = Instant.fromEpochMilliseconds(profileEventTemplate.createdAt),
                tags = profileEventTemplate.tags,
                content = profileEventTemplate.content
            )
        )

        if (unsignedNostrEvent != null) {
            onUnsignProfile.invoke(unsignedNostrEvent)

            delay(7_500) // Seeming busy is very important...

            // TODO: Call and collect kind 0 nostrEvent with pubKey
            onUnbroadcastedProfile.invoke(
                NostrEvent(
                    id = "",
                    pubKey = "",
                    kind = 0,
                    content = "Something here",
                    tags = emptyArray(),
                    sig = ""
                )
            )

            delay(4_500) // Seeming busy is very important...

            onProfileReady.invoke(
                Profile(
                    publicKey = "",
                    nostrEventId = "",
                    displayName = name,
                    about = biography,
                )
            )
        } else {
            onError.invoke()
        }
    }

    override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? = try {
        delay(2_100) // Just to make it look like we are doing serious work...
        unsignedNostrEvent.copy(
            id = database.unsignedNostrEventDao().upsert(unsignedNostrEvent)
        )
    } catch (e: Throwable) {
        null
    }

    override suspend fun wipeDatabase() {
        database.wipeData()
    }

}