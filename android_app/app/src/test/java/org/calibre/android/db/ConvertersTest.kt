package org.calibre.android.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun stringList_roundTrips() {
        val input = listOf("a", "b", "c")
        val encoded = converters.fromStringList(input)
        val decoded = converters.toStringList(encoded)
        assertEquals(input, decoded)
    }

    @Test
    fun stringList_nullBecomesEmpty() {
        assertEquals(emptyList<String>(), converters.toStringList(null))
    }

    @Test
    fun localDateTime_roundTrips() {
        val dt = LocalDateTime.of(2025, 12, 14, 10, 11, 12)
        val encoded = converters.fromLocalDateTime(dt)
        val decoded = converters.toLocalDateTime(encoded)
        assertEquals(dt, decoded)
    }

    @Test
    fun localDateTime_nullRoundTrips() {
        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
    }
}

