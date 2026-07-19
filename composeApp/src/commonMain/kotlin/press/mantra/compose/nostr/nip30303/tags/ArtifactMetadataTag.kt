package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class ArtifactMetadataTag(
    val url: String,
    val visibility: String,
    val license: String,
) {
    fun toTagArray() = assemble(
        url = url,
        visibility = visibility,
        license = license
    )

    companion object {
        const val TAG_NAME = "artifact"

        fun parse(tag: Array<String>): ArtifactMetadataTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            return ArtifactMetadataTag(
                url = tag[1],
                visibility = tag[2],
                license = tag[3],
            )
        }

        fun assemble(
            url: String,
            visibility: String,
            license: String,
        ): Array<String> = arrayOf(
            TAG_NAME,
            url,
            visibility,
            license
        )

        fun assemble(artifactMetadataTag: ArtifactMetadataTag) = assemble(
            url = artifactMetadataTag.url,
            visibility = artifactMetadataTag.visibility,
            license = artifactMetadataTag.license
        )
    }
}