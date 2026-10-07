package cz.litoj.schlmgr.app

import android.content.SharedPreferences
import cz.litoj.schlmgr.ui.GlobalDependencies

/**
 * The single access point for the app's options, backed by the system's `"settings"`
 * SharedPreferences. The engine itself exposes no options API — it only reads them.
 */
object AppSettings {
	private val prefs: SharedPreferences
		get() = GlobalDependencies.appContext.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

	@JvmStatic fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
	@JvmStatic fun getInt(key: String, def: Int): Int = prefs.getInt(key, def)
	@JvmStatic fun getString(key: String): String? = prefs.getString(key, null)
	@JvmStatic fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def
	@JvmStatic fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
	@JvmStatic fun set(key: String, value: String) { prefs.edit().putString(key, value).apply() }
	@JvmStatic fun set(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
	@JvmStatic fun remove(key: String) { prefs.edit().remove(key).apply() }
}
