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
import press.mantra.compose.nostr.nip30303.tags.ArtifactMetadataTag
import press.mantra.compose.nostr.nip30303.tags.DialectIdTag


@Immutable
class ArtifactEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    val artifactMetadata = tags.firstNotNullOfOrNull(ArtifactMetadataTag::parse)

    fun url() = artifactMetadata?.url
    fun visibility() = artifactMetadata?.visibility
    fun license() = artifactMetadata?.license

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
            addUnique(
                ArtifactMetadataTag.assemble(
                    url = url,
                    visibility = visibility,
                    license = license
                )
            )
            addUnique(
                DialectIdTag.assemble(dialectId)
            )
            initializer()
        }
    }
}
