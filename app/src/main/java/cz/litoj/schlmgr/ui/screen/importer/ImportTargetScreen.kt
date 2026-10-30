package cz.litoj.schlmgr.ui.screen.importer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.ActionStripHeight
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ImportActionBar
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.ListEndReveal
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.explorerBack
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.shell.messageSnackbarHost
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel

/**
 * The import mover screen: the explorer picks the target, the always-visible bar acts.
 * - the file URI stays with the view model, so the activity only starts and finishes it.
 * - the result reloads the target list and turns the bar into Done.
 */
@Composable
fun ImportTargetScreen(
	viewModel: ImportTargetViewModel,
	onExit: () -> Unit,
	onDone: (List<Int>) -> Unit,
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	val target by viewModel.targetState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	// the import posts its progress bar, so the mover asks for the permission
	// before the import can start. A denial only loses the bar.
	// - the ask runs once, a recreation of a denied mover must not nag
	var permissionAsked by rememberSaveable { mutableStateOf(false) }
	val notificationPermission = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission()) {}
	LaunchedEffect(Unit) {
		if (permissionAsked) return@LaunchedEffect
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
				context, Manifest.permission.POST_NOTIFICATIONS)
			!= PackageManager.PERMISSION_GRANTED) {
			permissionAsked = true
			notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}
	val snackbar = messageSnackbarHost()
	val scrollState = rememberLazyListState()
	ListEndReveal(listState, scrollState)
	// a busy import keeps the target, so back and cancel cannot change or lose it.
	// the mover rides the explorer back chain, so its exit hint shows too
	val leave = {
		if (!target.busy) explorerBack(viewModel, onExit, context)
	}
	BackHandler(target.busy) {}
	ExplorerBackHandler(viewModel, onExitApp = onExit, enabled = !target.busy)

	Box(Modifier.fillMaxSize().navigationBarsPadding()) {
		Column(Modifier.fillMaxSize()) {
			TopBar {
				TopBarNav(up = true, onNavigate = leave)
				TopBarTitle(stringResource(R.string.import_file_title, target.fileName))
			}
			ExplorerScaffold(
				state = state,
				listState = listState,
				onBreadcrumbClick = { if (!target.busy) viewModel.breadcrumbClick(it) },
				onInfoClick = viewModel::toggleInfoExpanded,
				callbacks = importCallbacks(viewModel, target.busy),
				scrollState = scrollState,
				modifier = Modifier.weight(1f).headerShadow(),
			) {
				ImportActionBar(
					busy = target.busy,
					imported = target.imported,
					onCancel = onExit,
					onImport = viewModel::importNow,
					onDone = { onDone(viewModel.targetPathIds) },
				)
			}
		}
		// the snackbar sits above the action strip.
		SnackbarHost(
			hostState = snackbar,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = ActionStripHeight),
		)
	}
}

/** The mover rows only open containers, and a description still opens its popup. */
private fun importCallbacks(viewModel: ImportTargetViewModel, busy: Boolean): ItemListCallbacks =
	ItemListCallbacks(
		onItemClick = { ui -> if (!busy) viewModel.onItemClick(ui.payload) },
		onInfo = { ui -> MessagePopup(ui.description) },
		onInnerInfo = { _, line -> MessagePopup(line.description) },
	)
