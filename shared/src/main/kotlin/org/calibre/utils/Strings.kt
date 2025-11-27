package org.calibre.utils

import java.text.MessageFormat
import java.util.Locale
import java.util.ResourceBundle

object Strings {
    private var bundle: ResourceBundle = ResourceBundle.getBundle("messages", Locale.getDefault())

    fun setLocale(locale: Locale) {
        bundle = ResourceBundle.getBundle("messages", locale)
    }

    operator fun get(key: String): String {
        return try {
            bundle.getString(key)
        } catch (e: Exception) {
            "??$key??"
        }
    }

    fun format(key: String, vararg args: Any): String {
        return try {
            MessageFormat.format(bundle.getString(key), *args)
        } catch (e: Exception) {
            "??$key??"
        }
    }
}
