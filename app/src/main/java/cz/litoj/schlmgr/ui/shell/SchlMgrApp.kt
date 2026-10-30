package cz.litoj.schlmgr.ui.shell

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.CrashReporter
import cz.litoj.schlmgr.ui.activity.SettingsActivity
import cz.litoj.schlmgr.ui.popup.CrashReportPopup
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.about.AboutScreen
import cz.litoj.schlmgr.ui.screen.about.AboutViewModel
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.viewmodel.mainHistory
import kotlinx.coroutines.launch

/**
 * The one Compose shell of the app: snackbars, dialogs, drawer and navigation graph.
 * - each destination owns its top bar and view model.
 * - the shell manages only names, factories, the drawer and the back stack.
 */
@Composable
fun SchlMgrApp(
	launchTarget: MainLaunchTarget? = null,
	onLaunchHandled: () -> Unit = {},
) {
	// the presses of the app arm faster than the platform default, see [QuickLongPress]
	val system = LocalViewConfiguration.current
	CompositionLocalProvider(LocalViewConfiguration provides QuickLongPress(system)) {
		// the transient user messages, which every screen shows over the content
		val snackbar = messageSnackbarHost()

		// the crash report of the previous session, once per composition.
		// a composed window is attached, so the popup cannot hit a missing window token
		LaunchedEffect(Unit) {
			val crash = CrashReporter.getCrashReport() ?: return@LaunchedEffect
			if (!Dialogs.isShowing<CrashReportPopup>()) CrashReportPopup(crash)
		}

		val drawer = rememberDrawerLayerState()
		val navController = rememberNavController()
		fun openDrawer() = drawer.open()

		// an import Done returns to the subjects screen, which applies the target path.
		LaunchedEffect(launchTarget) {
			if (launchTarget != null) navController.navigate(Destination.SUBJECTS.name) {
				popUpTo(Destination.SUBJECTS.name) {
					inclusive = false
					saveState = false
				}
				launchSingleTop = true
			}
		}

		// the open drawer destination, which checks a row and owns the gestures
		val backStack by navController.currentBackStackEntryAsState()
		val route = backStack?.destination?.route
		val current = Destination.entries.firstOrNull { it.name == route }
			?: Destination.SUBJECTS

		Box(Modifier.fillMaxSize()) {
			DrawerLayer(
				state = drawer,
				drawerContent = {
					val context = LocalContext.current
					AppDrawer(current) { destination ->
						drawer.close()
						// the settings open their own activity, everything else navigates flat
						if (destination == Destination.SETTINGS) {
							context.startActivity(Intent(context, SettingsActivity::class.java))
							return@AppDrawer
						}
						// the flat-siblings rule applies to the start destination.
						navController.navigate(destination.name) {
							popUpTo(Destination.SUBJECTS.name) {
								inclusive = false
								saveState = false
							}
							launchSingleTop = true
						}
					}
				},
			) {
				Box(Modifier.fillMaxSize()) {
					NavHost(
						navController = navController,
						startDestination = Destination.SUBJECTS.name,
						// the screens draw under the system bars.
						// the content stops above the navigation bar and the keyboard here, once.
						modifier = Modifier.navigationBarsPadding().imePadding(),
					) {
						composable(Destination.SUBJECTS.name) { entry ->
							val vm: MainExplorerViewModel = viewModel(entry) {
								MainExplorerViewModel(mainHistory)
							}
							// the app exit, since the shell owns the host activity
							val activity = LocalActivity.current
							SubjectsScreen(
								viewModel = vm,
								openDrawer = ::openDrawer,
								onOpenTest = { navController.navigate(Destination.TEST.name) },
								onExitApp = { activity?.finish() },
								launchTarget = launchTarget,
								onLaunchHandled = onLaunchHandled,
							)
						}
						composable(Destination.TEST.name) { entry ->
							val vm: TestSetupViewModel = viewModel(entry) { TestSetupViewModel() }
							TestSetupScreen(
								viewModel = vm,
								openPicker = { navController.navigate(PICKER_ROUTE) },
								openDrawer = ::openDrawer,
							)
						}
						composable(PICKER_ROUTE) { entry ->
							val vm: PickerViewModel = viewModel(entry) { PickerViewModel() }
							PickerScreen(
								viewModel = vm,
								onExit = { navController.popBackStack() },
							)
						}
						composable(Destination.ABOUT.name) { entry ->
							val vm: AboutViewModel = viewModel(entry) { AboutViewModel() }
							AboutScreen(vm, ::openDrawer)
						}
					}
					// the snackbar sits above everything except the windows of the dialogs.
					// it takes the insets of the content, or rests under the navigation buttons
					SnackbarHost(
						hostState = snackbar,
						modifier = Modifier.align(Alignment.BottomCenter)
							.navigationBarsPadding()
							.imePadding(),
					)
				}
			}
			// the status band is the dark green of the drawer banner.
			// - it is drawn last, so it also covers the drawer and its scrim.
			// - the bar looks the same whether the drawer is open or closed.
			StatusBarBand()
		}

		DialogHost()
	}
}

private class QuickLongPress(system: ViewConfiguration) : ViewConfiguration by system {
	override val longPressTimeoutMillis = LongPressMillis
}

/** The long-press wait, since the 400ms platform default feels slow for a row selection. */
private const val LongPressMillis = 250L
