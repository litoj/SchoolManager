package cz.litoj.schlmgr.ui.screen.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Holds which help sections are expanded, keyed by section title resource. */
class AboutViewModel : ViewModel() {

	var expanded by mutableStateOf(setOf<Int>())
		private set

	fun toggle(key: Int) {
		expanded = if (key in expanded) expanded - key else expanded + key
	}

	var version by mutableStateOf<String?>(null)
		private set

	/**
	 * Reads the version once per screen.
	 * - the application context outlives the screen, so holding no activity reference is safe.
	 */
	fun loadVersion(context: Context) {
		if (version != null) return
		version = try {
			context.applicationContext.packageManager
				.getPackageInfo(context.applicationContext.packageName, 0).versionName
		} catch (e: PackageManager.NameNotFoundException) {
			null
		}
	}
}
