package org.calibre.android.db

import androidx.room.TypeConverter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime

class Converters {
    private val mapper = jacksonObjectMapper()

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let { mapper.writeValueAsString(it) }

    @TypeConverter
    fun toStringList(value: String?): List<String> = value?.let { mapper.readValue(it) } ?: emptyList()

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
}

