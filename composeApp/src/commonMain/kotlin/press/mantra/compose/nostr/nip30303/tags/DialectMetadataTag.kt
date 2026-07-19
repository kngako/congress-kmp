package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class DialectMetadataTag(
    val name: String,
    val country: String,
    val language: String,
) {
    fun toTagArray() = assemble(
        name = name,
        country = country,
        language = language
    )

    companion object {
        const val TAG_NAME = "dialect"

        fun parse(tag: Array<String>): DialectMetadataTag? {
            ensure(tag.has(3)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            return DialectMetadataTag(
                name = tag[1],
                country = tag[2],
                language = tag[3],
            )
        }

        fun assemble(
            name: String,
            country: String,
            language: String,
        ): Array<String> = arrayOf(
            TAG_NAME,
            name,
            country,
            language
        )

        fun assemble(dialectMetadataTag: DialectMetadataTag) = assemble(
            name = dialectMetadataTag.name,
            country = dialectMetadataTag.country,
            language = dialectMetadataTag.language
        )
    }
}