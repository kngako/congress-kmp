package press.mantra.compose.text

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent

/**
 * An event a group is being asked to sign, said in words.
 *
 * Shown as the thing rather than as an event: a member deciding whether to sign
 * is deciding about a dialect or an artifact, and "kind 30304" answers a
 * question nobody asked. The raw kind stays for anything not recognised, since
 * refusing to describe an event is better than describing it wrongly.
 *
 * Not a composable, and deliberately: the same words appear on the signing
 * screen and in the room's list of proposals, and two copies of this mapping
 * would drift into two different names for one thing.
 */
object ProposedEvent {
    /**
     * [label] is what kind of thing it is, [detail] which one. Both are shown
     * together, so neither repeats the other.
     */
    data class Summary(val label: String, val detail: String)

    /**
     * [chatRoomId] is the room the proposal is in, where one is known.
     *
     * Only one branch reads it, and only to leave something out: a project owned
     * by the room it is in is the ordinary case, and saying "owned by this group"
     * on every project trains a member not to read the line that matters when
     * somebody else is on it. Null simply says everything.
     *
     * [resolve] turns a public key or an event id into something a person can
     * read, or null when it cannot. It is synchronous on purpose -- this is
     * called from composables as well as view models -- so a caller with a
     * database behind it resolves what it needs first and passes a lookup into
     * the map.
     *
     * The default resolves nothing, which is honest rather than ideal. A
     * membership event carries two 64-character ids and nothing else, so without
     * a resolver it can only be described by a truncated hash. That is tolerable
     * inside the batch that founds a project, where the project's own line names
     * what is happening and the memberships read as its continuation -- and in
     * that batch the project id is unresolvable anyway, since nothing has
     * applied it yet. It is not tolerable for a membership proposed on its own,
     * which is the whole of what a member is being asked to agree to.
     */
    fun summarize(
        event: Event,
        chatRoomId: String? = null,
        resolve: (HexKey) -> String? = { null },
    ): Summary = when (event.kind) {
        DialectEvent.KIND -> Summary(
            label = "New dialect",
            detail = DialectEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { dialect ->
                listOfNotNull(dialect.name(), dialect.country(), dialect.language())
                    .joinToString(" · ")
            }
        )

        ArtifactEvent.KIND -> Summary(
            label = "New artifact",
            detail = ArtifactEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { artifact ->
                // The url is the substance of an artifact -- signing one is putting
                // the group's name to what it points at -- so it goes next to the
                // name rather than being left for the detail screen afterwards.
                listOfNotNull(event.content, artifact.versionLabel(), artifact.url())
                    .joinToString(" · ")
            }
        )

        // Signed alongside the artifact it belongs to rather than on its own,
        // so this is almost always read as the second line of a batch of two.
        // Named after the artifact rather than the label, because the label is
        // the whole of the content and would otherwise be said twice.
        ArtifactVersionEvent.KIND -> Summary(
            label = "Version of the artifact",
            detail = event.content.ifBlank { "Unlabelled" }
        )

        ChapterEvent.KIND -> Summary(
            label = "New chapter",
            detail = ChapterEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { chapter ->
                // The text is the substance of a chapter -- signing one is putting
                // the group's name to what everybody will translate from -- but it
                // is a whole chapter, so its size stands in for it here. The chunk
                // count says how many of the batch's other items came out of this
                // text, which is the rest of what is being signed.
                val chunkCount = chapter.originalText()?.let { Markdown.splitParagraphs(it).size }

                listOfNotNull(
                    chapter.name(),
                    chapter.wordCount()?.let { "$it words" },
                    chunkCount?.let { "$it ${if (it == 1) "chunk" else "chunks"}" }
                ).joinToString(" · ")
            }
        )

        TranslationArtifactVersionEvent.KIND -> Summary(
            label = "New translation",
            detail = TranslationArtifactVersionEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { translation ->
                // Named after the dialect it is into, which is the whole of what is
                // being decided: everything else in the batch follows from it.
                listOfNotNull(translation.name(), translation.visibility(), translation.license())
                    .joinToString(" · ")
            }
        )

        TranslationChapterEvent.KIND -> Summary(
            label = "Chapter of the translation",
            detail = TranslationChapterEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { chapter ->
                // A translation chapter is a place for translated chunks to hang
                // off, so there is nothing in it to read -- only where in the work
                // it sits. Counted from one, the way the chapter list reads.
                chapter.index()?.let { "Chapter ${it + 1}" } ?: "Position unknown"
            }
        )

        TranslationChunkEvent.KIND -> Summary(
            label = "Translated passage",
            detail = TranslationChunkEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { chunk ->
                // The translation itself is the whole of what is being decided --
                // a member signing this is agreeing that these words say what the
                // original said -- so it is the detail rather than a count of it.
                // Where in the chapter it sits comes first, since that is what
                // says which passage to read it against.
                listOfNotNull(
                    chunk.index()?.let { "Passage ${it + 1}" },
                    event.content
                ).joinToString(" · ")
            }
        )

        ProjectEvent.KIND -> Summary(
            label = "New project",
            detail = ProjectEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { project ->
                listOfNotNull(
                    project.name(),
                    event.content.takeIf { it.isNotBlank() },
                    // Shown because signing this is what makes it the group's
                    // statement that X started it. A member who cannot see the
                    // claim cannot be said to have agreed to it -- and nothing
                    // else checks it, since the creator signs nothing here.
                    project.creator()?.let { "started by ${describe(it, resolve)}" },
                    // Only the owners who are not this room. See [chatRoomId].
                    project.owners()
                        .filter { it != chatRoomId }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(", ") { describe(it, resolve) }
                        ?.let { "owned by $it" },
                ).joinToString(" · ")
            }
        )

        ProjectArtifactVersionEvent.KIND -> Summary(
            label = "A source version joins the project",
            detail = ProjectArtifactVersionEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { membership ->
                // The version first: in a founding batch the project is being
                // signed two lines up and its id resolves to nothing, because
                // nothing has applied it yet.
                listOfNotNull(
                    membership.artifactVersionId()?.let { describe(it, resolve) },
                    membership.projectId()?.let { resolve(it) }?.let { "to $it" },
                ).joinToString(" · ").ifBlank { "Unknown version" }
            }
        )

        ProjectTranslationArtifactVersionEvent.KIND -> Summary(
            label = "A translation joins the project",
            detail = ProjectTranslationArtifactVersionEvent(
                event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
            ).let { membership ->
                listOfNotNull(
                    membership.translationArtifactVersionId()?.let { describe(it, resolve) },
                    membership.projectId()?.let { resolve(it) }?.let { "to $it" },
                ).joinToString(" · ").ifBlank { "Unknown translation" }
            }
        )

        // The one thing a group signs that is about the group rather than about
        // its work, and the only one a member sees before the room has done
        // anything. Shown as the path and the ceremony rather than as the key:
        // the key is the room's own id, which the member is already looking at.
        GroupKeyStateEvent.KIND -> Summary(
            label = "This group's shared key",
            detail = listOfNotNull(
                GroupKeyStateEvent.parsePath(event.tags)?.let(SharedKeyDerivation::formatPath),
                GroupKeyStateEvent.parseDkgSessionId(event.tags)?.take(12)
            ).joinToString(" · ")
        )

        // The other thing a group signs that is about groups rather than about
        // work, and the only one where the *proposal* is the last chance to
        // check. A parent admin approving this is putting the group's signature
        // to a room id produced by a ceremony most of them were not in, so the
        // summary is who it is for and what it is called -- the two things they
        // can actually weigh. The id is shown by the screen, and their device has
        // already checked it derives from the key the certificate names.
        SubgroupBirthCertificateEvent.KIND -> Summary(
            label = "A new subgroup of this group",
            detail = listOfNotNull(
                SubgroupBirthCertificateEvent.parseName(event.tags),
                SubgroupBirthCertificateEvent.parseAdminPublicKeys(event.tags)
                    .size
                    .takeIf { it > 0 }
                    ?.let { "run by $it members" }
            ).joinToString(" · ")
        )

        else -> Summary(label = "Event of kind ${event.kind}", detail = event.content)
    }

    /**
     * [id] as a name if [resolve] has one, and as a short hash otherwise.
     *
     * Twelve characters because that is what the rest of the signing screens
     * truncate to. A short hash is a poor thing to put in front of somebody
     * deciding whether to sign, and saying so is the point: see [summarize].
     */
    private fun describe(id: HexKey, resolve: (HexKey) -> String?): String =
        resolve(id) ?: id.take(12)
}
