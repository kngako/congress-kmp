package press.mantra.compose.nostr.nip30303

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.ProjectIdTag


/**
 * One source version is part of one project.
 *
 * Its own event rather than a line in a list the project carries. Every entity
 * in this family is its own event and its own row, and the only list-shaped kinds
 * here -- the three contributor lists -- are precisely the ones nothing applies.
 * A membership is also something a group decided, and one quorum per decision is
 * the grain this app signs at: a replaceable list would make "we added this" and
 * "we removed that" the same event.
 *
 * It carries no creator. Who moved a version into a project is a fact about
 * process, which the `FrostSigningSession` records; a membership signed in the
 * batch that founds a project has the project's creator anyway.
 */
@Immutable
class ProjectArtifactVersionEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {

    fun projectIdReference() = tags.firstNotNullOfOrNull(ProjectIdTag::parse)?.ref
    fun projectId() = projectIdReference()?.eventId

    fun artifactVersionIdReference() = tags.firstNotNullOfOrNull(ArtifactVersionIdTag::parse)?.ref
    fun artifactVersionId() = artifactVersionIdReference()?.eventId

    companion object {
        const val KIND = 30331
        const val ALT_DESCRIPTION = "ProjectArtifactVersion"

        /**
         * The source versions [project] starts life with, ready to be signed
         * with it.
         *
         * A project and its first contents go to the group as one batch, so this
         * is built from the project *after* it has been authored under the
         * group's key -- [project] is the unsigned event the session will sign,
         * which is where the id these carry comes from. Working it out anywhere
         * else would mean naming a project id before one exists: it is a hash
         * over the group's key at the room's path, and neither is resolved until
         * the proposal is made.
         *
         * They take the project's own timestamp, so the batch reads as one act
         * rather than several events that happen to share a session.
         *
         * The same shape as `ArtifactVersionEvent.initialVersionOf`, and empty
         * for a project founded with no contents -- which is allowed, unlike an
         * artifact with no version: an empty project is a name waiting for work.
         */
        fun membershipsOf(
            project: Event,
            artifactVersionIds: List<HexKey>,
        ): List<EventTemplate<ProjectArtifactVersionEvent>> =
            artifactVersionIds.map { artifactVersionId ->
                build(
                    projectId = project.id,
                    artifactVersionId = artifactVersionId,
                    createdAt = project.createdAt,
                )
            }

        fun build(
            projectId: HexKey,
            artifactVersionId: HexKey,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProjectArtifactVersionEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            addUnique(ProjectIdTag.assemble(projectId))
            addUnique(ArtifactVersionIdTag.assemble(artifactVersionId))
            initializer()
        }
    }
}
