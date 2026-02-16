package org.calibre.android

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate

/**
 * Lightweight adapter around theming concepts inspired by Kaleaon/ktheme.
 *
 * The project cannot bundle the upstream engine directly in this environment,
 * so we provide a compatible theme registry/persistence layer that can be
 * swapped to the upstream engine when dependency resolution is enabled.
 */
object KThemeEngine {
    private const val PREFS_NAME = "ktheme_prefs"
    private const val KEY_THEME = "selected_theme"

    data class ThemeOption(
        val id: String,
        val displayName: String,
        @StyleRes val styleRes: Int,
        val nightMode: Int
    )

    val themes: List<ThemeOption> = listOf(
        ThemeOption(
            id = "classic",
            displayName = "Classic Calibre",
            styleRes = R.style.Theme_CalibreAndroid,
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ),
        ThemeOption(
            id = "ocean",
            displayName = "Ocean",
            styleRes = R.style.Theme_CalibreAndroid_Ocean,
            nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        ),
        ThemeOption(
            id = "sepia",
            displayName = "Sepia",
            styleRes = R.style.Theme_CalibreAndroid_Sepia,
            nightMode = AppCompatDelegate.MODE_NIGHT_NO
        ),
        ThemeOption(
            id = "night",
            displayName = "Night Reader",
            styleRes = R.style.Theme_CalibreAndroid_Night,
            nightMode = AppCompatDelegate.MODE_NIGHT_YES
        )
    )

    fun applyTheme(activity: androidx.appcompat.app.AppCompatActivity) {
        val selected = getSelectedTheme(activity)
        AppCompatDelegate.setDefaultNightMode(selected.nightMode)
        activity.setTheme(selected.styleRes)
    }

    fun getSelectedTheme(context: Context): ThemeOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME, themes.first().id)
        return themes.firstOrNull { it.id == themeId } ?: themes.first()
    }

    fun selectTheme(context: Context, themeId: String): Boolean {
        val selected = themes.firstOrNull { it.id == themeId } ?: return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, selected.id).apply()
        AppCompatDelegate.setDefaultNightMode(selected.nightMode)
        return true
    }
}
