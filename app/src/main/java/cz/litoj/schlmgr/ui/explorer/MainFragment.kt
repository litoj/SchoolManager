package cz.litoj.schlmgr.ui.explorer

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.io.JsonChapter
import cz.litoj.schlmgr.db.io.JsonWordList
import cz.litoj.schlmgr.db.dao.WordDao
import cz.litoj.schlmgr.db.io.WordListReader
import cz.litoj.schlmgr.db.io.WordListWriter
import cz.litoj.schlmgr.db.ratio
import cz.litoj.schlmgr.db.io.LegacyJsonLoader
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.ui.AppBarViewModel
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.ui.activity.BaseActivity
import cz.litoj.schlmgr.ui.explorer.CurrentData.EasyList
import cz.litoj.schlmgr.ui.explorer.CurrentData.backLog
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.ui.fragments.TestFragment
import cz.litoj.schlmgr.ui.popup.ContinuePopup
import cz.litoj.schlmgr.ui.explorer.CreatorContent
import cz.litoj.schlmgr.ui.explorer.TranslationRow
import cz.litoj.schlmgr.ui.explorer.ExplorerController
import cz.litoj.schlmgr.ui.explorer.model.ItemUiModel
import cz.litoj.schlmgr.ui.explorer.components.ExplorerScaffold
import cz.litoj.schlmgr.ui.explorer.components.ItemListCallbacks
import cz.litoj.schlmgr.ui.explorer.components.PasteActionBar
import cz.litoj.schlmgr.ui.explorer.components.SelectionActionBar
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import java.util.Collections

/**
 * The main browsing screen — Kotlin, full-Compose port of the legacy `MainFragment.java`,
 * re-based on the Room database ([ItemRepository]): the explorer chrome and item list
 * come from [ExplorerScaffold] driven by [controller]; this fragment owns the browsing
 * behaviours: opening/flipping rows, the selection and paste action bars, the `more_*`
 * menus + sort, and the file import/export flows.
 *
 * It also owns the shell's "more"/"select" buttons for this destination via
 * [AppBarViewModel.setDestination] in [onResume] (matching TestFragment/AboutFragment);
 * `menuRes` is recomputed per content (root/container/subject/search). `VS`'s contract is
 * preserved: [ViewState.mfInstance] points at the live fragment (MainActivity resets the
 * screen through it) and [ViewState.pasteData] tracks the in-progress file-operation picker.
 */
class MainFragment : Fragment(), AppBarViewModel.DestinationActions, ExplorerController.Host {

	private lateinit var controller: ExplorerController
	private val appBar: AppBarViewModel by activityViewModels()

	/** The export destination picker (legacy `createWordFile` launcher). */
	private val createWordFile: ActivityResultLauncher<String> =
		registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
			if (uri != null) onWordTargetPicked(uri)
		}

	/** Directory picker for old-style JSON subject import (one of the two Import paths). */
	private val importDirLauncher: ActivityResultLauncher<Uri?> =
		registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
			if (uri != null) onDirPicked(uri)
		}

	/**
	 * The merged file import picker: one entry accepts word lists and tree JSON
	 * files alike, the file's extension decides the type at [onAnyFilePicked].
	 */
	private val importAnyFile: ActivityResultLauncher<Array<String>> =
		registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
			if (uri != null) onAnyFilePicked(uri)
		}

	/** The tree JSON (HJSON) export destination picker. */
	private val createJsonFile: ActivityResultLauncher<String> =
		registerForActivityResult(ActivityResultContracts.CreateDocument("application/hjson")) { uri ->
			if (uri != null) onJsonTargetPicked(uri)
		}

	/**
	 * This destination's back callback: the shell consults it (the newest one
	 * first) while this fragment's view exists, and the root exit hands it to
	 * [BaseActivity.goBack] to decline the event — see the recursion note there.
	 */
	private val backCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() { onBackRequested() }
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		oldState: Bundle?,
	): View {
		VS.mfInstance = this
		// The controller may post before the view exists (cold-start load); `view` is safe here.
		controller = ExplorerController(backLog, this, onUiThread = { r ->
			val v = view
			if (v != null) v.post(r) else activity?.runOnUiThread(r)
		})

		// The legacy-format subjects were migrated in MainActivity before this fragment
		// inflates, so the repository already answers with the full subject list here.
		setContent(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2), backLog.path.size)
		controller.refreshInfo()

		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent { MainExplorer() }
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		// Back reaches this destination through the dispatcher while its view
		// exists — the newest callback outranks the shell's own.
		requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
	}

	override fun onResume() {
		super.onResume()
		// Declare this destination to the shell now, so the "more" button is converged
		// here exactly like TestFragment/AboutFragment do (they pass menuRes 0). The
		// concrete menu resource is always recomputed by [applyMenuForContent], and the
		// search button follows the path (hidden at the subjects root, like the old bar).
		appBar.setDestination(this, VS.menuRes, backLog.path.isNotEmpty())
	}

	// ==========================================================================
	//  Compose content
	// ==========================================================================

	/** The whole explorer screen + its bottom strip, driven by [controller]. */
	@Composable
	private fun MainExplorer() {
		val listState by controller.listState.collectAsStateWithLifecycle()
		// The strip is a pure function of the list view-model: a content switch (a new
		// chapter, a back hop, a search) clears the row selection, so the strip hides
		// itself with no per-navigation bookkeeping.
		val selected = listState.items.filter { it.selected }
		val any = selected.isNotEmpty()
		val single = selected.size == 1
		val anyRef = selected.any { it.isReference }
		// The shell's search button doubles as select-all exactly while rows are selected;
		// the 'more options' menu then shrinks to the flip action alone (see MainActivity).
		LaunchedEffect(any) { appBar.setSelectAsSelectAll(any) }
		ExplorerScaffold(
			controller = controller,
			callbacks = mainCallbacks(any),
		) {
			when {
				pasteArmed -> PasteActionBar(
					pasteEnabled = !anyRef,
					onCancel = { cancelPaste() },
					onPaste = { doPaste() },
				)
				any -> SelectionActionBar(
					cutEnabled = any,
					editEnabled = single,
					deleteEnabled = any,
					onTest = ::prepareTest,
					onCut = { move(false) },
					onEdit = ::onEditAction,
					onDelete = ::onDeleteAction,
				)
				else -> {}
			}
		}
	}

	private fun mainCallbacks(selectionActive: Boolean): ItemListCallbacks = ItemListCallbacks(
		// Browse rows carry the live [ItemRow] in the payload; search-hit rows are told
		// apart by their trackPath and always take the jump-to-directory intent. In
		// selection mode a tap toggles the row's selection — it never opens it.
		onItemClick = { ui ->
			if (selectionActive) controller.setSelected(ui.payload, !ui.selected)
			else if (ui.trackPath != null) onSearchHitClick(ui)
			else onItemClick(ui.payload)
		},
		onItemLongClick = { ui ->
			// in selection mode a long-press toggles (like a tap); otherwise a search
			// hit jumps to its directory and a browse row starts the selection
			if (ui.trackPath != null && !selectionActive) onSearchHitClick(ui)
			else onItemLongClick(ui.payload)
		},
		onCheck = { ui, checked ->
			controller.setSelected(ui.payload, checked)
		},
		onInfo = { ui -> cz.litoj.schlmgr.ui.popup.MessagePopup(ui.desc) },
		onSelectRange = { from, to -> controller.selectRange(from, to) },
		onSelectEnd = { controller.endRangeSelect() },
		onReorderMove = { from, to -> controller.moveRow(from, to) },
		onReorderDrop = { persistOrder() },
	)

	/** Saves the current row order of the open container (a completed reorder drag). */
	private fun persistOrder() {
		val parentId = backLog.path.getOrNull(-1)?.id ?: ItemRepository.ROOT_ID
		ItemRepository.setOrder(parentId, controller.items().map { it.item.id })
		// the long-press that began the drag also selected the row; the drop ends that
		controller.clearSelection()
		controller.refreshInfo()
	}

	// ==========================================================================
	//  content loading + menu wiring
	// ==========================================================================

	/** Legacy `Content.setContent` — loads the opened container's children into the list. */
	override fun setContent(bd: DbItem?, parent: DbItem?, pathLength: Int) {
		controller.loadContent(bd, parent, pathLength)
		applyMenuForContent(bd)
	}

	/**
	 * Recomputes the destination's menu resource from the current content (the legacy
	 * `MainFragment.setContent` branches):
	 * - root (empty path) → [R.menu.more_main]
	 * - search results → [R.menu.more_search]
	 * - a subject root (the only chapter on the path) → [R.menu.more_mch]
	 * - any other container → [R.menu.more_container]
	 */
	private fun applyMenuForContent(bd: DbItem?) {
		VS.menuRes = when {
			controller.search -> R.menu.more_search
			backLog.path.isEmpty() -> R.menu.more_main
			bd != null && backLog.path.size == 1 -> R.menu.more_mch
			else -> R.menu.more_container
		}
		appBar.setMenu(VS.menuRes)
		// the search button mirrors the old search bar's rule: nothing to search at
		// the subjects root. The select-all button form is driven by [MainExplorer].
		appBar.setSearchVisible(backLog.path.isNotEmpty())
	}

	// Both the bottom strip and the shell's select-all button form are derived in
	// [MainExplorer] from the list view-model, so a selection change needs no work here.
	override fun onSelectionChanged() {}

	override fun onSearchStateChanged() = controller.clearSelection()

	override fun changedPath(skip: Int) {
		controller.updateBackPath(skip)
		// a back hop onto a stacked search run restores the filtered list instead
		// of showing the container's full contents
		if (controller.restoreSearch()) {
			applyMenuForContent(backLog.path.getOrNull(-1))
			return
		}
		// leaving the search list for a browse screen ends the search flow (the
		// hops updateBackPath already consumed were kind-aware, so endSearch's own
		// unwind only fires for hops still on top)
		if (controller.search) controller.endSearch()
		val bd = backLog.path.getOrNull(-1)
		setContent(bd, backLog.path.getOrNull(-2), backLog.path.size)
	}

	// ==========================================================================
	//  selection / paste state
	// ==========================================================================

	/**
	 * The one host-owned part of the bottom strip: whether a move/reference paste is
	 * armed. The selection strip itself is derived from the list view-model in
	 * [MainExplorer], so this only has to be observable for the strip to follow it.
	 */
	private var pasteArmed by mutableStateOf(false)

	// ==========================================================================
	//  top-bar button / menu handling (the destination's `DestinationActions` contract)
	// ==========================================================================

	/**
	 * Back reached this destination: leave selection mode first,
	 * then the search list, then pop one hierarchy level; at the subjects root a double
	 * press within 3s exits the app (the picker's pattern).
	 */
	private fun onBackRequested() {
		if (controller.selectionCount() > 0) {
			controller.clearSelection()
			return
		}
		if (controller.search) {
			controller.endSearch()
			return
		}
		if (backLog.path.isNotEmpty()) {
			changedPath(1)
			return
		}
		if (System.currentTimeMillis() - backTime > 3000) {
			backTime = System.currentTimeMillis()
			Toast.makeText(requireContext(), R.string.press_exit, Toast.LENGTH_SHORT).show()
		} else
			// decline the event: goBack re-dispatches, so our callback must sit
			// it out (see the recursion note on BaseActivity.goBack)
			(activity as BaseActivity).goBack(backCallback)
	}

	/**
	 * The bar's select-all button (it replaces the three-dot menu while rows are
	 * selected): toggles whether the current list is in selection mode (marks all /
	 * drops selection), mirroring the old cycle over `VS.contentAdapter.selected`.
	 */
	override fun onSelectAllToggle() {
		// not all rows selected yet → select all; otherwise drop the selection
		if (controller.selectionCount() < controller.items().size) controller.selectAll(true)
		else controller.clearSelection()
	}

	/**
	 * The app-bar search field was submitted (the shell forwards the typed query):
	 * run the search over the currently opened container's content.
	 */
	override fun onSearchSubmit(query: String) {
		controller.submitSearch(query)
	}

	/**
	 * The `more_*` menu click handler (legacy `MainFragment.onMenuItemClick`). Only the
	 * subset valid for this destination's current [VS.menuRes] is ever shown by the shell;
	 * the rest returns `false` so the shell can offer it to other handlers.
	 */
	override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
		R.id.sort_alpha_AZ -> { sort(compareBy { it.toShow.lowercase() }); true }
		R.id.sort_alpha_ZA -> { sort(compareBy<ItemRow> { it.toShow.lowercase() }.reversed()); true }
		R.id.sort_sf_01 -> { sort(compareBy { it.item.ratio }); true }
		R.id.sort_sf_10 -> { sort(compareBy<ItemRow> { it.item.ratio }.reversed()); true }
		R.id.sort_length_01 -> { sort(compareBy { it.toShow.length }); true }
		R.id.sort_length_10 -> { sort(compareBy<ItemRow> { it.toShow.length }.reversed()); true }
		R.id.sort_default -> { reloadDefaultOrder(); true }
		R.id.more_new_mch -> { newSubject(); true }
		R.id.more_new_container -> { newChapter(); true }
		R.id.more_new_word -> { newWord(); true }
		R.id.more_new_note -> { newNote(); true }
		R.id.more_import_mch -> { importDirLauncher.launch(null); true }
		R.id.more_import_file -> { importFile(); true }
		R.id.more_export_word -> { exportWordFile(); true }
		R.id.more_export_json -> { exportJsonFile(); true }
		R.id.more_flip -> { flipSelected(); true }
		R.id.more_reference -> { move(true); true }
		else -> false
	}

	/** Legacy `sort(int, boolean)` — reorders the live list with [comparator]. */
	private fun sort(comparator: Comparator<ItemRow>) {
		val items = controller.items().toMutableList()
		items.sortWith(comparator)
		val parent = backLog.path.getOrNull(-1)
		val parentParent = backLog.path.getOrNull(-2)
		controller.load(items, isSearch = controller.search, isPicker = false)
		controller.setInfo(parent, parentParent)
	}

	/** Legacy `sort_default`: reloads the container so children appear in file order. */
	private fun reloadDefaultOrder() {
		val bd = backLog.path.getOrNull(-1)
		setContent(bd, backLog.path.getOrNull(-2), backLog.path.size)
	}

	// ==========================================================================
	//  row intents
	// ==========================================================================

	/**
	 * Legacy `onItemClick`: a word row flips, a chapter row opens. A reference row (an
	 * item held by more containers) opens its target directly — in the link model an
	 * item simply appears under each of its parents, so the old jump-to-ref-path
	 * resolution is the open itself. A note row copies its text to the clipboard.
	 */
	private fun onItemClick(item: ItemRow) {
		when (item.item.type) {
			ItemKind.WORD -> controller.flipWord(item)
			ItemKind.CHAPTER -> {
				controller.clearSelection()
				backLog.add(false, item.item, backLog.path)
				setContent(item.item, backLog.path.getOrNull(-2), backLog.path.size)
			}
			ItemKind.NOTE -> copyNote(item)
			else -> {}
		}
	}

	/** Copies the note's description (the name when it is empty) to the system clipboard. */
	private fun copyNote(item: ItemRow) {
		val text = item.item.description.ifEmpty { item.item.name }
		val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		clipboard.setPrimaryClip(ClipData.newPlainText(item.item.name, text))
		Toast.makeText(requireContext(), R.string.note_copied, Toast.LENGTH_SHORT).show()
	}

	private fun onItemLongClick(item: ItemRow): Boolean {
		if (VS.pasteData == null) {
			// the VM recomputes the selection mode, so the row's checkbox appears
			controller.toggleSelect(item)
		} else return false
		return true
	}

	/** Search-hit row tap/long-press (legacy jump-to-directory): open the hit's container. */
	private fun onSearchHitClick(ui: ItemUiModel) {
		val row = ui.payload
		if (row.item.type == ItemKind.WORD) return
		if (row.item.type != ItemKind.CHAPTER) {
			// nothing to open: leave the search and show the origin's browse list
			controller.endSearch()
			return
		}
		val path = EasyList<DbItem>()
		ui.trackPath?.let(path::addAll)
		path.add(row.item)
		backLog.add(true, null, path)
		// The search hop stays stacked under the new path entry, so a back press
		// restores the filtered list; this open only switches to browse mode.
		controller.suspendSearch()
		setContent(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2), backLog.path.size)
	}

	// ==========================================================================
	//  file / edit actions (cut/paste/reference, rename, delete)
	// ==========================================================================

	/**
	 * Legacy `move(boolean)`: arms paste mode from the current selection. The selected
	 * [ItemRow]s and the source path are captured into [VS.pasteData]; the actual
	 * insert-or-reference happens in [doPaste] (legacy inline `paste.setOnClickListener`).
	 */
	private fun move(reference: Boolean) {
		if (controller.selectionCount() == 0) return
		val search = controller.search
		VS.pasteData = PasteData(
			referencing = reference,
			src = controller.items().filter(ItemRow::selected),
			srcPath = ArrayList(backLog.path),
			searchSrc = search,
		)
		pasteArmed = true
		appBar.setSearchVisible(false)
		controller.clearSelection()
	}

	/**
	 * Legacy `paste.setOnClickListener` — performs the armed move/reference into the
	 * current container. The database is the persistence now, so the list is re-read
	 * from it instead of the legacy in-place adapter surgery.
	 */
	private fun doPaste() {
		val paste = VS.pasteData as? PasteData ?: return
		val np = backLog.path.getOrNull(-1)
		val searchNow = controller.search
		// Only a container can receive the pasted items; at the subjects root there is
		// nothing to paste into (the legacy code crashed there too).
		if (np != null) {
			// The legacy pre-filter: items already in the target are skipped, so no
			// second link to the same parent is ever created.
			val present = ItemRepository.children(np.id).mapTo(HashSet()) { it.id }
			// Cycle guard, the legacy paste-enablement check enforced at execution time:
			// nothing is placed into itself or into its own subgraph.
			val inside = ItemRepository.subgraph(np.id).mapTo(HashSet()) { it.id }
			var refRefused = false
			for (him in paste.src) {
				if (him.item.id in present || him.item.id in inside) continue
				if (paste.referencing) {
					// a chapter never becomes a reference — the report follows the loop
					if (!ItemRepository.reference(him.item.id, np.id)) refRefused = true
				} else {
					// Search-selected rows keep their own container ([ItemRow.parentId]
					// is the hit's parent); browse rows use the capture-time source
					// path — both resolve to the row's parent id, the legacy
					// `search ? sim.path : srcPath` distinction.
					val oldParent = him.parentId
					if (oldParent == null) {
						if (!ItemRepository.reference(him.item.id, np.id)) refRefused = true
					} else ItemRepository.move(him.item.id, oldParent, np.id)
				}
			}
			if (refRefused) {
				val msg = getString(R.string.fail_reference_chapter)
				Startup.showMsg(msg, msg)
			}
		}
		VS.pasteData = null
		pasteArmed = false
		appBar.setSearchVisible(backLog.path.isNotEmpty())
		if (!searchNow) reloadContent()
		controller.refreshInfo()
	}

	/** Re-reads the open container from the database (the legacy adapter refill after mutations). */
	private fun reloadContent() {
		setContent(backLog.path.getOrNull(-1), backLog.path.getOrNull(-2), backLog.path.size)
	}

	private fun cancelPaste() {
		VS.pasteData = null
		pasteArmed = false
		appBar.setSearchVisible(backLog.path.isNotEmpty())
	}

	/**
	 * Legacy edit (`selActs.EDIT`): opens the [CreatorPopup] for the single selected item
	 * and commits the change in its OK listener — containers and notes update name +
	 * desc + position, words go through [applyWord], and the row is refreshed to match.
	 * (Notes were not editable in the engine; the database model makes them a plain
	 * name/description row, so they edit like chapters.)
	 */
	private fun onEditAction() {
		if (controller.selectionCount() != 1) return
		val him = controller.items().firstOrNull(ItemRow::selected) ?: return
		val item = him.item
		if (item.type == ItemKind.WORD) {
			val rows = ArrayList<TranslationRow>()
			for (trl in ItemRepository.translations(item.id)) {
				rows.add(TranslationRow(trl.name, trl.description, trl))
			}
			requireActivity().runOnUiThread {
				val cp = CreatorPopup(getString(R.string.edit), him, him.position, CreatorContent.Word(rows))
				cp.setOkListener {
					val ok = applyWord(cp, him)
					if (!ok) return@setOkListener
					// The database is the source of truth: re-read the container so the
					// edited row, every row sharing its translations and the shifted
					// position numbers all match it — the legacy in-place model surgery
					// could not refresh the other rows.
					reloadContent()
					controller.clearSelection()
					controller.refreshInfo()
					cp.dismiss()
				}
			}
		} else {
			requireActivity().runOnUiThread {
				val cp = CreatorPopup(getString(R.string.edit), him, him.position, CreatorContent.SIMPLE)
				cp.setOkListener {
					val name = cp.name
					if (name.isEmpty()) return@setOkListener
					cp.dismiss()
					ItemRepository.setDescription(item.id, cp.desc.replace("\\t", "\t"))
					if (name != item.name) ItemRepository.rename(item.id, name)
					// Re-read the container (the shared-translation/position refresh
					// of the word editor, for the same reason).
					reloadContent()
					controller.clearSelection()
					controller.refreshInfo()
				}
			}
		}
	}

	/**
	 * Delete (legacy `selActs.DELETE`): confirm first (the `continue_delete` dialog),
	 * then remove the selected rows. [ContinuePopup] runs its action on a worker
	 * thread, so the removal is posted back to the UI thread.
	 */
	private fun onDeleteAction() {
		if (controller.selectionCount() == 0) return
		val activity = requireActivity()
		ContinuePopup(getString(R.string.continue_delete)) {
			activity.runOnUiThread(::performDelete)
		}
	}

	private fun performDelete() {
		val doomed = controller.items().filter(ItemRow::selected)
		for (him in doomed) ItemRepository.remove(him.item.id, him.parentId)
		controller.removeItems(doomed)
		controller.refreshInfo()
	}

	/**
	 * Flips the selected words between the word and translation roles; a selected
	 * chapter stands for every word below it (see [ItemRepository.flip]). The flip
	 * blocks, so it runs on a worker thread and the container is re-read on the UI
	 * thread afterwards — the database is the source of truth.
	 */
	private fun flipSelected() {
		val ids = controller.items().filter(ItemRow::selected).map { it.item.id }
		if (ids.isEmpty()) return
		val activity = requireActivity()
		Thread({
			ItemRepository.flip(ids)
			activity.runOnUiThread {
				controller.clearSelection()
				reloadContent()
				controller.refreshInfo()
			}
		}, "MFrag flip").start()
	}

	/**
	 * 'Prepare test': the selected rows become the test's sources and the test
	 * setup screen opens with exactly them (a picked chapter covers the words in
	 * it — the dedup pass of [cz.litoj.schlmgr.ui.fragments.TestFragment] removes
	 * the covered chains). The chains are built on the worker thread because they
	 * read the database; the selection stays armed for a possible export after
	 * returning.
	 */
	private fun prepareTest() {
		val rows = controller.items().filter(ItemRow::selected)
		if (rows.isEmpty()) return
		val activity = requireActivity()
		Thread({
			val chains = ArrayList<List<DbItem>>(rows.size)
			for (row in rows) chains.add(TestFragment.chainOf(row))
			TestFragment.list.clear()
			TestFragment.list.addAll(chains)
			TestFragment.dedup(TestFragment.list)
			activity.runOnUiThread {
				TestFragment.openedWithSelection = true
				findNavController().navigate(R.id.test)
			}
		}, "MFrag test prepare").start()
	}

	/**
	 * Applies the word editor results (name, description, translations) through the
	 * repository — the legacy `applyWord(cp, him)`. A new word is [ItemRepository.createWord]
	 * per `\`-split name (its subject-scope merge absorbs same-named words); an edited
	 * word is renamed/described in place and the missing translations are added by the
	 * same [ItemRepository.createWord] call — its name-merge links only the ones the
	 * word does not have yet, which is the engine's translate-merge behaviour.
	 *
	 * @return `false` when the input is incomplete and nothing should be committed
	 */
	private fun applyWord(cp: CreatorPopup, him: ItemRow?): Boolean {
		val name = cp.name
		val rows = cp.translations
		if (name.isEmpty() || rows.isEmpty()) return false
		val parentId = him?.parentId ?: backLog.path.getOrNull(-1)?.id ?: return false
		// Every row is read and validated first: a mid-loop return false after
		// the earlier rows were already renamed would commit half an edit.
		// The `\` divider splits an editor field into several entries; an
		// existing translation edited under a single name is updated in place
		// instead — the writes follow below, once nothing can fail anymore.
		val newTrls = ArrayList<Pair<String, String?>>()
		val renamed = ArrayList<Triple<Int, String, String>>()
		for (row in rows) {
			val trls = WordDao.splitEscaped(row.name)
			if (trls[0].isEmpty()) return false
			val trlDescs = WordDao.splitEscaped(row.desc)
			if (him == null || row.source == null || trls.size != 1) {
				for (i in trls.indices)
					newTrls.add(
						trls[i] to (if (i < trlDescs.size) trlDescs[i].replace("\\t", "\t") else null))
			} else renamed.add(Triple(row.source.id, trls[0], trlDescs[0]))
		}
		for ((id, trlName, trlDesc) in renamed) {
			ItemRepository.setDescription(id, trlDesc)
			ItemRepository.rename(id, trlName)
		}
		val desc = cp.desc.replace("\\t", "\t")
		if (him == null) {
			val names = WordDao.splitEscaped(name)
			val descs = WordDao.splitEscaped(cp.desc)
			val pos = cp.position
			for (i in names.indices) {
				val w = ItemRepository.createWord(
					parentId, names[i],
					if (i < descs.size) descs[i].replace("\\t", "\t") else null,
					newTrls,
				)
				// Creation offers no position picker, but the requested slot is honoured
				// anyway — the legacy `putChild(..., pos + i - 1)` insert.
				ItemRepository.reorder(parentId, w.id, pos + i - 1)
				controller.addItem(pos + i - 1, ItemRow(w, parentId, pos + i))
			}
		} else {
			// Removals go first: a rename-merge would otherwise carry the removed
			// translations over to the surviving word.
			for (trl in cp.removedTranslations) ItemRepository.remove(trl.id, him.item.id)
			val word = ItemRepository.rename(him.item.id, name)
			ItemRepository.setDescription(word.id, desc)
			ItemRepository.createWord(parentId, name, desc, newTrls)
			// A merge into another word changed the row's identity.
			if (word.id != him.item.id) him.setNew(word, him.parentId)
			else him.update()
		}
		return true
	}

	// ==========================================================================
	//  creation / import-export / dir actions (menu)
	// ==========================================================================

	/** Legacy `more_new_mch`: the subject-creation popup ([CreatorContent.SIMPLE]). */
	private fun newSubject() {
		val cp = CreatorPopup(getString(R.string.new_mch), null, 0, CreatorContent.SIMPLE)
		cp.setOkListener {
			val name = cp.name
			if (name.isEmpty()) return@setOkListener
			val mch = ItemRepository.createSubject(name, cp.desc.replace("\\t", "\t"))
			controller.addItem(ItemRow(mch, ItemRepository.ROOT_ID, controller.items().size + 1))
			cp.dismiss()
		}
	}

	/** Legacy `more_new_container`: the chapter creation popup. */
	private fun newChapter() {
		val pos = controller.items().size + 1
		val cp = CreatorPopup(getString(R.string.new_chapter), null, pos, CreatorContent.SIMPLE)
		cp.setOkListener {
			val name = cp.name
			if (name.isEmpty()) return@setOkListener
			val par = backLog.path.getOrNull(-1) ?: return@setOkListener
			val ch = ItemRepository.createChapter(par.id, name, cp.desc.replace("\\t", "\t"), cp.position - 1)
			controller.addItem(cp.position - 1, ItemRow(ch, par.id, cp.position))
			cp.dismiss()
		}
	}

	/** `more_new_note`: notes are name + description rows placed like chapters. */
	private fun newNote() {
		val pos = controller.items().size + 1
		val cp = CreatorPopup(getString(R.string.new_note), null, pos, CreatorContent.SIMPLE)
		cp.setOkListener {
			val name = cp.name
			if (name.isEmpty()) return@setOkListener
			val par = backLog.path.getOrNull(-1) ?: return@setOkListener
			val note = ItemRepository.createNote(par.id, name, cp.desc.replace("\\t", "\t"), cp.position - 1)
			controller.addItem(cp.position - 1, ItemRow(note, par.id, cp.position))
			cp.dismiss()
		}
	}

	/** Legacy `more_new_word`: the word creator with translations. */
	private fun newWord() {
		val pos = controller.items().size + 1
		val cp = CreatorPopup(getString(R.string.new_word), null, pos,
			CreatorContent.Word(Collections.emptyList()))
		cp.setOkListener {
			if (!applyWord(cp, null)) return@setOkListener
			controller.notifyChanged()
			cp.dismiss()
		}
	}

	/** `more_import_file` — one picker for every importable file; see [onAnyFilePicked]. */
	private fun importFile() {
		importAnyFile.launch(
			arrayOf(
				"text/plain", getString(R.string.json_import_types),
				"application/json", "application/octet-stream",
			)
		)
	}

	/**
	 * One imported file, whatever it is: a `.json`/`.hjson` name is a subject
	 * tree; anything else is parsed as a word list — at the subjects root it
	 * becomes a new subject, inside a container it lands in the open one.
	 */
	private fun onAnyFilePicked(uri: Uri) {
		Thread({
			val name = (DocumentFile.fromSingleUri(GlobalDependencies.appContext, uri)?.name ?: "").lowercase()
			if (name.endsWith(".json") || name.endsWith(".hjson")) onJsonFilePicked(uri)
			else if (backLog.path.isEmpty()) onWordMchFilePicked(uri)
			else onWordFilePicked(uri)
		}, "MFrag file import").start()
	}

	/** Legacy `more_export_word` — pick the export target for the selected containers. */
	private fun exportWordFile() { createWordFile.launch(exportName() + ".txt") }

	/** The tree JSON counterpart of `more_export_word` — pick the export target for the selected tree. */
	private fun exportJsonFile() {
		createJsonFile.launch(exportName() + getString(R.string.json_export_ext))
	}

	/** Every selected row of a container kind — the only rows the export actions write. */
	private fun selectedContainers(): List<DbItem> =
		controller.items().filter { it.selected && it.item.type == ItemKind.CHAPTER }.map { it.item }

	/** The suggested export file name: the single selected container's name, else the generic one. */
	private fun exportName(): String {
		val containers = selectedContainers()
		return if (containers.size == 1) containers[0].name else "subjects"
	}

	/**
	 * A word list (.txt) file was picked for import (legacy `onWordFilePicked`).
	 * Runs on a background thread: the file is parsed ([WordListReader]) and inserted
	 * under the current container ([ItemRepository.importWordList]), then the list is
	 * re-read from the database.
	 */
	private fun onWordFilePicked(uri: Uri) {
		Thread({
			try {
				val self = backLog.path.getOrNull(-1) ?: return@Thread
				val content = LegacyJsonLoader.readText(uri) ?: return@Thread
				val (tree, counts) = WordListReader.parse(content, self.name)
				ItemRepository.importWordList(self.id, tree)
				requireActivity().runOnUiThread {
					reloadContent()
					controller.refreshInfo()
				}
				Startup.onImportSuccess(counts)
			} catch (e: Exception) {
				if (e is IllegalArgumentException) {
					Startup.onParseFail(e.message ?: "")
					return@Thread
				}
				Startup.onLoadFail(e, uri.toString())
			}
		}, "MFrag word import").start()
	}

	/**
	 * A word file (.txt) containing a whole subject was picked on the main screen
	 * (legacy `onWordMchFilePicked`). The subject's name comes from the file's name
	 * (without the suffix); the file is parsed before anything is written, so a bad
	 * file leaves no half-created subject behind (the legacy `mch.destroy` cleanup).
	 */
	private fun onWordMchFilePicked(uri: Uri) {
		Thread({
			var name = DocumentFile.fromSingleUri(GlobalDependencies.appContext, uri)?.name
			if (name.isNullOrEmpty()) name = "subject"
			else {
				val dot = name.lastIndexOf('.')
				if (dot > 0) name = name.substring(0, dot)
			}
			val exists = ItemRepository.subjects().any { it.name.equals(name, ignoreCase = true) }
			if (exists) {
				val msg = getString(R.string.fail_import_mch_word_exists) + name
				requireActivity().runOnUiThread {
					Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
				}
				return@Thread
			}
			try {
				val content = LegacyJsonLoader.readText(uri) ?: return@Thread
				val (tree, counts) = WordListReader.parse(content, name)
				val mch = ItemRepository.createSubject(name, null)
				ItemRepository.importWordList(mch.id, tree)
				requireActivity().runOnUiThread {
					controller.addItem(ItemRow(mch, ItemRepository.ROOT_ID, controller.items().size + 1))
				}
				Startup.onImportSuccess(counts)
			} catch (iae: IllegalArgumentException) {
				Startup.onParseFail(iae.message ?: "")
			} catch (e: Exception) {
				Startup.onLoadFail(e, uri.toString())
			}
		}, "MFrag mch word import").start()
	}

	/**
	 * A target location was picked for exporting the word list (legacy
	 * `onWordTargetPicked`): every selected container is written, each wrapped
	 * as a chapter ([WordListWriter]) — the export actions live in the selection
	 * mode, so there is always one.
	 */
	private fun onWordTargetPicked(uri: Uri) {
		Thread({
			val toExport = selectedContainers()
			if (toExport.isEmpty()) return@Thread
			controller.clearSelection()
			val text = WordListWriter.write(toExport)
			// the success report follows only a written file — a failure
			// already went out through onSaveFail
			if (LegacyJsonLoader.writeText(uri, text)) {
				val name = DocumentFile.fromSingleUri(GlobalDependencies.appContext, uri)?.name
				Startup.onExportSuccess(name ?: uri.toString())
			}
		}, "MFrag word export").start()
	}

	/**
	 * A tree JSON (HJSON) file was picked for import: the whole file is parsed as a
	 * list of subjects ([JsonWordList.parse]) and persisted in one transaction
	 * ([ItemRepository.importJson]). The tree carries its own names and counters, so
	 * it needs no container target.
	 *
	 * The import is subject-level, so the subjects screen is re-read at the root
	 * (the [onDirPicked] pattern); the open content is re-read otherwise, because
	 * a merge can have added to the subject the user is inside.
	 */
	private fun onJsonFilePicked(uri: Uri) {
		Thread({
			try {
				val content = LegacyJsonLoader.readText(uri) ?: return@Thread
				val subjects = JsonWordList.parse(content)
				ItemRepository.importJson(subjects)
				requireActivity().runOnUiThread {
					if (backLog.path.isEmpty()) VS.mfInstance?.setContent(null, null, 0)
					else reloadContent()
				}
				Startup.onImportSuccess(jsonCounts(subjects))
			} catch (iae: IllegalArgumentException) {
				Startup.onParseFail(iae.message ?: "")
			} catch (e: Exception) {
				Startup.onLoadFail(e, uri.toString())
			}
		}, "MFrag json import").start()
	}

	/**
	 * A target location was picked for the tree JSON export: every selected
	 * container is rendered to HJSON ([JsonWordList.render]) — the same
	 * selection rule as the word-list export, minus the chapter wrapping (the
	 * JSON shape carries the names).
	 */
	private fun onJsonTargetPicked(uri: Uri) {
		Thread({
			val containers = selectedContainers()
			if (containers.isEmpty()) return@Thread
			controller.clearSelection()
			// the success report follows only a written file — a failure
			// already went out through onSaveFail
			if (LegacyJsonLoader.writeText(uri, JsonWordList.render(containers))) {
				val name = DocumentFile.fromSingleUri(GlobalDependencies.appContext, uri)?.name
				Startup.onExportSuccess(name ?: uri.toString())
			}
		}, "MFrag json export").start()
	}

	/** The chapter/word/translation counters of a parsed tree, for the import toast. */
	private fun jsonCounts(subjects: List<JsonChapter>): IntArray {
		val counts = IntArray(3)
		for (subject in subjects) countChapter(subject, counts)
		return counts
	}

	private fun countChapter(chapter: JsonChapter, counts: IntArray) {
		for (word in chapter.words) {
			counts[1]++
			counts[2] += word.translations.size
		}
		for (child in chapter.chapters) {
			counts[0]++
			countChapter(child, counts)
		}
	}

	/**
	 * Import a subject directory (old-style JSON format) — used by the unified import picker.
	 * One-time conversion: the old subject is loaded once and inserted as a normal database
	 * subject (no link back to the picked folder, nothing to remove later); the subjects
	 * screen reloads afterwards. A subject of the same name already in the database is not
	 * inserted twice.
	 */
	private fun onDirPicked(uri: Uri) {
		Thread({
			val dir = DocumentFile.fromTreeUri(GlobalDependencies.appContext, uri)
			// findFile, never a creating lookup: a missing file must not be born here
			if (dir != null && dir.findFile("main.json") != null && dir.findFile("setts.dat") != null) {
				try {
					if (ItemRepository.subjects().none { it.name == dir.name })
						LegacyJsonLoader.insertSubjectDir(dir)
				} catch (e: Exception) {
					Startup.onLoadFail(e, dir.name ?: uri.toString())
				}
			}
			if (backLog.path.isEmpty()) {
				requireActivity().runOnUiThread { VS.mfInstance?.setContent(null, null, 0) }
			}
		}, "MFrag dir import").start()
	}

	/** The single legacy [`PasteData`] — the armed move/reference operation. */
	private class PasteData(
		val referencing: Boolean,
		val src: List<ItemRow>,
		val srcPath: List<DbItem>,
		val searchSrc: Boolean,
	)

	// ==========================================================================
	//  ViewState + companion (legacy contract)
	// ==========================================================================

	/** Legacy per-fragment state (kept; `mfInstance`/`pasteData` contract preserved). */
	class ViewState {
		var menuRes = 0
		@JvmField
		var mfInstance: MainFragment? = null
		var pasteData: Any? = null
	}

	companion object {
		@JvmField
		var VS: ViewState = ViewState()

		@SuppressLint("StaticFieldLeak")
		private var backTime = 0L
	}
}
