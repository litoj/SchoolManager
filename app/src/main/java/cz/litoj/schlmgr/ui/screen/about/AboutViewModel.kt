package cz.litoj.schlmgr.ui.screen.about

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Holds the help tree state: which sections are expanded and the active search. */
class AboutViewModel : ViewModel() {

	var expanded by mutableStateOf(setOf<Int>())
		private set

	fun toggle(key: Int) {
		expanded = if (key in expanded) expanded - key else expanded + key
	}

	var searchOpen by mutableStateOf(false)
		private set

	var query by mutableStateOf("")
		private set

	fun openSearchField() {
		searchOpen = true
	}

	// the tree is unfiltered once the field is gone, so the query leaves with it
	fun closeSearchField() {
		searchOpen = false
		query = ""
	}

	fun onQueryChange(text: String) {
		query = text
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
