package cz.cvut.fit.litosjos.features.settings.data.file_system

import android.content.Context
import android.net.Uri
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.domain.toMap
import cz.cvut.fit.litosjos.features.settings.domain.toSettingsOrDefault
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class SettingsFsSource(private val context: Context) {

	fun export(settings: Settings, uri: Uri) {
		OutputStreamWriter(
			context.contentResolver.openOutputStream(uri), Charsets.UTF_8
		).use { writer ->
			for ((key, value) in settings.toMap()) {
				writer.write("$key=$value\n")
			}
		}
	}

	fun import(uri: Uri, default: Settings): Settings {
		InputStreamReader(context.contentResolver.openInputStream(uri), Charsets.UTF_8).use { reader ->
			val map = mutableMapOf<String, String>()
			reader.readLines().forEach { line ->
				val arr = line.split("=")
				if (arr.size == 2) map[arr[0].trim()] = arr[1].trim()
			}
			return map.toSettingsOrDefault(default)
		}
	}
}