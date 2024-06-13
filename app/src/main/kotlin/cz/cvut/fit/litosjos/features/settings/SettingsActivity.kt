package cz.cvut.fit.litosjos.features.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cz.cvut.fit.litosjos.features.settings.presentation.SettingsScreen
import cz.cvut.fit.litosjos.features.settings.theme.SchoolManagerTheme

class SettingsActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			SchoolManagerTheme {
				SettingsScreen(::finish)
			}
		}
	}
}