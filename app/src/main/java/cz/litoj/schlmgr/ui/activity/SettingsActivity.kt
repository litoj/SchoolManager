package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.SnackbarHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.shell.StatusBarBand
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel

/**
 * The settings in their own activity, so the system app view opens them directly.
 * - the shared cold start matches [MainActivity], the system can launch it alone.
 * - back finishes it, since the activity owns no drawer.
 */
class SettingsActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		startAppShell()
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
				// the status band is drawn last, so it covers the band area of the top bar.
				StatusBarBand()
			}
		}
	}
}
