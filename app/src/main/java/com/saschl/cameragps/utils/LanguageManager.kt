package com.saschl.cameragps.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {
    
    /**
     * Supported languages
     */
    enum class SupportedLanguage(val locale: Locale, val displayName: String) {
        ENGLISH(Locale.ENGLISH, "English"),
        GERMAN(Locale.GERMAN, "Deutsch"),
        CHINESE(Locale.SIMPLIFIED_CHINESE, "中文");

        companion object {
            fun getSupportedLocales(): List<Locale> {
                return entries.map { it.locale }
            }
        }
    }


    /**
     * Apply language immediately to the current activity
     */
    fun applyLanguageToActivity(activity: Activity, locale: Locale?) {
        // PreferencesManager.setLanguageCode(activity, languageCode)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(locale))
        AppCompatDelegate.getApplicationLocales()
    }

    /**
     * Get the currently selected language
     */
    fun getCurrentLanguage(context: Context): Locale? {
        val locale = if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            null
        } else {
            AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
        }
        return locale
    }
}
