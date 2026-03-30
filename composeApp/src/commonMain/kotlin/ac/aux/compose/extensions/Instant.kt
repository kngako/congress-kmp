package ac.aux.compose.extensions

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Instant.toFormattedTimeAndDateString(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).format(
        LocalDateTime.Format {
            year()
            char('-')
            monthNumber()
            char('-')
            day()
            char(' ')
            hour()
            char(':')
            minute()
        }
    )
}