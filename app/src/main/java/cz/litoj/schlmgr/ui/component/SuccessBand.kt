package cz.litoj.schlmgr.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.app.Settings

/**
 * The peak opacity of the success band, the base of its alpha ramp.
 * - the settings slider sets it in percent, a live list re-renders through the Compose state.
 * - zero leaves every tested row untinted.
 */
object SuccessBand {
	var intensity by mutableIntStateOf(DEFAULT_INTENSITY)
		private set

	/** Reads the stored value, called before the first composition. */
	fun applyStoredOptions(settings: Settings) {
		intensity = settings.getInt(KEY, DEFAULT_INTENSITY).coerceIn(0, 100)
	}

	/** One mutation door, the settings slider owns it. */
	fun setPeak(settings: Settings, percent: Int) {
		intensity = percent.coerceIn(0, 100)
		settings.set(KEY, intensity)
	}

	/** The peak alpha of the band, rounded from the percent of full opacity. */
	fun peakAlpha(): Int = (intensity * 0xFF + 50) / 100

	const val DEFAULT_INTENSITY = 50
	private const val KEY = "successBandIntensity"
}
