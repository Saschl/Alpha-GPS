package com.saschl.cameragps.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    fun reviewHintLastShownDaysAgo(context: Context): Long {
        val lastShown = getPreferences(context).getLong("review_hint_last_shown", 0L)

        var lastShownInstant: Instant
        if (lastShown == 0L) {
            // give the user one day breathing room before showing the hint after setting up the first device
            lastShownInstant = Instant.now().minus(29, ChronoUnit.DAYS)
            getPreferences(context).edit {
                putLong("review_hint_last_shown", lastShownInstant.epochSecond)
            }
        } else {
            lastShownInstant = Instant.ofEpochSecond(lastShown)

        }
        val daysAgo = Duration.between(lastShownInstant, Instant.now()).toDays()
        return daysAgo
    }

    fun setReviewHintShownNow(context: Context) {
        getPreferences(context).edit {
            putLong("review_hint_last_shown", Instant.now().epochSecond)
        }
    }

    fun resetReviewHintShown(context: Context) {
        getPreferences(context).edit {
            putLong("review_hint_last_shown", 0L)
        }
    }

    fun reviewHintShownTimes(applicationContext: Context): Int {
        return getPreferences(applicationContext).getInt("review_hint_shown_times", 0)
    }

    fun increaseReviewHintShownTimes(applicationContext: Context) {
        val currentTimes = reviewHintShownTimes(applicationContext)
        getPreferences(applicationContext).edit {
            putInt("review_hint_shown_times", currentTimes + 1)
        }
    }

    fun decreaseReviewHintShownTimes(applicationContext: Context) {
        val currentTimes = reviewHintShownTimes(applicationContext)
        getPreferences(applicationContext).edit {
            putInt("review_hint_shown_times", currentTimes - 1)
        }
    }
}
