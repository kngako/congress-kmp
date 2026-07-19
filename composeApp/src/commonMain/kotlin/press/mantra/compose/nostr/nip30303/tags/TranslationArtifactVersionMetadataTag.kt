package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class TranslationArtifactVersionMetadataTag(
    val name: String,
    val visibility: String,
    val license: String,
) {
    fun toTagArray() = assemble(
        name = name,
        visibility = visibility,
        license = license
    )

    companion object {
        const val TAG_NAME = "artifact"

        fun parse(tag: Array<String>): TranslationArtifactVersionMetadataTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            return TranslationArtifactVersionMetadataTag(
                name = tag[1],
                visibility = tag[2],
                license = tag[3],
            )
        }

        fun assemble(
            name: String,
            visibility: String,
            license: String,
        ): Array<String> = arrayOf(
            TAG_NAME,
            name,
            visibility,
            license
        )

        fun assemble(translationArtifactVersionMetadataTag: TranslationArtifactVersionMetadataTag) = assemble(
            name = translationArtifactVersionMetadataTag.name,
            visibility = translationArtifactVersionMetadataTag.visibility,
            license = translationArtifactVersionMetadataTag.license
        )
    }
}