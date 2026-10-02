package press.mantra.compose.text

/**
 * A byte count as the number part of "19.9 KiB".
 *
 * The unit itself is a string resource, so this returns only the figure.
 *
 * One decimal place, by integer arithmetic rather than through a float format.
 * Two reasons, and the first is the one that matters: the figures this renders
 * sit next to each other -- what a chapter weighs, and what the group can carry
 * -- and whole kibibytes would print a 20,473-byte chapter and its 21,226-byte
 * limit as the same "19 KiB", which reads as a form refusing something it says
 * is within the limit. The second is that there is no common-code decimal
 * formatter to reach for, and the three platforms' own would each punctuate it
 * differently.
 *
 * Truncating rather than rounding, for the same reason: a figure shown as under
 * the limit has to be under it.
 */
internal fun kibibytes(bytes: Int): String {
    val whole = bytes / 1024
    val tenths = (bytes % 1024) * 10 / 1024
    return "$whole.$tenths"
}
