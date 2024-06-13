package cz.cvut.fit.litosjos.core.presentation

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cz.cvut.fit.litosjos.features.settings.domain.Settings
import cz.cvut.fit.litosjos.features.settings.presentation.presets.RowFillIcon

@Composable
fun ColorizedItemIcon(icon: Int, passed: Int, failed: Int, settings: Settings) {
	if (settings.testSuccessColorIntensity == 0) return RowFillIcon(iconId = icon)

	val total = passed + failed
	if (total == 0 && !settings.untestedItemColorize) return RowFillIcon(iconId = icon)

	val color = if (total == 0) Color(0x00, 0x00, 0xff, settings.testSuccessColorIntensity)
	else {
		Color(
			if (passed > failed) 0x1fe * failed / total else 0xff,
			if (passed > failed) 0xff else 0x1fe * passed / total,
			0,
			settings.testSuccessColorIntensity
		)
	}

	if (settings.testSuccessColorizeBackground) RowFillIcon(
		iconId = icon, modifier = Modifier.background(color)
	)
	else RowFillIcon(iconId = icon, tint = color)
}