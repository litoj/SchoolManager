package cz.litoj.schlmgr.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * App options, backed by the `"settings"` SharedPreferences.
 */
object AppSettings {
	private val prefs: SharedPreferences
		get() = GlobalDependencies.appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

	@JvmStatic fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
	@JvmStatic fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
	@JvmStatic fun getString(key: String): String? = prefs.getString(key, null)
	@JvmStatic fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def
	@JvmStatic fun set(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) } }
	@JvmStatic fun set(key: String, value: String) { prefs.edit { putString(key, value) } }
	@JvmStatic fun set(key: String, value: Int) { prefs.edit { putInt(key, value) } }
	@JvmStatic fun remove(key: String) { prefs.edit { remove(key) } }

	/**
	 * Writes one string synchronously — for a caller about to die (the
	 * crash handler). An async write would not land.
	 */
	@JvmStatic fun setNow(key: String, value: String) { prefs.edit(commit = true) { putString(key, value) } }
}
