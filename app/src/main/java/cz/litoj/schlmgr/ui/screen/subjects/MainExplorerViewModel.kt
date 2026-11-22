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
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.menuIdOf
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.RowKind
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

		data object PickImportFile : Request

		data object PickReferences : Request

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

		data class PickSource(
			val paths: List<List<DbItem>>,
			val origin: List<DbItem>,
			val pulseItemId: Int?,
		) : Request
	}

	/** The chapter the reference picker writes into, or null for the test-source picker. */
	var referenceTarget: DbItem? = null
		private set

	/** Opens the shared picker into the currently open chapter. */
	fun pickReferences() {
		referenceTarget = history.path.lastOrNull() ?: return
		emitRequest(Request.PickReferences)
	}

	/** Returning from the picker either leaves the list alone or refreshes new links. */
	fun finishReferencePicker(committed: Boolean) {
		if (referenceTarget == null) return
		referenceTarget = null
		if (committed) setContent(history.path.lastOrNull())
	}

	/**
	 * The one implementation of the satellite doors, see [SubjectsHost].
	 * - the adapters below extend it through delegation.
	 * - it must initialize first, since the satellite constructors capture it.
	 */
	private inner class SharedHost : SubjectsHost {
		override fun items() = this@MainExplorerViewModel.items()
		override val path get() = history.path
		override val searching get() = this@MainExplorerViewModel.searching
		override fun emitRequest(request: Request) = this@MainExplorerViewModel.emitRequest(request)
		override fun postToUi(work: Runnable) {
			this@MainExplorerViewModel.postToUi(work)
		}
		override fun clearSelection() = this@MainExplorerViewModel.clearSelection()
		override fun reloadContent() = this@MainExplorerViewModel.reloadContent()
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
	}

	private val shared = SharedHost()

	private val paste = SubjectsPaste(viewModelScope, io, PasteHost())

	val pasteArmed: Boolean get() = paste.armed

	/** What the armed paste carries, its kind and count for the strip status. */
	val armedReferences: Boolean get() = paste.armedReferences

	val armedCount: Int get() = paste.armedCount

	/** Why the open container refuses the armed paste, 0 when it may run. */
	val pasteBlockedReason: Int get() = paste.blockedReason

	override fun pasteArmed() = pasteArmed

	/** The armed paste carries the batch, so its rows gather across folders. */
	override fun gathering() = pasteArmed

	override fun batchChanged() = paste.onBatchChanged()

	/** Why Select all is muted, 0 when it may run. */
	val selectAllBlockedReason: Int get() = if (pasteArmed) R.string.unavailable_paste_armed else 0

	override fun onSelectAllToggle() {
		if (selectAllBlockedReason == 0) super.onSelectAllToggle()
	}

	private inner class PasteHost : SubjectsPaste.Host, SubjectsHost by shared {
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override fun notifyChanged() = this@MainExplorerViewModel.notifyChanged()
		override fun refreshReorderable() = this@MainExplorerViewModel.refreshReorderable()
		override fun batchRows() = this@MainExplorerViewModel.batchRows()
		override fun startBatch(rows: List<ItemRow>) = this@MainExplorerViewModel.startBatch(rows)
		override fun clearBatch() = this@MainExplorerViewModel.clearBatch()
		override fun batchDragging() = this@MainExplorerViewModel.batchDragging()
	}

	private val wordEdit = SubjectsWordEdit(viewModelScope, io, WordEditHost())

	private inner class WordEditHost : SubjectsWordEdit.Host, SubjectsHost by shared {
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override fun notifyChanged() = this@MainExplorerViewModel.notifyChanged()
		override fun addItem(item: ItemRow) = this@MainExplorerViewModel.addItem(item)
		override fun addItem(index: Int, item: ItemRow) = this@MainExplorerViewModel.addItem(index, item)
		override fun removeItems(items: Collection<ItemRow>) = this@MainExplorerViewModel.removeItems(items)
		override fun openContainer(container: DbItem) = this@MainExplorerViewModel.openContainer(container)
	}

	// the flip needs no own door, only the shared ones
	private val flip = SubjectsFlip(viewModelScope, io, FlipHost())

	private inner class FlipHost : SubjectsFlip.Host, SubjectsHost by shared

	private val delete = SubjectsDelete(viewModelScope, io, DeleteHost())

	private inner class DeleteHost : SubjectsDelete.Host, SubjectsHost by shared {
		override fun removeItems(items: Collection<ItemRow>) = this@MainExplorerViewModel.removeItems(items)
		override fun refreshSearchRows() = this@MainExplorerViewModel.refreshSearchRows()
	}

	/** Work that outlives the model drops. */
	private fun postToUi(work: Runnable) = viewModelScope.launch { work.run() }

	private val ctx get() = GlobalDependencies.appContext

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

	/**
	 * Opens the container the caller just created: the same landing as tapping it.
	 * - the selection leaves, the path grows by one, and the new content loads.
	 */
	fun openContainer(container: DbItem) {
		clearSelection()
		history.hop(container)
		setContent(container)
		onChange()
	}

	// see [ExplorerViewModel.onBackRequested].

	/**
	 * The New actions refuse during search.
	 */
	override fun onMenuItemClick(itemId: Int): Boolean {
		if (super.onMenuItemClick(itemId)) return true
		return when (itemId) {
			MenuId.NEW_SUBJECT -> newAction { newSubject() }
			MenuId.NEW_CHAPTER -> newAction { newChapter() }
			MenuId.NEW_WORD -> newAction { newWord() }
			MenuId.NEW_NOTE -> newAction { newNote() }
			MenuId.IMPORT_FILE -> { emitRequest(Request.PickImportFile); true }
			MenuId.EXPORT -> { transfer.openExportSheet(); true }
			MenuId.FLIP -> { flipSelected(); true }
			MenuId.REFERENCE -> { move(true); true }
			MenuId.TEST -> { prepareTest(); true }
			MenuId.CUT -> { move(false); true }
			MenuId.EDIT -> { onEditAction(); true }
			MenuId.DELETE -> { onDeleteAction(); true }
			MenuId.GOTO -> { onGoToAction(); true }
			else -> false
		}
	}

	/** Runs one strip action, whose choice ends the selection whether it runs or refuses. */
	fun onSelectionAction(action: SelectionAction) = pickSelectionAction(menuIdOf(action))

	/** The strip overflow names the same actions, so it ends the selection the same way. */
	fun onSelectionMenuPick(itemId: Int) = pickSelectionAction(itemId)

	private fun pickSelectionAction(itemId: Int) {
		onMenuItemClick(itemId)
		clearSelection()
	}

	/** Every navigation lands here, so the armed paste re-reads its target. */
	override fun setContent(container: DbItem?, pulseItemId: Int?) {
		super.setContent(container, pulseItemId)
		paste.refreshBlock()
	}

	/**
	 * The drawer press on the open explorer: the back ladder straight to the subjects list.
	 * - the armed paste rides along, only its cancel drops it.
	 */
	fun goToRoot() {
		clearSelection()
		if (searching) endSearch()
		history.reset()
		setContent(null)
		onChange()
	}

	private fun newAction(action: () -> Unit): Boolean {
		if (searching) return false
		action()
		return true
	}

	private fun copyNote(item: ItemRow) {
		val text = item.item.description.ifEmpty { item.item.name }
		val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText(item.item.name, text))
		UserMessages.showNotice(UiNotice.NoteCopied)
	}

	/** A note copies its text, and an inert search hit ends the run. */
	override fun onOtherRowClick(row: ItemRow, searchHit: Boolean) {
		when {
			row.item.type == ItemKind.NOTE -> copyNote(row)
			searchHit -> endSearch()
		}
	}

	/** The container of the selected hit: one home opens and several pick. */
	private fun goToSelectedHit() {
		val row = items().singleOrNull(ItemRow::selected) ?: return
		navigation.goToSelectedHit(row)
	}

	private val navigation = SubjectsNavigation(viewModelScope, io, history, NavigationHost())

	private inner class NavigationHost : SubjectsNavigation.Host, SubjectsHost by shared {
		override fun selectionCount() = this@MainExplorerViewModel.selectionCount()
		override val pasteArmed get() = this@MainExplorerViewModel.pasteArmed
		override fun openChain(path: MutableList<DbItem>, pulseItemId: Int?) =
			this@MainExplorerViewModel.openChain(path, pulseItemId)
	}

	fun onReferenceClick(index: Int) = navigation.onReferenceClick(index)

	fun navigateToSource(path: List<DbItem>, pulseItemId: Int? = null) =
		navigation.navigateToSource(path, pulseItemId)

	fun persistOrder() {
		if (searching || listState.value.pickerMode) return
		val parentId = history.path.lastOrNull()?.id ?: ROOT_ID
		val ordered = items().map { it.item.id }
		// the drop ends the selection at once, so the strip never flashes before the io lands
		clearSelection()
		viewModelScope.launch(io) {
			ItemRepository.setOrder(parentId, ordered)
			postToUi {
				// a late read retries via the moved generation.
				orderPersisted()
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
				clearSelection()
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

	private inner class TransferHost : SubjectsTransfer.Host, SubjectsHost by shared {
		override val ctx get() = this@MainExplorerViewModel.ctx
		override fun setContent(container: DbItem?) = this@MainExplorerViewModel.setContent(container)
		override fun requestScrollToBottom() = this@MainExplorerViewModel.requestScrollToBottom()
	}

	internal fun exportRows(): List<DbItem> = transfer.exportRows()

	/** A selection of translations alone has no export line, so the row stays and explains. */
	val exportBlockedReason: Int
		get() = if (exportRows().isEmpty()) R.string.unavailable_export_empty else 0

	fun onAnyFilePicked(uri: Uri) = transfer.onAnyFilePicked(uri)

	fun onWordTargetPicked(uri: Uri) = transfer.onWordTargetPicked(uri)

	fun onJsonTargetPicked(uri: Uri) = transfer.onJsonTargetPicked(uri)
}
