package com.saschl.cameragps.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferencesManager {
    private const val PREFS_NAME = "camera_gps_prefs"
    private const val KEY_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_APP_ENABLED = "app_enabled"
    private const val KEY_DEVICE_ENABLED_PREFIX = "device_enabled_"

    private const val KEY_DEVICE_KEEPALIVE_PREFIX = "device_keepalive_"
    private const val KEY_BATTERY_OPTIMIZATION_DIALOG_DISMISSED = "battery_optimization_dialog_dismissed"
    private const val KEY_LANGUAGE_CODE = "language_code"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }
    fun showFirstLaunch(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, true)
        }
    }

    fun isAppEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_APP_ENABLED, true)
    }

    fun setAppEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_APP_ENABLED, enabled)
        }
    }

    fun isDeviceEnabled(context: Context, deviceAddress: String): Boolean {
        return getPreferences(context).getBoolean(KEY_DEVICE_ENABLED_PREFIX + deviceAddress, true)
    }

    fun setDeviceEnabled(context: Context, deviceAddress: String, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_DEVICE_ENABLED_PREFIX + deviceAddress, enabled)
        }
    }

    fun isBatteryOptimizationDialogDismissed(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_BATTERY_OPTIMIZATION_DIALOG_DISMISSED, false)
    }

    fun setBatteryOptimizationDialogDismissed(context: Context, dismissed: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_BATTERY_OPTIMIZATION_DIALOG_DISMISSED, dismissed)
        }
    }

    fun isKeepAliveEnabled(context: Context, deviceAddress: String): Boolean {
        return getPreferences(context).getBoolean(KEY_DEVICE_KEEPALIVE_PREFIX + deviceAddress, false)
    }

    fun setKeepAliveEnabled(context: Context, deviceAddress: String, enabled: Boolean) {
        getPreferences(context).edit {
            putBoolean(KEY_DEVICE_KEEPALIVE_PREFIX + deviceAddress, enabled)
        }
    }

    /**
     * Gets the saved language code. Returns null if system default should be used.
     */
    fun getLanguageCode(context: Context): String? {
        val code = getPreferences(context).getString(KEY_LANGUAGE_CODE, null)
        return if (code == "system") null else code
    }

    /**
     * Sets the language code. Pass null or "system" to use system default.
     */
    fun setLanguageCode(context: Context, languageCode: String?) {
        getPreferences(context).edit {
            putString(KEY_LANGUAGE_CODE, languageCode ?: "system")
        }
    }
}
