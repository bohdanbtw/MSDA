package com.msda.android

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences

/**
 * Minimal in-memory SharedPreferences-backed Context for local JVM unit tests.
 *
 * Uses ContextWrapper so no abstract framework methods need implementing, and a
 * hand-rolled SharedPreferences so the tests stay pure JUnit (no Robolectric).
 */
class TestContext : ContextWrapper(null) {
    private val store = linkedMapOf<String, Any?>()

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs

    val prefs: SharedPreferences = object : SharedPreferences {
        override fun contains(key: String?): Boolean = synchronized(store) { store.containsKey(key) }

        override fun edit(): SharedPreferences.Editor = EditorImpl()

        override fun getAll(): MutableMap<String, *> = synchronized(store) { LinkedHashMap(store) }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            synchronized(store) { store[key] as? Boolean ?: defValue }

        override fun getFloat(key: String?, defValue: Float): Float =
            synchronized(store) { store[key] as? Float ?: defValue }

        override fun getInt(key: String?, defValue: Int): Int =
            synchronized(store) { store[key] as? Int ?: defValue }

        override fun getLong(key: String?, defValue: Long): Long =
            synchronized(store) { store[key] as? Long ?: defValue }

        override fun getString(key: String?, defValue: String?): String? =
            synchronized(store) { store[key] as? String ?: defValue }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            val stored = synchronized(store) { store[key] as? Set<*> }
            return stored?.mapNotNull { it as? String }?.toMutableSet() ?: defValues
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            // No-op: tests do not observe changes.
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            // No-op: tests do not observe changes.
        }
    }

    private object Removed

    private inner class EditorImpl : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clearAll = false

        override fun clear(): SharedPreferences.Editor {
            clearAll = true
            changes.clear()
            return this
        }

        override fun commit(): Boolean {
            synchronized(store) {
                if (clearAll) store.clear()
                for ((key, value) in changes) {
                    if (value === Removed) store.remove(key) else store[key] = value
                }
            }
            changes.clear()
            clearAll = false
            return true
        }

        override fun apply() {
            commit()
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor =
            put(key, value)

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor =
            put(key, value)

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor =
            put(key, value)

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor =
            put(key, value)

        override fun putString(key: String?, value: String?): SharedPreferences.Editor =
            put(key, value)

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            put(key, values?.toSet())

        override fun remove(key: String?): SharedPreferences.Editor =
            put(key, Removed)

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            changes[key as String] = value
            return this
        }
    }
}
