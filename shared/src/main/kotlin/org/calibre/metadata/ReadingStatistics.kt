package org.calibre.metadata

data class ReadingStatistics(
    val totalBooks: Int,
    val readBooks: Int,
    val unreadBooks: Int,
    val totalReadingTimeMinutes: Int,
    val totalBookmarks: Int,
    val averageRating: Double
) {
    val totalReadingTimeHours: Double
        get() = totalReadingTimeMinutes / 60.0
    
    val readPercentage: Double
        get() = if (totalBooks > 0) (readBooks.toDouble() / totalBooks) * 100.0 else 0.0
}
