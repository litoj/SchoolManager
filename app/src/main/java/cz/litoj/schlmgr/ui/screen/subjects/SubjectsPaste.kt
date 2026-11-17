package cz.litoj.schlmgr.ui.screen.subjects

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.ItemRepository.PasteFacts
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.Placement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The cut and reference paste of the subjects screen.
 * - it covers the armed state, the placement and the follow-up.
 */
internal class SubjectsPaste(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host : SubjectsHost {
		fun selectionCount(): Int

		fun notifyChanged()

		fun refreshReorderable()
	}

	private class PasteData(
		val referencing: Boolean,
		val src: List<ItemRow>,
	)

	private class PasteSrc(val row: ItemRow, val id: Int, val oldParent: Int?)

	private class PasteOutcome(val notice: UiNotice?, val placements: List<Pair<ItemRow, Placement>>)

	private var pasteData by mutableStateOf<PasteData?>(null)

	/** The open block read, the newest one only. */
	private var blockJob: Job? = null

	val armed: Boolean get() = pasteData != null

	private fun armPaste(data: PasteData?) {
		pasteData = data
		host.refreshReorderable()
		refreshBlock()
	}

	/** Why the open container refuses the armed rows, 0 when the paste may run. */
	@get:StringRes
	var blockedReason by mutableIntStateOf(0)
		private set

	/** Re-reads why the open container refuses the armed rows. */
	fun refreshBlock() {
		blockJob?.cancel()
		blockJob = null
		val paste = pasteData
		if (paste == null) {
			blockedReason = 0
			return
		}
		val target = host.path.lastOrNull()
		if (target == null) {
			blockedReason = R.string.fail_paste_target
			return
		}
		// snapshot on main, so the answer can only describe one container and one batch
		val src = paste.src.map { PasteSrc(it, it.item.id, it.parentId) }
		blockJob = scope.launch(io) {
			val facts = ItemRepository.pasteFacts(src.map { it.id }, target.id)
			// the read costs a graph walk per row, so a superseded one must not answer
			if (!isActive) return@launch
			host.postToUi { blockedReason = blockOf(paste.referencing, src, facts) }
		}
	}

	/**
	 * The mute rule for one batch, the first refusal its writes would report.
	 * - a batch the target already holds would only lose the armed rows.
	 */
	private fun blockOf(referencing: Boolean, src: List<PasteSrc>, facts: List<PasteFacts>): Int {
		var allPresent = true
		for (index in src.indices) {
			val oldParent = src[index].oldParent
			// a reference and a cut subject cross sets on purpose.
			val staysInSet = !referencing && oldParent != null && oldParent != ROOT_ID
			if (staysInSet && facts[index].otherSet) return R.string.fail_paste_other_subject
			if (facts[index].inside) return R.string.fail_paste_inside
			if (!facts[index].present) allPresent = false
		}
		return if (allPresent) R.string.fail_paste_present else 0
	}

	/** What the armed paste holds, its kind and the rows it carries. */
	val armedReferences: Boolean get() = pasteData?.referencing == true

	val armedCount: Int get() = pasteData?.src?.size ?: 0

	/** Arms the paste from the selection, where true means references, see [doPaste]. */
	fun move(reference: Boolean) {
		if (host.selectionCount() == 0) return
		armPaste(PasteData(
			referencing = reference,
			src = host.items().filter(ItemRow::selected),
		))
		host.clearSelection()
	}

	/** A browse list re-reads, and a search list re-maps. */
	fun doPaste() {
		val paste = pasteData ?: return
		armPaste(null)
		val np = host.path.lastOrNull()
		if (np == null) {
			UserMessages.showNotice(UiNotice.PasteNoTarget)
			return
		}
		val searchNow = host.searching
		// snapshot on main
		val src = paste.src.map { PasteSrc(it, it.item.id, it.parentId) }
		scope.launch(io) {
			val outcome = performPaste(paste.referencing, src, np, searchNow)
			host.postToUi {
				outcome.notice?.let(UserMessages::showNotice)
				outcome.placements.forEach { (row, placement) -> row.applyPlacement(placement) }
				if (searchNow) host.notifyChanged() else host.reloadContent()
				host.refreshInfo()
			}
		}
	}

	private suspend fun performPaste(
		referencing: Boolean,
		src: List<PasteSrc>,
		np: DbItem,
		searchNow: Boolean,
	): PasteOutcome {
		// skips the rows already in the target.
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
			// a parentless row references instead of moving.
			val oldParent = armed.oldParent
			when {
				// a reference and a subject merge refuse on a cycle.
				referencing || oldParent == null ->
					if (!ItemRepository.reference(armed.id, np.id)) refusedCycle = true
				// a cut subject merges its whole tree.
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
		// a moved hit carries its new placement for jumps.
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
