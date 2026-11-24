package cz.litoj.schlmgr.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Typed preferences owned by one application dependency container. */
class Settings internal constructor(context: Context) {
	private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

	fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
	fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
	fun getFloat(key: String, def: Float): Float = prefs.getFloat(key, def)
	fun has(key: String): Boolean = prefs.contains(key)
	fun getString(key: String): String? = prefs.getString(key, null)
	fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def
	fun set(key: String, value: Boolean) { prefs.edit { putBoolean(key, value) } }
	fun set(key: String, value: String) { prefs.edit { putString(key, value) } }
	fun set(key: String, value: Int) { prefs.edit { putInt(key, value) } }
	fun set(key: String, value: Float) { prefs.edit { putFloat(key, value) } }
	fun remove(key: String) { prefs.edit { remove(key) } }

	/** A caller about to die needs this string written synchronously. */
	fun setNow(key: String, value: String) { prefs.edit(commit = true) { putString(key, value) } }
}
