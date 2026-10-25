package cz.litoj.schlmgr.ui.activity

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel

/**
 * The settings in their own activity, so the system app view opens them directly.
 * - the cold init matches [MainActivity], the system can launch it alone.
 * - back finishes it, since the activity owns no drawer.
 */
class SettingsActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Startup.init(this)
		// the row display options have their own stored half in the ui
		ItemRow.applyStoredOptions()
		// a solid dark scrim keeps the navigation bar black in both palettes
		enableEdgeToEdge(
			statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
			navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
		)
		setContent {
			val snackbar = messageSnackbarHost()
			val vm: SettingsViewModel = viewModel { SettingsViewModel() }
			Box(Modifier.fillMaxSize()) {
				// the content stops above the navigation bar and the keyboard here, once
				Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
					SettingsScreen(vm, ::finish)
					SnackbarHost(
						hostState = snackbar,
						modifier = Modifier.align(Alignment.BottomCenter),
					)
				}
				// the status band is the dark green of the drawer banner.
				// it is drawn last, so it also covers the band area of the top bar.
				Box(
					Modifier
						.fillMaxWidth()
						.windowInsetsTopHeight(WindowInsets.statusBars)
						.background(colorResource(R.color.colorStatusBar))
				)
			}
		}
	}
}
