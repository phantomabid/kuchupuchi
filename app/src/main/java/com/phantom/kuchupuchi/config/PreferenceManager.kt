package com.phantom.kuchupuchi.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferenceManager {
    private const val PREF_NAME = "kuchupuchi_prefs"

    const val KEY_VIBRATION_PATTERN = "selected_vibration_pattern"
    const val DEFAULT_PATTERN = "HEARTBEAT"

    const val KEY_IDLE_OPACITY = "idle_opacity"
    const val DEFAULT_IDLE_OPACITY = 0.5f

    const val KEY_BUTTON_SIZE_DP = "floating_button_size_dp"
    const val DEFAULT_BUTTON_SIZE_DP = 64

    const val KEY_FIRST_LAUNCH = "is_first_launch"

    const val KEY_APP_THEME = "app_theme"
    const val DEFAULT_THEME = "DEFAULT"

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getSelectedPattern(context: Context): String {
        return getPreferences(context).getString(KEY_VIBRATION_PATTERN, DEFAULT_PATTERN) ?: DEFAULT_PATTERN
    }

    fun setSelectedPattern(context: Context, pattern: String) {
        getPreferences(context).edit {
            putString(KEY_VIBRATION_PATTERN, pattern)
        }
    }

    fun getIdleOpacity(context: Context): Float {
        return getPreferences(context).getFloat(KEY_IDLE_OPACITY, DEFAULT_IDLE_OPACITY)
    }

    fun setIdleOpacity(context: Context, opacity: Float) {
        getPreferences(context).edit {
            putFloat(KEY_IDLE_OPACITY, opacity.coerceIn(0.1f, 1.0f))
        }
    }

    fun getFloatingButtonSizeDp(context: Context): Int {
        return getPreferences(context).getInt(KEY_BUTTON_SIZE_DP, DEFAULT_BUTTON_SIZE_DP)
    }

    fun setFloatingButtonSizeDp(context: Context, sizeDp: Int) {
        getPreferences(context).edit {
            putInt(KEY_BUTTON_SIZE_DP, sizeDp.coerceIn(16, 120))
        }
    }

    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        getPreferences(context).edit {
            putBoolean(KEY_FIRST_LAUNCH, false)
        }
    }

    fun getAppTheme(context: Context): String {
        return getPreferences(context).getString(KEY_APP_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }

    fun setAppTheme(context: Context, theme: String) {
        getPreferences(context).edit {
            putString(KEY_APP_THEME, theme)
        }
    }
}
