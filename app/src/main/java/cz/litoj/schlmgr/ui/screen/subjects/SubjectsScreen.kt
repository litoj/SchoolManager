package cz.litoj.schlmgr.ui.screen.subjects

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.TopBarSearchField
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.FlipConflictPopup
import cz.litoj.schlmgr.ui.popup.ReferencePopup
import cz.litoj.schlmgr.ui.popup.SourcePickPopup
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.MainLaunchTarget
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarMoreButton
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.SearchUi

private fun creatorKind(kind: RowKind) = when (kind) {
	RowKind.SUBJECT -> CreatorPopup.Kind.NEW_SUBJECT
	RowKind.CHAPTER -> CreatorPopup.Kind.NEW_CHAPTER
	RowKind.NOTE -> CreatorPopup.Kind.NEW_NOTE
	RowKind.WORD -> CreatorPopup.Kind.NEW_WORD
}

@Composable
fun SubjectsScreen(
	viewModel: MainExplorerViewModel,
	openDrawer: () -> Unit,
	onOpenTest: () -> Unit,
	onExitApp: () -> Unit,
	launchTarget: MainLaunchTarget? = null,
	onLaunchHandled: () -> Unit = {},
) {
	val context = LocalContext.current
	// the local resources avoid stale cached resources.
	val resources = LocalResources.current
	val state by viewModel.state.collectAsStateWithLifecycle()
	val title = state.breadcrumbs.lastOrNull() ?: stringResource(R.string.menu_objects)
	val query by viewModel.query.collectAsStateWithLifecycle()
	val listState by viewModel.listState.collectAsStateWithLifecycle()
	val selecting = listState.selectionActive
	// the mode decides the chrome, see [SearchUi].
	val fieldOpen = state.searchUi == SearchUi.TYPING
	val searchVisible = state.breadcrumbs.isNotEmpty() && !viewModel.pasteArmed && !fieldOpen
	val menuKind = menuKindFor(listState.variant == ExplorerRowVariant.Search, state.breadcrumbs.size)

	// the pickers for the import and export requests.
	val createWordFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain"),
	) { uri -> if (uri != null) viewModel.onWordTargetPicked(uri) }
	val importDirLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocumentTree(),
	) { uri -> if (uri != null) viewModel.onDirPicked(uri) }
	val importAnyFile = rememberLauncherForActivityResult(
		ActivityResultContracts.OpenDocument(),
	) { uri -> if (uri != null) viewModel.onAnyFilePicked(uri) }
	val createJsonFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/hjson"),
	) { uri -> if (uri != null) viewModel.onJsonTargetPicked(uri) }

	// the notification permission for the progress bar.
	// the action runs on grant and on denial
	val notificationPermission = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestPermission(),
	) {
		viewModel.runAfterPermission()
	}

	fun withProgressPermission(action: () -> Unit) {
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
				context, Manifest.permission.POST_NOTIFICATIONS)
			!= PackageManager.PERMISSION_GRANTED
		) {
			// a refused request keeps the older action, so its dialog must not open
			if (viewModel.requestWithPermission(action))
				notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
		} else action()
	}

	fun importFile() {
		importAnyFile.launch(
			arrayOf(
				"text/plain", resources.getString(R.string.json_import_types),
				"application/json", "application/octet-stream",
			)
		)
	}

	LaunchedEffect(viewModel) {
		viewModel.requests.collect { request ->
			when (request) {
				MainExplorerViewModel.Request.OpenTest -> onOpenTest()
				MainExplorerViewModel.Request.PickImportDir ->
					withProgressPermission { importDirLauncher.launch(null) }
				MainExplorerViewModel.Request.PickImportFile ->
					withProgressPermission { importFile() }
				is MainExplorerViewModel.Request.PickWordExport ->
					withProgressPermission { createWordFile.launch(request.fileName) }
				is MainExplorerViewModel.Request.PickJsonExport ->
					withProgressPermission {
						createJsonFile.launch(request.fileName + resources.getString(R.string.json_export_ext))
					}
				is MainExplorerViewModel.Request.EditWord -> {
					val popup = CreatorPopup(CreatorPopup.Kind.EDIT, request.row, CreatorContent.Word(request.rows))
					popup.setOkListener { result -> viewModel.commitWordEdit(request.row, result) }
				}
				is MainExplorerViewModel.Request.EditItem -> {
					val popup = CreatorPopup(CreatorPopup.Kind.EDIT, request.row, CreatorContent.Simple)
					popup.setOkListener { result -> viewModel.commitItemEdit(request.row, result) }
				}
				is MainExplorerViewModel.Request.CreateItem -> {
					val popup = CreatorPopup(
						creatorKind(request.kind), null,
						if (request.kind == RowKind.WORD) CreatorContent.Word(emptyList()) else CreatorContent.Simple,
					)
					popup.setOkListener { result -> viewModel.commitNewItem(request.kind, result) }
				}
				is MainExplorerViewModel.Request.ForkReferences -> {
					val popup = ReferencePopup(request.references)
					popup.setOkListener { kept ->
						viewModel.commitForkReferences(request.row, request.edit, kept)
					}
				}
				is MainExplorerViewModel.Request.ResolveFlipConflicts -> {
					val popup = FlipConflictPopup(request.analysis.conflicts)
					popup.setOkListener { result -> viewModel.commitFlipResolutions(request.analysis, result) }
					popup.onCanceled = viewModel::cancelFlipRound
				}
				is MainExplorerViewModel.Request.ConfirmDelete ->
					ContinuePopup {
						viewModel.performDelete(request.doomed, resources.getString(R.string.progress_delete) +
							if (request.doomed.size == 1) request.doomed[0].item.name
							else resources.getQuantityString(
								R.plurals.progress_items, request.doomed.size, request.doomed.size))
					}
				is MainExplorerViewModel.Request.PickSource ->
					SourcePickPopup(request.paths, request.origin, viewModel::navigateToSource)
			}
		}
	}

	// the import mover hands over its destination after the worker finished.
	LaunchedEffect(launchTarget) {
		val target = launchTarget ?: return@LaunchedEffect
		viewModel.openImportTarget(target.path, target.scrollToBottom)
		onLaunchHandled()
	}

	ExplorerBackHandler(viewModel, onExitApp)

	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(onNavigate = openDrawer)
			if (fieldOpen) TopBarSearchField(query, viewModel::onQueryChange, viewModel::onQuerySubmit, viewModel::closeSearchField, Modifier.weight(1f))
			else TopBarTitle(title)
			TopBarIconButton(
				icon = if (selecting) R.drawable.ic_check_all else R.drawable.ic_search,
				visible = selectAllButtonVisible(selecting, searchVisible),
			) {
				if (selecting) viewModel.onSelectAllToggle() else viewModel.openSearchField()
			}
			TopBarMoreButton(
				subjectsMenu(menuKind, selecting),
			) { itemId ->
				viewModel.onMenuItemClick(itemId)
			}
		}
		SubjectsContent(viewModel, Modifier.weight(1f).headerShadow())
	}
}

internal fun selectAllButtonVisible(selecting: Boolean, searchVisible: Boolean): Boolean =
	selecting || searchVisible

