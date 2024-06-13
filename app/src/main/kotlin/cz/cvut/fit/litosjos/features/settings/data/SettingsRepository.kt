package cz.cvut.fit.litosjos.features.settings.data

import android.net.Uri
import cz.cvut.fit.litosjos.features.settings.data.datastore.SettingsDsSource
import cz.cvut.fit.litosjos.features.settings.data.file_system.SettingsFsSource
import cz.cvut.fit.litosjos.features.settings.domain.Settings

class SettingsRepository(
	private val dataStore: SettingsDsSource, private val fileSystem: SettingsFsSource
) {
	fun getSettings() = dataStore.getSettings()

	suspend fun updateSettings(settings: Settings) = dataStore.updateSettings(settings)

	fun exportSettings(settings: Settings, uri: Uri) = fileSystem.export(settings, uri)

	fun importSettings(uri: Uri, default: Settings) = fileSystem.import(uri, default)
}