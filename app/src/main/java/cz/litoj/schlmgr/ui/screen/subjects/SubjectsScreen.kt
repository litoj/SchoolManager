package cz.litoj.schlmgr.ui.screen.subjects

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.ui.component.ExplorerSearchControls
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.popup.CreatorContent
import cz.litoj.schlmgr.ui.popup.CreatorPopup
import cz.litoj.schlmgr.ui.popup.FlipConflictPopup
import cz.litoj.schlmgr.ui.popup.ReferencePopup
import cz.litoj.schlmgr.ui.popup.SourcePickPopup
import cz.litoj.schlmgr.ui.shell.ExplorerBackHandler
import cz.litoj.schlmgr.ui.shell.MainLaunchTarget
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.headerShadow
import cz.litoj.schlmgr.ui.shell.pickExplorerMenu
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.explorerMenu
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig

private fun creatorKind(kind: RowKind) = when (kind) {
	RowKind.SUBJECT -> CreatorPopup.Kind.NEW_SUBJECT
	RowKind.CHAPTER -> CreatorPopup.Kind.NEW_CHAPTER
	RowKind.NOTE -> CreatorPopup.Kind.NEW_NOTE
	RowKind.WORD -> CreatorPopup.Kind.NEW_WORD
}

@Composable
fun SubjectsScreen(
	viewModel: MainExplorerViewModel,
	settings: Settings,
	userMessages: UserMessages,
	openDrawer: () -> Unit,
	onOpenTest: () -> Unit,
	onExitApp: () -> Unit,
	launchTarget: MainLaunchTarget? = null,
	onLaunchHandled: () -> Unit = {},
	onPickReferences: () -> Unit = {},
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
	// the field stays through the results, since the query marks what the list shows
	val fieldOpen = state.searchUi != SearchUi.OFF
	val searchBlockedReason = viewModel.searchBlockedReason
	val exportBlockedReason = viewModel.exportBlockedReason

	// the pickers for the import and export requests.
	val createWordFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain"),
	) { uri -> if (uri != null) viewModel.onWordTargetPicked(uri) }
	val importAnyFile = rememberLauncherForActivityResult(OpenEditableDocument()) { uri ->
		if (uri != null) {
			FileGateway.holdForEdit(context, uri)
			viewModel.onAnyFilePicked(uri)
		}
	}
	val createJsonFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/hjson"),
	) { uri -> if (uri != null) viewModel.onJsonTargetPicked(uri) }

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
				MainExplorerViewModel.Request.PickReferences -> onPickReferences()
				MainExplorerViewModel.Request.PickImportFile -> importFile()
				is MainExplorerViewModel.Request.PickWordExport ->
					createWordFile.launch(request.fileName)
				is MainExplorerViewModel.Request.PickJsonExport ->
					createJsonFile.launch(request.fileName + resources.getString(R.string.json_export_ext))
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
					SourcePickPopup(request.paths, request.origin) { path ->
						viewModel.navigateToSource(path, request.pulseItemId)
					}
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

	BoxWithConstraints(Modifier.fillMaxSize()) {
		Column(Modifier.fillMaxSize()) {
		TopBar {
			// the arrow leaves the deepest layer: the field, a selection or an armed move
			TopBarNav(
				userMessages = userMessages,
				up = fieldOpen || selecting || viewModel.pasteArmed,
				onNavigate = when {
					fieldOpen -> viewModel::cancelSearch
					selecting -> viewModel::clearSelection
					viewModel.pasteArmed -> viewModel::cancelPaste
					else -> openDrawer
				},
				contentDescription = stringResource(
					if (fieldOpen || selecting || viewModel.pasteArmed) R.string.back
					else R.string.menu_label,
				),
			)
		ExplorerSearchControls(
			userMessages = userMessages,
			title = title,
			query = query,
			fieldOpen = fieldOpen,
			searchBlockedReason = searchBlockedReason,
			entries = explorerMenu(
				viewModel.menuKind, selecting = false, listState.singleSelected,
				ActionConfig.load(settings), exportBlockedReason,
				filterActive = (state.info as? InfoBar.Search)?.criteria?.isNotEmpty() == true,
				searchBlockedReason = searchBlockedReason,
				selectAllBlockedReason = viewModel.selectAllBlockedReason,
			),
			width = this@BoxWithConstraints.maxWidth,
			onOpenSearch = { viewModel.openSearchField() },
			onQueryChange = viewModel::onQueryChange,
			onQuerySubmit = viewModel::onQuerySubmit,
			onDismissSearch = viewModel::dismissSearch,
			onMenuPick = { pickExplorerMenu(viewModel, it) },
		)
		}
		SubjectsContent(viewModel, settings, userMessages, exportBlockedReason, Modifier.weight(1f).headerShadow())
		}
	}
}

/**
 * The document of an import, asked for with write access.
 * - the failure popup offers the file to an outside editor, which has to reach the same file.
 */
internal class OpenEditableDocument : ActivityResultContract<Array<String>, Uri?>() {

	override fun createIntent(context: Context, input: Array<String>): Intent =
		Intent(Intent.ACTION_OPEN_DOCUMENT)
			.addCategory(Intent.CATEGORY_OPENABLE)
			.setType("*/*")
			.putExtra(Intent.EXTRA_MIME_TYPES, input)
			.addFlags(
				Intent.FLAG_GRANT_READ_URI_PERMISSION or
					Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
					Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
			)

	override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
		if (resultCode != Activity.RESULT_OK) null else intent?.data
}

