package ac.cord.auxiliary.compose.database.converters

import ac.cord.auxiliary.compose.database.model.typealiases.SynchronizationFilterArray
import ac.cord.auxiliary.compose.database.model.typealiases.HexArray
import ac.cord.auxiliary.compose.database.model.typealiases.KindArray
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import androidx.room3.TypeConverter
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class AuxConverters {
    val logger = Logger.withTag("AuxConverters")

    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochSeconds(it) }
    }

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.epochSeconds
    }

    @TypeConverter
    fun fromTagArray(value: Array<Array<String>>?): String? {
        // Returns null if the value is null, otherwise serializes to JSON string
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toTagArray(value: String?): Array<Array<String>>? {
        // Decodes the JSON string back into the nested array structure
        return value?.let { Json.decodeFromString<Array<Array<String>>>(it) }
    }


    @TypeConverter
    fun fromHexArray(value: Array<String>?): String? = try {
        return value?.let {
            Json.encodeToString(it).lowercase()
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert from HexArray $value", e)
        return null
    }

    @TypeConverter
    fun toHexArray(value: String?): Array<String>? = try {
        return value?.let {
            Json.decodeFromString<Array<String>>(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert to HexArray $value", e)
        return null
    }

    @TypeConverter
    fun fromKindArray(value: Array<Int>?): String? = try {
        return value?.let {
            Json.encodeToString(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert from KindArray $value", e)
        return null
    }

    @TypeConverter
    fun toKindArray(value: String?): Array<Int>? = try {
        return value?.let {
            Json.decodeFromString<Array<Int>>(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert to KindArray $value", e)
        return null
    }

    @TypeConverter
    fun fromFilterArray(value: Array<SynchronizationFilter>?): String? = try {
        return value?.let {
            Json.encodeToString(value)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert from FilterArray $value", e)
        return null
    }

    @TypeConverter
    fun toFilterArray(value: String?): Array<SynchronizationFilter>? = try {
        return value?.let {
            Json.decodeFromString<Array<SynchronizationFilter>>(it)
        }
    } catch (e: Throwable) {
        logger.e("Failed to convert to FilterArray $value", e)
        return null
    }
}