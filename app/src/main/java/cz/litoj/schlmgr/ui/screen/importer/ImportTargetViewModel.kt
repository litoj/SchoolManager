package cz.litoj.schlmgr.ui.screen.importer

import android.net.Uri
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The import mover: the explorer chooses the target container and the worker lands the file.
 * - the bar stays visible, since every move needs a destination and an action.
 * - the picked file URI stays fixed for the screen, the provider name
 *   resolves once on io.
 */
class ImportTargetViewModel(
	private val fileUri: Uri,
	io: CoroutineDispatcher = Dispatchers.IO,
	nameOf: (Uri) -> String = {
		FileGateway.displayName(GlobalDependencies.appContext, it, "")
	},
) : ExplorerViewModel<Nothing>(NavigationHistory(), io = io) {

	/**
	 * The state of the mover bar and the resolved document name.
	 * - the name starts empty, the provider answer fills it.
	 */
	data class TargetState(
		val busy: Boolean = false,
		val imported: Boolean = false,
		val fileName: String = "",
	)

	private val _targetState = MutableStateFlow(TargetState())
	val targetState: StateFlow<TargetState> = _targetState.asStateFlow()

	/** The path the main screen opens after Done. */
	val targetPathIds: List<Int> get() = history.path.map { it.id }

	init {
		setContent(null)
		// the provider query never runs on the main thread
		viewModelScope.launch(io) { _targetState.update { it.copy(fileName = nameOf(fileUri)) } }
	}

	/** Only a container is a destination, so a word or note click does nothing. */
	fun onItemClick(row: ItemRow?) {
		val item = row?.item ?: return
		if (_targetState.value.busy || item.type != ItemKind.CHAPTER) return
		history.hop(item)
		setContent(item)
		onChange()
	}

	fun importNow() {
		if (_targetState.value.busy || _targetState.value.imported) return
		_targetState.update { it.copy(busy = true) }
		viewModelScope.launch {
			val context = GlobalDependencies.appContext
			val open = history.path.lastOrNull()
			val worker = withContext(io) {
				ImporterFactory.createPickedImport(
					context, ::postToUi, fileUri, _targetState.value.fileName,
					{ open }, { open == null },
					{ reloadAfterImport(null) },
					{ reloadAfterImport(open) },
					{
						reloadAfterImport(open)
						refreshInfo()
					},
				)
			}
			worker.onImported = { result -> postToUi { openSingleTop(result.topLevel) } }
			val thread = worker.start()
			withContext(io) { thread.join() }
			_targetState.update { it.copy(busy = false) }
		}
	}

	private fun postToUi(work: Runnable) {
		viewModelScope.launch { work.run() }
	}

	private fun reloadAfterImport(container: DbItem?) {
		requestScrollToBottom()
		setContent(container)
		_targetState.update { it.copy(imported = true) }
	}

	/** A file with one top-level container opens it, so Done lands on the imported content. */
	private fun openSingleTop(topLevel: List<DbItem>) {
		val item = topLevel.singleOrNull() ?: return
		if (item.type != ItemKind.CHAPTER) return
		history.hop(item)
		setContent(item)
		onChange()
		requestScrollToBottom()
	}
}
