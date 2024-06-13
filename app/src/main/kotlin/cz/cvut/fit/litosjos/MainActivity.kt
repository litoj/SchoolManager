package cz.cvut.fit.litosjos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cz.cvut.fit.litosjos.core.presentation.Navigation
import cz.cvut.fit.litosjos.features.settings.theme.SchoolManagerTheme

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			SchoolManagerTheme {
				Navigation()
			}
		}
	}
}