package org.calibre.metadata

data class ReadingSettings(
    var fontSize: Int = 16,
    var fontFamily: String = "serif",
    var backgroundColor: String = "#FFFFFF",
    var textColor: String = "#000000",
    var marginHorizontal: Int = 20,
    var marginVertical: Int = 30,
    var lineHeight: Double = 1.5,
    var theme: String = "light" // light, dark, sepia
) {
    fun toCss(): String {
        val bgColor = when (theme) {
            "dark" -> "#1E1E1E"
            "sepia" -> "#F4ECD8"
            else -> backgroundColor
        }
        val txtColor = when (theme) {
            "dark" -> "#E0E0E0"
            "sepia" -> "#5C4A37"
            else -> textColor
        }
        
        return """
            body {
                font-family: $fontFamily;
                font-size: ${fontSize}px;
                background-color: $bgColor;
                color: $txtColor;
                margin: ${marginVertical}px ${marginHorizontal}px;
                line-height: $lineHeight;
            }
        """.trimIndent()
    }
}
