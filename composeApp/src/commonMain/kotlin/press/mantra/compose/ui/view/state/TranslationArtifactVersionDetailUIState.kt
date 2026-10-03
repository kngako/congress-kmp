package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationChapter
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

/** A translation chapter with how many of its chunks have been translated. */
data class TranslationChapterProgress(
    val chapter: MantraTranslationChapter,
    val totalChunks: Int,
    val translatedChunks: Int,
)

sealed interface TranslationArtifactVersionDetailUIState {
    data class Loaded(
        val translation: MantraTranslationArtifactVersion,
        val chapters: List<TranslationChapterProgress> = emptyList(),

        /**
         * The room this translation belongs to, which is who would sign the
         * chapters it is missing. Null when it cannot be read, which is a
         * translation nothing can be proposed for.
         */
        val localChatRoom: LocalChatRoom? = null,

        /**
         * Chapters of the source version this translation has nothing for.
         *
         * A translation covers the chapters that existed when it was proposed,
         * and a chapter signed afterwards is scaffolded into it by a session of
         * that chapter's own -- which the two can miss, by being proposed at the
         * same moment or by one of them not reaching a quorum. This is what is
         * left over when they do, and it is shown rather than inferred because
         * a chapter nobody can translate is invisible from the translation.
         */
        val missingChapters: List<MantraChapter> = emptyList(),

        /** Whether the group holds a shared key, without which nothing can be proposed. */
        val canSign: Boolean = false,

        /**
         * Whether this device could open a subgroup for this translation at all.
         *
         * [canSign] and a Marmot room, the same pair
         * `ChatRoomDetailUIState.canAddSubgroup` states.
         */
        val canAddSubgroup: Boolean = false,
        /**
         * The rooms this group has made for this translation.
         *
         * The other direction of a birth certificate, and the reason the
         * certificate carries an origin at all. Empty says only that this device
         * holds no certificate naming it -- a room made for it by a group this
         * device is not in is invisible here, as everywhere else.
         */
        val subgroups: List<SubgroupManager.Subgroup> = emptyList(),

        /**
         * Key ceremonies already under way for this translation.
         *
         * Shown rather than refused. Two admins opening this screen at the same
         * time both see no room and both start one, which is the race the whole
         * pair exists for -- the screen that invites the act is the screen that
         * cannot otherwise see the act in progress. A group may still want two
         * rooms for one translation, and that is the group's business.
         */
        val subgroupCeremonies: List<DkgSession> = emptyList(),

    ): TranslationArtifactVersionDetailUIState

    data class Error(
        val message: String
    ): TranslationArtifactVersionDetailUIState

    data object Loading: TranslationArtifactVersionDetailUIState
}
