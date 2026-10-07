package cz.litoj.schlmgr.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.activity.MainActivity
import cz.litoj.schlmgr.ui.viewmodel.AppBarViewModel
import cz.litoj.schlmgr.ui.viewmodel.CurrentData.BackLog
import cz.litoj.schlmgr.ui.viewmodel.CurrentData.EasyList
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.component.ExplorerScaffold
import cz.litoj.schlmgr.ui.component.ItemListCallbacks
import cz.litoj.schlmgr.ui.component.PickerActionBar
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.launch

/**
 * The item-picker (test-source chooser) — the former `SelectItemsActivity`, now a
 * navigation destination inside MainActivity so that it IS the explorer: same
 * window, same app bar (title, search button, the three-dot hidden via
 * `menuRes = 0`) and same back dispatch through [ControlListener]. The explorer
 * chrome and item list come from [ExplorerScaffold]; this host keeps only the
 * picker's own behaviours: its row-intent semantics, its bottom strip
 * (Cancel / Select-all / Select) and the source-chain building.
 *
 * On confirm, every selected row becomes one source chain — root first, the picked
 * item last, matching the [cz.litoj.schlmgr.testing.TestEntry] path shape — and the
 * chains are prepended into [TestFragment.list], where a background thread dedups
 * them against the already-picked sources (a picked ancestor covers its subtree).
 */
class SelectItemsFragment : Fragment(), AppBarViewModel.DestinationActions, ExplorerViewModel.Host {
	private val appBar: AppBarViewModel by activityViewModels()

	private val viewModel: ExplorerViewModel by viewModels {
		ExplorerViewModel.Factory(ensureBackLog(), picker = true)
	}

	/** The picker's own [BackLog], created on first use (see the companion). */
	private fun ensureBackLog(): BackLog {
		if (backLog == null) backLog = BackLog().apply { clear() }
		return backLog!!
	}

	/** The picker's own shared-search flag (legacy `SelectItemsActivity.search`). */
	private val search get() = viewModel.search

	override fun onCreateView(
		inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
	): View {
		// A fresh view always (re)loads the open container's content, be it a first
		// open or a return to the preserved position.
		setContent(backLog!!.path.getOrNull(-1), backLog!!.path.getOrNull(-2), backLog!!.path.size)

		return ComposeView(requireContext()).apply {
			setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			setContent { PickerScreen() }
		}
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		viewModel.attachHost(this)
		// Back reaches this destination through the dispatcher while its view
		// exists — the newest callback outranks the shell's own.
		requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
			object : OnBackPressedCallback(true) {
				override fun handleOnBackPressed() { onBackRequested() }
			})
		// The toolbar search bar is a dumb view of the view model's field state:
		// this host attaches/detaches it, the field talks straight to the model.
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.searchFieldOpen.collect { open ->
					val shell = activity as? MainActivity ?: return@collect
					if (open) shell.showSearchBar(viewModel)
					else {
						shell.hideSearchBar()
						applyTitle()
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		viewModel.detachHost()
		// the field state survives in the model, but the toolbar view does not —
		// drop the shell view; the collector re-attaches it.
		(activity as? MainActivity)?.hideSearchBar()
		super.onDestroyView()
	}

	override fun onResume() {
		super.onResume()
		// Claim the shell like the other destinations: no menu (the three-dot stays
		// hidden — the picker's select-all lives in its bottom strip), the search
		// button following the path rule, and this screen handles back.
		appBar.setDestination(this, 0, backLog!!.path.isNotEmpty())
		// the title follows the path, not the destination: rotation recreates
		// the view while the backlog still sits deep inside a chapter
		applyTitle()
	}

	/** The picker screen: the explorer scaffold with the picker's bottom strip. */
	@Composable
	private fun PickerScreen() {
		// The Select button's enablement derives straight from the list state, so
		// it recomposes as rows get (un)selected (any selection is a valid source set).
		val listState by viewModel.listState.collectAsStateWithLifecycle()
		ExplorerScaffold(
			viewModel = viewModel,
			callbacks = pickerCallbacks(),
		) {
			PickerActionBar(
				selectEnabled = listState.items.count { it.selected } > 0,
				onCancel = { findNavController().popBackStack() },
				onSelectAll = { onSelectAll() },
				onSelect = { onSelect() },
			)
		}
	}

	// ------------------------------------------------------------------ bottom strip

	/** Legacy `((TextView) select_all).setOnClickListener` — toggle every row's selection. */
	private fun onSelectAll() {
		val all = viewModel.items().size > viewModel.selectionCount()
		viewModel.selectAll(all)
	}

	/**
	 * Legacy `((TextView) objects_select).setOnClickListener` — collect the selected
	 * rows into `TestFragment.list`, dedup on a background thread, and return.
	 */
	private fun onSelect() {
		val list = TestFragment.list
		// Browse rows and search-hit rows share the live models; a hit carries
		// its own chain, a browse row's chain is rebuilt from the parent it was
		// selected under.
		for (row in viewModel.items()) {
			if (row.selected) list.add(0, TestFragment.chainOf(row))
		}
		TestFragment.control = Thread({ TestFragment.dedup(list) }, "TFrag test item control")
		TestFragment.control?.start()
		findNavController().popBackStack()
	}

	// ------------------------------------------------------------------ destination actions

	/**
	 * Back reached this destination: pop one hierarchy level; at the subjects
	 * root a double press within 3s returns to the test screen.
	 */
	private fun onBackRequested() {
		// back cancels the whole search at once: the field closes and the
		// container's full list returns
		if (viewModel.searchFieldOpen.value) {
			viewModel.cancelSearch()
			return
		}
		if (backLog!!.path.isNotEmpty() && TestFragment.list.isEmpty()
			|| backLog!!.path.size > 1 || search
		) {
			changedPath(1)
		} else {
			if (System.currentTimeMillis() - backTime > 3000) {
				backTime = System.currentTimeMillis()
				Toast.makeText(requireContext(), R.string.press_exit, Toast.LENGTH_SHORT).show()
			} else findNavController().popBackStack()
		}
	}

	/** The bar's select-all action, should the host ever wire it (the strip has its own). */
	override fun onSelectAllToggle() {
		val sel = viewModel.selectionCount()
		val size = viewModel.items().size
		if (sel < size) viewModel.selectAll(true) else viewModel.clearSelection()
	}

	/**
	 * The bar's search button: open this explorer's search field. The field
	 * talks straight to the view model; the shell only hosts its view.
	 */
	override fun onSearchOpen() {
		viewModel.openSearchField()
	}

	// ------------------------------------------------------------------ ExplorerViewModel.Host

	/** Legacy `setContent` — load [bd]'s children (or the subjects at the root). */
	override fun setContent(bd: DbItem?, parent: DbItem?, pathLength: Int) {
		viewModel.loadContent(bd, parent, pathLength)
		// the search button follows the path, exactly like the main explorer's bar
		appBar.setSearchVisible(backLog!!.path.isNotEmpty())
		applyTitle()
	}

	/**
	 * Shows the open chapter in the toolbar title (this screen's label at the
	 * root). Skipped while the search bar covers the title — its collector
	 * re-applies on close.
	 */
	private fun applyTitle() {
		if (viewModel.searchFieldOpen.value) return
		(activity as? AppCompatActivity)?.supportActionBar?.title =
			backLog?.path?.getOrNull(-1)?.name ?: getString(R.string.test_add)
	}

	override fun onSelectionChanged() {
		// recomposition reads the selection counters from the list state — nothing to do
	}

	override fun onSearchStateChanged() {
		// the picker has no selection-bar to reset on a new search
	}

	/** Legacy `updateBackPath(skip)` = `es.updateBackPath` + the search reset. */
	override fun changedPath(skip: Int) {
		viewModel.updateBackPath(skip)
		// a back hop onto a stacked search run restores the filtered list
		if (viewModel.restoreSearch()) {
			applyTitle()
			return
		}
		// leaving the search list for a browse screen ends the search flow
		if (viewModel.search) viewModel.endSearch()
		setContent(backLog!!.path.getOrNull(-1), backLog!!.path.getOrNull(-2), backLog!!.path.size)
	}

	// ------------------------------------------------------------------ row intents

	private fun pickerCallbacks(): ItemListCallbacks = ItemListCallbacks(
		onItemClick = { ui -> onItemClick(ui.payload) },
		onItemLongClick = { ui -> onItemLongClick(ui.payload) },
		onCheck = { ui, checked ->
			// search-hit and browse rows share the live models, so one path selects
			viewModel.setSelected(ui.payload, checked)
		},
		onSelectRange = { from, to -> viewModel.selectRange(from, to) },
		onSelectEnd = { viewModel.endRangeSelect() },
	)

	/**
	 * Legacy `onItemClick` — flip a word, otherwise open the container under the parent
	 * it was displayed in (the legacy reference deref is gone: a shared item opens
	 * through the parent the row was found under).
	 */
	private fun onItemClick(row: ItemRow) {
		val item = row.item
		if (item.type == ItemKind.WORD) {
			viewModel.flipWord(row)
			return
		}
		if (item.type != ItemKind.CHAPTER) return
		if (!search) {
			viewModel.backLog.add(false, item, null)
		} else {
			// a search row opens through its hit's chain: root first, the hit's parent last
			val path = EasyList<DbItem>()
			path.addAll(row.path ?: emptyList())
			path.add(item)
			viewModel.backLog.add(true, null, path)
			// keep the search hop stacked so a back press restores the results
			viewModel.suspendSearch()
		}
		setContent(backLog!!.path.getOrNull(-1), backLog!!.path.getOrNull(-2), viewModel.backLog.path.size)
		viewModel.onChange(true)
	}

	/** Legacy `onItemLongClick` — in search jump to the hit's directory, else toggle selection. */
	private fun onItemLongClick(row: ItemRow): Boolean {
		if (search) {
			val hitPath = row.path ?: return true
			val path = EasyList<DbItem>()
			path.addAll(hitPath)
			path.add(row.item)
			viewModel.backLog.add(true, null, path)
			// keep the search hop stacked so a back press restores the results
			viewModel.suspendSearch()
			// the hit's direct parent is the container to show, its own parent the context
			setContent(hitPath.lastOrNull(), hitPath.getOrNull(hitPath.size - 2), 1)
			viewModel.onChange(true)
		} else {
			// browsing (HierarchyAdapter in the legacy code): toggle the row's checkbox
			viewModel.toggleSelect(row)
		}
		return true
	}

	companion object {
		private var backTime = 0L

		/**
		 * The picker's own [BackLog] (legacy `public static BackLog backLog`), kept across
		 * fragment instances so re-entering the picker restores the previous position;
		 * `TestFragment` resets it to `null` to start fresh.
		 */
		@JvmField
		var backLog: BackLog? = null
	}
}
