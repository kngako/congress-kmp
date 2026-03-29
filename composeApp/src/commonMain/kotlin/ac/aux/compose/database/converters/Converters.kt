package ac.aux.compose.database.converters

import ac.aux.compose.database.model.typealiases.HexArray
import ac.aux.compose.database.model.typealiases.KindArray
import androidx.room.TypeConverter
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class AuxConverters {
    val logger = Logger.withTag("AuxConverters")

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


    @TypeConverter
    fun fromHexArray(value: HexArray?): String? = try {
        return value?.let {
            Json.encodeToString(it).lowercase()
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert from HexArray $value", e)
        return null
    }

    @TypeConverter
    fun toHexArray(value: String?): HexArray? = try {
        return value?.let {
            Json.decodeFromString<HexArray>(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert to HexArray $value", e)
        return null
    }

    @TypeConverter
    fun fromKindArray(value: KindArray?): String? = try {
        return value?.let {
            Json.encodeToString(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert from KindArray $value", e)
        return null
    }

    @TypeConverter
    fun toKindArray(value: String?): KindArray? = try {
        return value?.let {
            Json.decodeFromString<KindArray>(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert to KindArray $value", e)
        return null
    }
}