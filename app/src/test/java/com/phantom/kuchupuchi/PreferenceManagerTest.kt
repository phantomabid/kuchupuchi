package com.phantom.kuchupuchi

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.phantom.kuchupuchi.config.PreferenceManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String?, defValue: String?): String? {
            return map[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as? MutableSet<String> ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return map[key] as? Int ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return map[key] as? Long ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float {
            return map[key] as? Float ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return map[key] as? Boolean ?: defValue
        }

        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor(map)

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) map[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) map[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) map.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                map.clear()
                return this
            }

            override fun commit(): Boolean = true

            override fun apply() {}
        }
    }

    private class FakeContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
            return prefs
        }
    }

    @Test
    fun defaultButtonSize_is64() {
        assertEquals(64, PreferenceManager.DEFAULT_BUTTON_SIZE_DP)
    }

    @Test
    fun floatingButtonSize_getAndSet() {
        val fakePrefs = FakeSharedPreferences()
        val context = FakeContext(fakePrefs)

        assertEquals(PreferenceManager.DEFAULT_BUTTON_SIZE_DP, PreferenceManager.getFloatingButtonSizeDp(context))

        PreferenceManager.setFloatingButtonSizeDp(context, 60)
        assertEquals(60, PreferenceManager.getFloatingButtonSizeDp(context))

        PreferenceManager.setFloatingButtonSizeDp(context, 5)
        assertEquals(16, PreferenceManager.getFloatingButtonSizeDp(context))

        PreferenceManager.setFloatingButtonSizeDp(context, 200)
        assertEquals(120, PreferenceManager.getFloatingButtonSizeDp(context))
    }

    @Test
    fun appTheme_getAndSet() {
        val fakePrefs = FakeSharedPreferences()
        val context = FakeContext(fakePrefs)

        assertEquals("DEFAULT", PreferenceManager.getAppTheme(context))

        PreferenceManager.setAppTheme(context, "MIDNIGHT")
        assertEquals("MIDNIGHT", PreferenceManager.getAppTheme(context))

        PreferenceManager.setAppTheme(context, "ROMANTIC_ROSE")
        assertEquals("ROMANTIC_ROSE", PreferenceManager.getAppTheme(context))
    }
}
