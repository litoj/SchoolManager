package cz.litoj.schlmgr.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.litoj.schlmgr.app.AppDependencies
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.LocalUserMessages
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.shell.StatusBarBand
import cz.litoj.schlmgr.ui.shell.MessageSnackbarHost
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel

/**
 * The settings in their own activity, so the system app view opens them directly.
 * - the shared cold start matches [MainActivity], the system can launch it alone.
 * - back finishes it, since the activity owns no drawer.
 */
class SettingsActivity : ComponentActivity() {

	private lateinit var dependencies: AppDependencies

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		dependencies = startAppShell()
		setContent {
			CompositionLocalProvider(LocalUserMessages provides dependencies.userMessages) {
				val snackbar = messageSnackbarHost(dependencies.userMessages)
				val vm: SettingsViewModel = viewModel { SettingsViewModel(dependencies.settings) }
				Box(Modifier.fillMaxSize()) {
					// the content stops above the navigation bar and the keyboard here, once
					Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
						SettingsScreen(vm, dependencies.userMessages, ::finish)
						MessageSnackbarHost(
							hostState = snackbar,
							modifier = Modifier.align(Alignment.BottomCenter),
						)
					}
					// the status band is drawn last, so it covers the band area of the top bar.
					StatusBarBand()
				}
				DialogHost()
			}
		}
	}
}
