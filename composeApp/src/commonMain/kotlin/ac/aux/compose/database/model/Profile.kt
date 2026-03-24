package ac.aux.compose.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("nostrEventId"),
    ],
)
data class Profile(
    @PrimaryKey
    val publicKey: String,
    val nostrEventId: String,
    val userName: String? = null, // name in the nostr event...
    val displayName: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val website: String? = null,
    val about: String? = null,
    val bot: Boolean? = null,
    val pronouns: String? = null,
    val nip05: String? = null,
    val domain: String? = null,
    val lud06: String? = null,
    val lud16: String? = null,
    val twitter: String? = null
) {
    companion object {
        val DUMMAY_PROFILES = arrayOf<Profile>()
    }
}