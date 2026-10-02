package press.mantra.compose.nostr.nip30303

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.nostr.nip30303.tags.ProjectIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionIdTag


/**
 * One translation is part of one project.
 *
 * The other half of [ProjectArtifactVersionEvent], and the same shape for the
 * same reasons -- see there.
 */
@Immutable
class ProjectTranslationArtifactVersionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {

    fun projectIdReference() = tags.firstNotNullOfOrNull(ProjectIdTag::parse)?.ref
    fun projectId() = projectIdReference()?.eventId

    fun translationArtifactVersionIdReference() =
        tags.firstNotNullOfOrNull(TranslationArtifactVersionIdTag::parse)?.ref
    fun translationArtifactVersionId() = translationArtifactVersionIdReference()?.eventId

    companion object {
        const val KIND = 30332
        const val ALT_DESCRIPTION = "ProjectTranslationArtifactVersion"

        /** The translations [project] starts life with. See the sibling's note. */
        fun membershipsOf(
            project: Event,
            translationArtifactVersionIds: List<HexKey>,
        ): List<EventTemplate<ProjectTranslationArtifactVersionEvent>> =
            translationArtifactVersionIds.map { translationArtifactVersionId ->
                build(
                    projectId = project.id,
                    translationArtifactVersionId = translationArtifactVersionId,
                    createdAt = project.createdAt,
                )
            }

        fun build(
            projectId: HexKey,
            translationArtifactVersionId: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProjectTranslationArtifactVersionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            addUnique(ProjectIdTag.assemble(projectId))
            addUnique(TranslationArtifactVersionIdTag.assemble(translationArtifactVersionId))
            initializer()
        }
    }
}
