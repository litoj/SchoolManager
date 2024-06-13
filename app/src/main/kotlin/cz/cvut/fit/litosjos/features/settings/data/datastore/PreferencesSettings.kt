package cz.cvut.fit.litosjos.features.settings.data.datastore

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.domain.settingsTypedKeys
import cz.cvut.fit.litosjos.features.settings.domain.toMap
import cz.cvut.fit.litosjos.features.settings.domain.toSettingsOrDefault

fun Preferences.toSettings(): Settings {
	val map = mutableMapOf<String, String>()
	val types = settingsTypedKeys()
	for ((key, type) in types) {
		val value = when (type) {
			Boolean::class -> get(booleanPreferencesKey(key))
			Int::class -> get(intPreferencesKey(key))
			else -> get(stringPreferencesKey(key))
		}
		if (value != null) map[key] = value.toString()
	}
	return map.toSettingsOrDefault(Settings())
}

fun MutablePreferences.save(settings: Settings) {
	val map = settings.toMap()
	val types = settingsTypedKeys()
	for ((key, type) in types) {
		val value = map[key]
		if (value != null) when (type) {
			Boolean::class -> this[booleanPreferencesKey(key)] = value as Boolean
			Int::class -> this[intPreferencesKey(key)] = value as Int
			else -> this[stringPreferencesKey(key)] = value.toString()
		}
	}
}