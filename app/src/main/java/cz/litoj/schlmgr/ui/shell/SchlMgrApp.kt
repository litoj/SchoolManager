package cz.litoj.schlmgr.ui.shell

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import cz.litoj.schlmgr.app.AppDependencies
import cz.litoj.schlmgr.app.CrashReporter
import cz.litoj.schlmgr.ui.activity.SettingsActivity
import cz.litoj.schlmgr.ui.popup.CrashReportPopup
import cz.litoj.schlmgr.ui.popup.DialogHost
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.LocalUserMessages
import cz.litoj.schlmgr.ui.screen.about.AboutScreen
import cz.litoj.schlmgr.ui.screen.about.AboutViewModel
import cz.litoj.schlmgr.ui.screen.picker.PickerScreen
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupScreen
import cz.litoj.schlmgr.ui.screen.test.TestSetupOwner
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.launch

/**
 * The one Compose shell of the app: snackbars, dialogs, drawer and navigation graph.
 * - each destination owns its top bar and view model.
 * - the shell manages only names, factories, the drawer and the back stack.
 */
@Composable
fun SchlMgrApp(
	dependencies: AppDependencies,
	launchTarget: MainLaunchTarget? = null,
	onLaunchHandled: () -> Unit = {},
) {
	// the presses of the app arm faster than the platform default, see [QuickLongPress]
	val system = LocalViewConfiguration.current
	CompositionLocalProvider(
		LocalViewConfiguration provides QuickLongPress(system),
		LocalUserMessages provides dependencies.userMessages,
	) {
		// the transient user messages, which every screen shows over the content
		val snackbar = messageSnackbarHost(dependencies.userMessages)

		// the crash report of the previous session, once per composition.
		// a composed window is attached, so the popup cannot hit a missing window token
		LaunchedEffect(Unit) {
			val crash = CrashReporter.getCrashReport() ?: return@LaunchedEffect
			if (!Dialogs.isShowing<CrashReportPopup>()) CrashReportPopup(dependencies.settings, crash)
		}

		val drawer = rememberDrawerLayerState()
		val navController = rememberNavController()
		val setupOwner: TestSetupOwner = viewModel()
		fun openDrawer() = drawer.open()

		// the one explorer model of the shell, so the drawer can reset it in place
		val subjectsViewModel: MainExplorerViewModel = viewModel {
			MainExplorerViewModel(
				NavigationHistory(),
				graphs = dependencies.graphQueries,
				editor = dependencies.itemEditor,
				itemTransferService = dependencies.itemTransfer,
				flipper = dependencies.flipper,
				settings = dependencies.settings,
				userMessages = dependencies.userMessages,
				testSetup = setupOwner.state,
			)
		}

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
						// a press on the open explorer itself returns to the subjects list
						if (destination == Destination.SUBJECTS && route == Destination.SUBJECTS.name) {
							subjectsViewModel.goToRoot()
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
						composable(Destination.SUBJECTS.name) {
							// the app exit, since the shell owns the host activity
							val activity = LocalActivity.current
							SubjectsScreen(
								viewModel = subjectsViewModel,
								settings = dependencies.settings,
								userMessages = dependencies.userMessages,
								openDrawer = ::openDrawer,
								onOpenTest = { navController.navigate(Destination.TEST.name) },
								onPickReferences = { navController.navigate(PICKER_ROUTE) },
								onExitApp = { activity?.finish() },
								launchTarget = launchTarget,
								onLaunchHandled = onLaunchHandled,
							)
						}
						composable(Destination.TEST.name) { entry ->
							val vm: TestSetupViewModel = viewModel(entry) {
								TestSetupViewModel(
									testSetup = setupOwner.state,
									settings = dependencies.settings,
									userMessages = dependencies.userMessages,
								)
							}
							TestSetupScreen(
								viewModel = vm,
								userMessages = dependencies.userMessages,
								openPicker = { navController.navigate(PICKER_ROUTE) },
								openDrawer = ::openDrawer,
							)
						}
						composable(PICKER_ROUTE) { entry ->
							// a reference pick links into the captured chapter, otherwise sources feed the test
							val vm: PickerViewModel = viewModel(entry) {
								PickerViewModel(
									subjectsViewModel.history,
									referenceTarget = subjectsViewModel.referenceTarget,
									settings = dependencies.settings,
									userMessages = dependencies.userMessages,
									graphs = dependencies.graphQueries,
									transfer = dependencies.itemTransfer,
									testSetup = setupOwner.state,
								)
							}
							PickerScreen(
								viewModel = vm,
								settings = dependencies.settings,
								userMessages = dependencies.userMessages,
								onExit = {
									subjectsViewModel.finishReferencePicker(vm.didReference)
									navController.popBackStack()
								},
								title = if (subjectsViewModel.referenceTarget != null)
									R.string.reference_picker_title else R.string.test_add,
							)
						}
						composable(Destination.ABOUT.name) { entry ->
							val vm: AboutViewModel = viewModel(entry) { AboutViewModel() }
							AboutScreen(vm, dependencies.userMessages, ::openDrawer)
						}
					}
					// the snackbar sits above everything except the windows of the dialogs.
					// it takes the insets of the content, or rests under the navigation buttons
					MessageSnackbarHost(
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
