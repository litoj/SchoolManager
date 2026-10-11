package cz.litoj.schlmgr.ui.shell

import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.popup.CrashReportPopup
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.about.AboutScreen
import cz.litoj.schlmgr.ui.screen.about.AboutViewModel
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.screen.settings.SettingsScreen
import cz.litoj.schlmgr.ui.screen.settings.SettingsViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.viewmodel.mainHistory
import kotlinx.coroutines.launch

/**
 * The app's one Compose shell — snackbars, dialogs, drawer, navigation graph.
 * - each destination owns its top bar and view model — the shell manages only
 *   names, factories, drawer and back stack.
 */
@Composable
fun SchlMgrApp() {
	// the transient user messages — every screen shows them over the content
	val snackbar = messageSnackbarHost()

	// The previous session's crash report, once per composition — a composed
	// window is attached, so the popup cannot hit a missing window token.
	LaunchedEffect(Unit) {
		val crash = Startup.getCrashReport() ?: return@LaunchedEffect
		if (!Dialogs.isShowing<CrashReportPopup>()) CrashReportPopup(crash)
	}

	val drawer = rememberDrawerLayerState()
	val navController = rememberNavController()
	fun openDrawer() = drawer.open()

	// the open drawer destination — the checked row and the gestures' owner
	val backStack by navController.currentBackStackEntryAsState()
	val route = backStack?.destination?.route
	val current = Destination.entries.firstOrNull { it.name == route }
		?: Destination.SUBJECTS

	Box(Modifier.fillMaxSize()) {
		DrawerLayer(
			state = drawer,
			drawerContent = {
				AppDrawer(current) { destination ->
					drawer.close()
					// the flat-siblings rule — never restore a saved stack under the
					// start destination (a restored stack silently ignored the tap)
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
					// the screens draw under the system bars. The content stops above the
					// navigation bar and the keyboard here, once, so no screen repeats
					// the insets (edge-to-edge never resizes the window for the IME)
					modifier = Modifier.navigationBarsPadding().imePadding(),
				) {
					composable(Destination.SUBJECTS.name) { entry ->
						val vm: MainExplorerViewModel = viewModel(entry) {
							MainExplorerViewModel(mainHistory)
						}
						// the app exit — the shell owns the host activity
						val activity = LocalActivity.current
						SubjectsScreen(
							viewModel = vm,
							openDrawer = ::openDrawer,
							onOpenTest = { navController.navigate(Destination.TEST.name) },
							onExitApp = { activity?.finish() },
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
					composable(Destination.SETTINGS.name) { entry ->
						val vm: SettingsViewModel = viewModel(entry) { SettingsViewModel() }
						SettingsScreen(vm, ::openDrawer)
					}
					composable(Destination.ABOUT.name) { entry ->
						val vm: AboutViewModel = viewModel(entry) { AboutViewModel() }
						AboutScreen(vm, ::openDrawer)
					}
				}
				// the snackbar sits above everything but the dialogs' own windows. It
				// takes the content's insets — or it rests under the navigation buttons
				SnackbarHost(
					hostState = snackbar,
					modifier = Modifier.align(Alignment.BottomCenter)
						.navigationBarsPadding()
						.imePadding(),
				)
			}
		}
		// The status band (the drawer banner's own green, darkened) is
		// drawn last, so it also covers the drawer and its scrim — the bar
		// looks the same whether the drawer is open or closed. TopBar
		// reserves only the strip's height.
		Box(
			Modifier
				.fillMaxWidth()
				.windowInsetsTopHeight(WindowInsets.statusBars)
				.background(colorResource(R.color.colorStatusBar))
		)
	}

	DialogHost()
}
