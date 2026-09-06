package press.mantra.compose.database.model.types

/**
 * What a replay of a room's stored kind:445 events did.
 *
 * Nothing here is persisted -- it exists so the screen that asked for the replay
 * can say what happened rather than leaving the user to guess from the message
 * list whether anything moved.
 *
 * @param stored every kind:445 held locally for the room.
 * @param unresolved how many of those had nothing to show for them, or only a
 *  placeholder line -- the ones a replay was allowed to touch.
 * @param recovered how many of [unresolved] came out with something to show:
 *  a message, an applied commit, an entity added to the group's library.
 * @param failed how many threw while being replayed. Expected to be non-zero
 *  on a room with events from before this device joined, whose epoch secrets it
 *  never held and never will.
 */
data class MarmotReindexReport(
    val stored: Int = 0,
    val unresolved: Int = 0,
    val recovered: Int = 0,
    val failed: Int = 0,
) {
    /** Nothing was left to try, so the replay was a no-op. */
    val isNoOp: Boolean get() = unresolved == 0
}
