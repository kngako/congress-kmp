package ac.aux.compose.database.converters

import androidx.room.TypeConverter
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromTagArray(value: TagArray?): String? {
        // Returns null if the value is null, otherwise serializes to JSON string
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toTagArray(value: String?): TagArray? {
        // Decodes the JSON string back into the nested array structure
        return value?.let { Json.decodeFromString<TagArray>(it) }
    }
}