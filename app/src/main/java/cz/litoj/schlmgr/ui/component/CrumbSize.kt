package cz.litoj.schlmgr.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.AppSettings

/** The three breadcrumb bands, with their labels in settings. */
enum class CrumbSize(val label: Int) {
	COMPACT(R.string.crumb_size_compact),
	NORMAL(R.string.crumb_size_row),
	SPACIOUS(R.string.crumb_size_header);

	companion object {
		var current by mutableStateOf(NORMAL)
			private set

		fun applyStoredOptions() {
			current = entries.getOrElse(AppSettings.getInt(KEY, NORMAL.ordinal)) { NORMAL }
		}

		fun select(mode: CrumbSize) {
			current = mode
			AppSettings.set(KEY, mode.ordinal)
		}

		fun next(): CrumbSize = entries[(current.ordinal + 1) % entries.size]

		private const val KEY = "crumbSize"
	}
}
