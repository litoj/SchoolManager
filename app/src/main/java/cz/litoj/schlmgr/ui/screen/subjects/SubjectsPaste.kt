package cz.litoj.schlmgr.ui.screen.subjects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.Placement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Cut/reference paste — armed state, placement, follow-up.
 */
internal class SubjectsPaste(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {
		fun items(): List<ItemRow>

		fun selectionCount(): Int

		fun clearSelection()

		/** Open chain — paste lands in last. */
		val path: List<DbItem>

		val searching: Boolean

		/** Runs [work] on main. */
		fun postToUi(work: Runnable)

		/** Re-reads open container — browse only. */
		fun reloadContent()

		/** Re-maps shown rows — search only. */
		fun notifyChanged()

		fun refreshInfo()

		/** Re-applies reorder freeze. */
		fun refreshReorderable()
	}

	private class PasteData(
		val referencing: Boolean,
		val src: List<ItemRow>,
	)

	private class PasteSrc(val row: ItemRow, val id: Int, val oldParent: Int?)

	private class PasteOutcome(val notice: UiNotice?, val placements: List<Pair<ItemRow, Placement>>)

	private var pasteData by mutableStateOf<PasteData?>(null)

	val armed: Boolean get() = pasteData != null

	private fun armPaste(data: PasteData?) {
		pasteData = data
		host.refreshReorderable()
	}

	/**
	 * Arms paste from selection — true means references. See [doPaste].
	 */
	fun move(reference: Boolean) {
		if (host.selectionCount() == 0) return
		armPaste(PasteData(
			referencing = reference,
			src = host.items().filter(ItemRow::selected),
		))
		host.clearSelection()
	}

	/**
	 * Pastes armed rows into current container. Browse re-reads — search re-maps.
	 */
	fun doPaste() {
		val paste = pasteData ?: return
		armPaste(null)
		val np = host.path.lastOrNull()
		if (np == null) {
			Startup.showNotice(UiNotice.PasteNoTarget)
			return
		}
		val searchNow = host.searching
		// snapshot on main
		val src = paste.src.map { PasteSrc(it, it.item.id, it.parentId) }
		scope.launch(io) {
			val outcome = performPaste(paste.referencing, src, np, searchNow)
			host.postToUi {
				outcome.notice?.let(Startup::showNotice)
				outcome.placements.forEach { (row, placement) -> row.applyPlacement(placement) }
				if (searchNow) host.notifyChanged() else host.reloadContent()
				host.refreshInfo()
			}
		}
	}

	/** Places armed rows into [np] — returns the notice. */
	private suspend fun performPaste(
		referencing: Boolean,
		src: List<PasteSrc>,
		np: DbItem,
		searchNow: Boolean,
	): PasteOutcome {
		// Skips rows already in target.
		val present = ItemRepository.children(np.id).mapTo(HashSet()) { it.id }
		var skipped = false
		var refusedCycle = false
		var refusedSubject = false
		val moved = ArrayList<PasteSrc>()
		for (armed in src) {
			if (armed.id in present) {
				skipped = true
				continue
			}
			// Parentless row references instead of moving.
			val oldParent = armed.oldParent
			when {
				// Reference and subject merge refuse on cycle.
				referencing || oldParent == null ->
					if (!ItemRepository.reference(armed.id, np.id)) refusedCycle = true
				// Cut subject merges whole tree.
				oldParent == ROOT_ID ->
					if (!ItemRepository.mergeSubject(armed.id, np.id)) refusedCycle = true
				else -> when (ItemRepository.move(armed.id, oldParent, np.id)) {
					ItemRepository.MoveResult.MOVED -> moved.add(armed)
					ItemRepository.MoveResult.REFUSED_CYCLE -> refusedCycle = true
					ItemRepository.MoveResult.REFUSED_OTHER_SUBJECT -> refusedSubject = true
					ItemRepository.MoveResult.ALREADY_PRESENT -> skipped = true
				}
			}
		}
		// Moved hit carries new placement for jumps.
		val placements = if (searchNow) moved.map {
			val fresh = ItemRepository.get(it.id) ?: return@performPaste PasteOutcome(null, emptyList())
			it.row to it.row.readPlacement(np.id, fresh)
		} else emptyList()
		val notice = when {
			refusedCycle -> UiNotice.PasteInsideItself
			refusedSubject -> UiNotice.PasteOtherSubject
			skipped -> UiNotice.PasteAlreadyPresent
			else -> null
		}
		return PasteOutcome(notice, placements)
	}

	fun cancelPaste() {
		armPaste(null)
	}
}
