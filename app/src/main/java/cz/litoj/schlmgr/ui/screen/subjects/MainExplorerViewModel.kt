package cz.litoj.schlmgr.ui.screen.subjects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.R
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipAnalysis
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.db.ratio
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.SiblingSwitch
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The subjects content, the row intents and the more menu dispatch.
 * - see [SubjectsPaste], [SubjectsWordEdit], [SubjectsFlip], [SubjectsDelete],
 *   [SubjectsTransfer] and [SubjectsNavigation].
 * - the file pickers stay behind [Request]s.
 */
class MainExplorerViewModel(history: NavigationHistory, io: CoroutineDispatcher = Dispatchers.IO) :
	ExplorerViewModel<MainExplorerViewModel.Request>(history, io = io) {

	/** A view-only step: picker, navigation or dialog. */
	sealed interface Request {
		data object OpenTest : Request

		data object PickImportDir : Request

		data object PickImportFile : Request

		data class PickWordExport(val fileName: String) : Request

		data class PickJsonExport(val fileName: String) : Request

		data class EditWord(val row: ItemRow, val rows: List<TranslationRow>) : Request

		data class EditItem(val row: ItemRow) : Request

		/**
		 * The created item appends, and the database places it.
		 */
		data class CreateItem(val kind: RowKind) : Request

		data class ForkReferences(val row: ItemRow, val edit: WordEdit, val references: List<DbItem>) : Request

		data class ResolveFlipConflicts(val analysis: FlipAnalysis) : Request

		data class ConfirmDelete(val doomed: List<ItemRow>) : Request

		data class PickSource(val paths: List<List<DbItem>>, val origin: List<DbItem>) : Request
	}

	enum class MenuKind { ROOT, CONTAINER, SEARCH }

	private val paste = SubjectsPaste(viewModelScope, io, PasteHost())

	val pasteArmed: Boolean get() = paste.armed

	override fun pasteArmed() = pasteArmed

	private inner class PasteHost : SubjectsPaste.Host {
		override fun items() = this@MainExplorerViewModel.items()
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
		override val path get() = history.path
		override val searching get() = this@MainExplorerViewModel.searching
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun reloadContent() = this@MainExplorerViewModel.reloadContent()
		override fun notifyChanged() = this@MainExplorerViewModel.notifyChanged()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
		override fun refreshReorderable() = this@MainExplorerViewModel.refreshReorderable()
	}

	private val wordEdit = SubjectsWordEdit(viewModelScope, io, WordEditHost())

	private inner class WordEditHost : SubjectsWordEdit.Host {
		override fun items() = this@MainExplorerViewModel.items()
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override val path get() = history.path
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
		override fun reloadContent() = this@MainExplorerViewModel.reloadContent()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
		override fun addItem(item: ItemRow) = this@MainExplorerViewModel.addItem(item)
		override fun addItem(index: Int, item: ItemRow) = this@MainExplorerViewModel.addItem(index, item)
	}

	private val flip = SubjectsFlip(viewModelScope, io, FlipHost())

	private inner class FlipHost : SubjectsFlip.Host {
		override fun items() = this@MainExplorerViewModel.items()
		override val searching get() = this@MainExplorerViewModel.searching
		override val path get() = history.path
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
		override fun reloadContent() = this@MainExplorerViewModel.reloadContent()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
	}

	private val delete = SubjectsDelete(viewModelScope, io, DeleteHost())

	private inner class DeleteHost : SubjectsDelete.Host {
		override fun items() = this@MainExplorerViewModel.items()
		override val searching get() = this@MainExplorerViewModel.searching
		override val path get() = history.path
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun removeItems(items: Collection<ItemRow>) = this@MainExplorerViewModel.removeItems(items)
		override fun refreshSearchRows() = this@MainExplorerViewModel.refreshSearchRows()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
	}

	/** Work that outlives the model drops. */
	private fun postToUi(work: Runnable) = viewModelScope.launch { work.run() }

	private val ctx get() = GlobalDependencies.appContext

	private var pendingAfterPermission: (() -> Unit)? = null

	/** A second request while one waits is refused and reported, so none is lost silently. */
	fun requestWithPermission(action: () -> Unit) {
		if (pendingAfterPermission != null) {
			UserMessages.showMsg(ctx.getString(R.string.permission_pending), ctx.getString(R.string.permission_pending))
			return
		}
		pendingAfterPermission = action
	}

	fun runAfterPermission() {
		pendingAfterPermission?.invoke()
		pendingAfterPermission = null
	}

	// starts at the shared position, which survives rotation.
	init {
		setContent(history.path.lastOrNull())
	}

	private fun reloadContent() {
		if (searching) {
			refreshSearchRows()
			return
		}
		setContent(history.path.lastOrNull())
	}

	/** The import mover hands over its destination after the worker finished. */
	fun openImportTarget(pathIds: List<Int>, scrollToBottom: Boolean) {
		viewModelScope.launch(io) {
			val path = pathIds.mapNotNull { ItemRepository.get(it) }
				.filter { it.type == ItemKind.CHAPTER }
				.toMutableList()
			postToUi {
				openChain(path)
				onChange()
				if (scrollToBottom) requestScrollToBottom()
			}
		}
	}

	// see [ExplorerViewModel.onBackRequested].

	/**
	 * The New actions refuse during search.
	 */
	fun onMenuItemClick(itemId: Int): Boolean = when (itemId) {
		MenuId.SORT_ALPHA_AZ -> { sort(compareBy { it.toShow.lowercase() }); true }
		MenuId.SORT_ALPHA_ZA -> { sort(compareBy<ItemRow> { it.toShow.lowercase() }.reversed()); true }
		MenuId.SORT_SF_01 -> { sort(compareBy { it.item.ratio }); true }
		MenuId.SORT_SF_10 -> { sort(compareBy<ItemRow> { it.item.ratio }.reversed()); true }
		MenuId.SORT_PRIORITY -> { sort(compareByDescending { TestEngine.urgency(it.item) }); true }
		MenuId.SORT_LENGTH_01 -> { sort(compareBy { it.toShow.length }); true }
		MenuId.SORT_LENGTH_10 -> { sort(compareBy<ItemRow> { it.toShow.length }.reversed()); true }
		MenuId.SORT_DEFAULT -> { reloadDefaultOrder(); true }
		MenuId.NEW_SUBJECT -> newAction { newSubject() }
		MenuId.NEW_CHAPTER -> newAction { newChapter() }
		MenuId.NEW_WORD -> newAction { newWord() }
		MenuId.NEW_NOTE -> newAction { newNote() }
		MenuId.IMPORT_DIR -> { emitRequest(Request.PickImportDir); true }
		MenuId.IMPORT_FILE -> { emitRequest(Request.PickImportFile); true }
		MenuId.EXPORT_WORD -> { exportWordFile(); true }
		MenuId.EXPORT_JSON -> { exportJsonFile(); true }
		MenuId.FLIP -> { flipSelected(); true }
		MenuId.REFERENCE -> { move(true); true }
		else -> false
	}

	private fun newAction(action: () -> Unit): Boolean {
		if (searching) return false
		action()
		return true
	}

	/** A pending navigation read refuses a reorder. */
	private fun sort(comparator: Comparator<ItemRow>) {
		if (loadJob?.isActive == true) return
		val items = items().toMutableList()
		items.sortWith(comparator)
		applyOrder(items)
		refreshInfo()
	}

	private fun reloadDefaultOrder() {
		val container = history.path.lastOrNull()
		setContent(container)
	}

	fun onItemClick(item: ItemRow?) {
		val row = item ?: return
		when (row.item.type) {
			ItemKind.WORD -> flipWord(row)
			ItemKind.CHAPTER -> {
				clearSelection()
				history.hop(row.item)
				setContent(row.item)
			}
			ItemKind.NOTE -> copyNote(row)
			else -> {}
		}
	}

	private fun copyNote(item: ItemRow) {
		val text = item.item.description.ifEmpty { item.item.name }
		val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText(item.item.name, text))
		UserMessages.showNotice(UiNotice.NoteCopied)
	}

	fun onItemLongClick(item: ItemRow?) {
		val row = item ?: return
		if (!pasteArmed) toggleSelect(row)
	}

	fun onSearchHitClick(ui: ItemUiModel) {
		val row = ui.payload ?: return
		if (row.item.type == ItemKind.WORD) {
			flipWord(row)
			return
		}
		if (row.item.type == ItemKind.NOTE) {
			copyNote(row)
			return
		}
		if (row.item.type != ItemKind.CHAPTER) {
			endSearch()
			return
		}
		val path = mutableListOf<DbItem>()
		ui.trackPath?.let(path::addAll)
		path.add(row.item)
		openChain(path)
	}

	/** The container of the selected hit: one home opens and several pick. */
	private fun goToSelectedHit() {
		val row = items().singleOrNull(ItemRow::selected) ?: return
		navigation.goToSelectedHit(row)
	}

	private val navigation = SubjectsNavigation(viewModelScope, io, history, NavigationHost())

	private inner class NavigationHost : SubjectsNavigation.Host {
		override val searching get() = this@MainExplorerViewModel.searching
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override val pasteArmed get() = this@MainExplorerViewModel.pasteArmed
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun setContent(container: DbItem?) = this@MainExplorerViewModel.setContent(container)
		override fun openChain(path: MutableList<DbItem>) = this@MainExplorerViewModel.openChain(path)
		override fun switchedSibling(step: Int, delta: Int) {
			updateState { it.copy(sibling = SiblingSwitch(step, delta)) }
		}
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
	}

	fun onReferenceClick(ui: ItemUiModel) = navigation.onReferenceClick(ui)

	fun openSibling(delta: Int) = navigation.openSibling(delta)

	fun navigateToSource(path: List<DbItem>) = navigation.navigateToSource(path)

	fun persistOrder() {
		if (searching || listState.value.pickerMode) return
		val parentId = history.path.lastOrNull()?.id ?: ROOT_ID
		val ordered = items().map { it.item.id }
		viewModelScope.launch(io) {
			ItemRepository.setOrder(parentId, ordered)
			postToUi {
				// a late read retries via the moved generation.
				orderPersisted()
				// the drop ends the selection the drag started.
				clearSelection()
				refreshInfo()
			}
		}
	}

	fun move(reference: Boolean) = paste.move(reference)

	fun doPaste() = paste.doPaste()

	fun cancelPaste() = paste.cancelPaste()

	fun onEditAction() = wordEdit.onEditAction()

	fun onGoToAction() = goToSelectedHit()

	/**
	 * A false verdict keeps the popup open, see [SubjectsWordEdit.commitWordEdit].
	 */
	fun commitWordEdit(row: ItemRow, result: CreatorResult): Boolean = wordEdit.commitWordEdit(row, result)

	fun commitForkReferences(row: ItemRow, edit: WordEdit, kept: List<Int>) = wordEdit.commitForkReferences(row, edit, kept)

	/**
	 * A false verdict keeps the popup open, see [SubjectsWordEdit.commitItemEdit].
	 */
	fun commitItemEdit(row: ItemRow, result: CreatorResult): Boolean = wordEdit.commitItemEdit(row, result)

	fun onDeleteAction() = delete.onDeleteAction()

	fun performDelete(doomed: List<ItemRow>, title: String? = null) = delete.performDelete(doomed, title)

	fun flipSelected() = flip.flipSelected()

	fun commitFlipResolutions(analysis: FlipAnalysis, resolutions: List<FlipResolution>): Boolean = flip.commitFlipResolutions(analysis, resolutions)

	fun cancelFlipRound() = flip.cancelFlipRound()

	/** Selection stays armed for export. */
	fun prepareTest() {
		val seeds = items().filter(ItemRow::selected).map(TestSetupState::seedOf)
		if (seeds.isEmpty()) return
		viewModelScope.launch(io) {
			val refused = TestSetupState.fillFromSelection(seeds)
			if (refused.isNotEmpty()) {
				postToUi { UserMessages.showNotice(UiNotice.OneSubjectOnly(TestSetupState.pickedSubject()?.name)) }
				return@launch
			}
			postToUi {
				TestSetupState.markOpenedWithSelection()
				emitRequest(Request.OpenTest)
			}
		}
	}

	private fun newSubject() = wordEdit.newSubject()

	private fun newChapter() = wordEdit.newChapter()

	private fun newNote() = wordEdit.newNote()

	private fun newWord() = wordEdit.newWord()

	/**
	 * A false verdict keeps the popup open, see [SubjectsWordEdit.commitNewItem].
	 */
	fun commitNewItem(kind: RowKind, result: CreatorResult): Boolean = wordEdit.commitNewItem(kind, result)

	private val transfer = SubjectsTransfer(viewModelScope, io, TransferHost())

	private inner class TransferHost : SubjectsTransfer.Host {
		override val ctx get() = this@MainExplorerViewModel.ctx
		override fun items() = this@MainExplorerViewModel.items()
		override val path get() = history.path
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
		override fun setContent(container: DbItem?) = this@MainExplorerViewModel.setContent(container)
		override fun reloadContent() = this@MainExplorerViewModel.reloadContent()
		override fun requestScrollToBottom() = this@MainExplorerViewModel.requestScrollToBottom()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
	}

	private fun exportWordFile() = transfer.exportWordFile()

	private fun exportJsonFile() = transfer.exportJsonFile()

	internal fun exportRows(): List<DbItem> = transfer.exportRows()

	fun onAnyFilePicked(uri: Uri) = transfer.onAnyFilePicked(uri)

	fun onWordTargetPicked(uri: Uri) = transfer.onWordTargetPicked(uri)

	fun onJsonTargetPicked(uri: Uri) = transfer.onJsonTargetPicked(uri)

	fun onDirPicked(uri: Uri) = transfer.onDirPicked(uri)
}
