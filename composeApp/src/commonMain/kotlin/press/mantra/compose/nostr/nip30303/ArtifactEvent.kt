package press.mantra.compose.nostr.nip30303

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip22Comments.RootScope
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.nostr.nip30303.tags.DialectIdTag
import press.mantra.compose.nostr.nip30303.tags.LicenseTag
import press.mantra.compose.nostr.nip30303.tags.UrlTag
import press.mantra.compose.nostr.nip30303.tags.VisibilityTag


@Immutable
class ArtifactEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    fun url() = tags.firstNotNullOfOrNull(UrlTag::parse)?.url
    fun visibility() = tags.firstNotNullOfOrNull(VisibilityTag::parse)?.visibility
    fun license() = tags.firstNotNullOfOrNull(LicenseTag::parse)?.license

    fun dialectIdReference() = tags.firstNotNullOfOrNull(DialectIdTag::parse)?.ref
    fun dialectId() = dialectIdReference()?.eventId

    companion object {
        const val KIND = 30300
        const val ALT_DESCRIPTION = "Artifact"

        fun build(
            name: String,
            url: String,
            visibility: String,
            license: String,
            dialectId: String,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ArtifactEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, name, createdAt) {
            alt(ALT_DESCRIPTION)
            addUnique(UrlTag.assemble(url))
            addUnique(VisibilityTag.assemble(visibility))
            addUnique(LicenseTag.assemble(license))
            addUnique(
                DialectIdTag.assemble(dialectId)
            )
            initializer()
        }
    }
}
