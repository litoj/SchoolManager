package cz.cvut.fit.litosjos.features.settings.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SettingsDsSource(private val context: Context) {
	private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

	fun getSettings(): Flow<Settings> =
		context.dataStore.data.map { it -> it.toSettings() }.distinctUntilChanged()

	suspend fun updateSettings(settings: Settings) = context.dataStore.edit { it.save(settings) }
}