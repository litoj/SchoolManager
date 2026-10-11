package cz.litoj.schlmgr.ui.screen.subjects

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.db.ratio
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
 * Subjects content, row intents, `more_*` dispatch.
 * See [SubjectsPaste], [SubjectsWordEdit], [SubjectsTransfer], [SubjectsNavigation].
 *
 * File pickers stay behind [Request]s.
 */
class MainExplorerViewModel(history: NavigationHistory, io: CoroutineDispatcher = Dispatchers.IO) :
	ExplorerViewModel<MainExplorerViewModel.Request>(history, io = io) {

	/** View-only step — picker, navigation, dialog. */
	sealed interface Request {
		/** Open test setup screen. */
		data object OpenTest : Request

		/** Pick subject directory. */
		data object PickImportDir : Request

		/** Pick importable file. */
		data object PickImportFile : Request

		/** Pick word-list export target. */
		data class PickWordExport(val fileName: String) : Request

		/** Pick tree-JSON export target. */
		data class PickJsonExport(val fileName: String) : Request

		/** Word editor input. */
		data class EditWord(val row: ItemRow, val rows: List<TranslationRow>) : Request

		/** Name/description editor input. */
		data class EditItem(val row: ItemRow) : Request

		/**
		 * Creator input — the created item appends, the database places it.
		 */
		data class CreateItem(val kind: RowKind) : Request

		/** Pick references following forked word. */
		data class ForkReferences(val row: ItemRow, val edit: WordEdit, val references: List<DbItem>) : Request

		data class ConfirmDelete(val doomed: List<ItemRow>) : Request

		/** Pick container to jump to. */
		data class PickSource(val paths: List<List<DbItem>>) : Request
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

	/**
	 * Runs [work] on main. Drops work outliving cleared model.
	 */
	private fun postToUi(work: Runnable) = viewModelScope.launch { work.run() }

	private val ctx get() = GlobalDependencies.appContext

	private var pendingAfterPermission: (() -> Unit)? = null

	fun requestWithPermission(action: () -> Unit) {
		pendingAfterPermission = action
	}

	fun runAfterPermission() {
		pendingAfterPermission?.invoke()
		pendingAfterPermission = null
	}

	// Starts at the shared position — survives rotation.
	init {
		setContent(history.path.lastOrNull())
	}

	// ==========================================================================
	//  content loading
	// ==========================================================================

	private fun reloadContent() {
		if (searching) {
			refreshSearchRows()
			return
		}
		setContent(history.path.lastOrNull())
	}

	// ==========================================================================
	//  back dispatch
	// ==========================================================================

	// See [ExplorerViewModel.onBackRequested].

	// ==========================================================================
	//  top-bar menu dispatch
	// ==========================================================================

	/**
	 * Dispatches `more_*` menu. New actions refuse during search.
	 */
	fun onMenuItemClick(itemId: Int): Boolean = when (itemId) {
		MenuId.SORT_ALPHA_AZ -> { sort(compareBy { it.toShow.lowercase() }); true }
		MenuId.SORT_ALPHA_ZA -> { sort(compareBy<ItemRow> { it.toShow.lowercase() }.reversed()); true }
		MenuId.SORT_SF_01 -> { sort(compareBy { it.item.ratio }); true }
		MenuId.SORT_SF_10 -> { sort(compareBy<ItemRow> { it.item.ratio }.reversed()); true }
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
		MenuId.GO_TO -> { goToSelectedHit(); true }
		else -> false
	}

	/** Runs New action outside search. Returns handled flag. */
	private fun newAction(action: () -> Unit): Boolean {
		if (searching) return false
		action()
		return true
	}

	/** Reorders live list 1..n. Refuses during pending navigation read. */
	private fun sort(comparator: Comparator<ItemRow>) {
		if (loadJob?.isActive == true) return
		val items = items().toMutableList()
		items.sortWith(comparator)
		items.forEachIndexed { index, row -> row.position = index + 1 }
		load(items, isSearch = searching, isPicker = listState.value.pickerMode)
		refreshInfo()
	}

	private fun reloadDefaultOrder() {
		val container = history.path.lastOrNull()
		setContent(container)
	}

	// ==========================================================================
	//  row intents
	// ==========================================================================

	/** Word flips, chapter opens, note copies. Reference opens target directly. */
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
		Startup.showNotice(UiNotice.NoteCopied)
	}

	/** Starts selection unless paste armed. */
	fun onItemLongClick(item: ItemRow?) {
		val row = item ?: return
		if (!pasteArmed) toggleSelect(row)
	}

	/** Search hit tap — word flips, chapter opens, note copies. Otherwise ends search. */
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

	/** Opens container of selected hit. */
	private fun goToSelectedHit() {
		val row = items().singleOrNull(ItemRow::selected) ?: return
		val path = row.path ?: return
		openChain(ArrayList(path))
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
				// Late read retries via moved generation.
				orderPersisted()
				// Drop ends drag-start selection.
				clearSelection()
				refreshInfo()
			}
		}
	}

	// ==========================================================================
	//  file / edit actions (cut/paste/reference, rename, delete)
	// ==========================================================================

	fun move(reference: Boolean) = paste.move(reference)

	fun doPaste() = paste.doPaste()

	fun cancelPaste() = paste.cancelPaste()

	fun onEditAction() = wordEdit.onEditAction()

	/**
	 * `false` keeps popup open. See [SubjectsWordEdit.commitWordEdit].
	 */
	fun commitWordEdit(row: ItemRow, result: CreatorResult): Boolean = wordEdit.commitWordEdit(row, result)

	fun commitForkReferences(row: ItemRow, edit: WordEdit, kept: List<Int>) = wordEdit.commitForkReferences(row, edit, kept)

	/**
	 * `false` keeps popup open. See [SubjectsWordEdit.commitItemEdit].
	 */
	fun commitItemEdit(row: ItemRow, result: CreatorResult): Boolean = wordEdit.commitItemEdit(row, result)

	fun onDeleteAction() {
		val doomed = items().filter(ItemRow::selected)
		if (doomed.isEmpty()) return
		emitRequest(Request.ConfirmDelete(doomed))
	}

	fun performDelete(doomed: List<ItemRow>, title: String? = null) {
		val marks = doomed.map { it.item.id to it.parentId }
		val searchNow = searching
		viewModelScope.launch(io) {
			if (title == null) for ((id, parent) in marks) ItemRepository.remove(id, parent)
			else FileProgress(title).use { progress ->
				// Count unknown before the walk — the bar shows the running count.
				progress.rows(0)
				for ((id, parent) in marks) ItemRepository.remove(id, parent, progress)
			}
			postToUi {
				// Search re-reads — the cascade takes more than the selection.
				if (searchNow) refreshSearchRows() else removeItems(doomed)
				refreshInfo()
			}
		}
	}

	/**
	 * Flips selected words — a chapter stands for its words.
	 * Outside living word blocks via [flipConflicts].
	 */
	fun flipSelected() {
		val selected = items().filter(ItemRow::selected)
		if (selected.isEmpty()) return
		val ids = selected.map { it.item.id }
		// Open container, or every hit parent in search.
		val actingParents = if (searching) selected.mapNotNull { it.parentId }.distinct()
		else listOf(history.path.lastOrNull()?.id ?: ROOT_ID)
		viewModelScope.launch(io) {
			val conflicts = ItemRepository.flipConflicts(actingParents, ids)
			if (conflicts.isNotEmpty()) {
				postToUi {
					Startup.showNotice(
						UiNotice.FlipConflicts(
							conflicts.map { conflict ->
								conflict.word.name + " — " +
									conflict.parents.joinToString(", ") { it.name }
							},
						)
					)
				}
				return@launch
			}
			ItemRepository.flip(ids)
			postToUi {
				clearSelection()
				reloadContent()
				refreshInfo()
			}
		}
	}

	/**
	 * Selected rows become test sources. Selection stays armed for export.
	 */
	fun prepareTest() {
		val seeds = items().filter(ItemRow::selected).map(TestSetupState::seedOf)
		if (seeds.isEmpty()) return
		viewModelScope.launch(io) {
			val refused = TestSetupState.fillFromSelection(seeds)
			if (refused.isNotEmpty()) {
				postToUi { Startup.showNotice(UiNotice.OneSubjectOnly(TestSetupState.pickedSubject()?.name)) }
				return@launch
			}
			postToUi {
				TestSetupState.openedWithSelection = true
				emitRequest(Request.OpenTest)
			}
		}
	}

	// ==========================================================================
	//  creation (menu)
	// ==========================================================================

	private fun newSubject() = wordEdit.newSubject()

	private fun newChapter() = wordEdit.newChapter()

	private fun newNote() = wordEdit.newNote()

	private fun newWord() = wordEdit.newWord()

	/**
	 * `false` keeps popup open. See [SubjectsWordEdit.commitNewItem].
	 */
	fun commitNewItem(kind: RowKind, result: CreatorResult): Boolean = wordEdit.commitNewItem(kind, result)

	// ==========================================================================
	//  import / export
	// ==========================================================================

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
		override fun refreshInfo() = this@MainExplorerViewModel.refreshInfo()
	}

	private fun exportWordFile() = transfer.exportWordFile()

	private fun exportJsonFile() = transfer.exportJsonFile()

	internal fun exportContainers(): List<DbItem> = transfer.exportContainers()

	fun onAnyFilePicked(uri: Uri) = transfer.onAnyFilePicked(uri)

	fun onWordTargetPicked(uri: Uri) = transfer.onWordTargetPicked(uri)

	fun onJsonTargetPicked(uri: Uri) = transfer.onJsonTargetPicked(uri)

	fun onDirPicked(uri: Uri) = transfer.onDirPicked(uri)
}
