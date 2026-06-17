package at.torch.compose.extensions

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun Instant.toFormattedTimeAndDateString(): String {
    return toLocalDateTime(TimeZone.currentSystemDefault()).format(
        LocalDateTime.Format {


            hour()
            char(':')
            minute()
            char(' ')
            day()
            char(' ')
            monthName(
                MonthNames.ENGLISH_ABBREVIATED
            )
            char(' ')
            year()
        }
    )
}