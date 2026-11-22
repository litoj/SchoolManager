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

		/** The rows the armed paste carries, gathered by the explorer batch. */
		fun batchRows(): List<ItemRow>

		fun startBatch(rows: List<ItemRow>)

		fun clearBatch()

		/** Whether a drag is adding its range right now. */
		fun batchDragging(): Boolean
	}

	private class PasteSrc(val row: ItemRow, val id: Int, val oldParent: Int?, val reference: Boolean)

	private class PasteOutcome(val notice: UiNotice?, val placements: List<Pair<ItemRow, Placement>>)

	/** The kind of the armed paste, true for references, null while none waits. */
	private var referencing by mutableStateOf<Boolean?>(null)

	/** The open block read, the newest one only. */
	private var blockJob: Job? = null

	/** The newest block read only, so an old answer cannot land after a newer one. */
	private var blockGeneration = 0

	val armed: Boolean get() = referencing != null

	private fun armPaste(kind: Boolean?, rows: List<ItemRow>) {
		referencing = kind
		host.refreshReorderable()
		if (kind == null) host.clearBatch() else host.startBatch(rows)
	}

	/** An emptied batch ends the paste, as its cancel does. */
	fun onBatchChanged() {
		if (armed && host.batchRows().isEmpty()) armPaste(null, emptyList())
		// a drag reads the block once at its end, since each step would cost a graph walk
		else if (!host.batchDragging()) refreshBlock()
	}

	/** Why the open container refuses the armed rows, 0 when the paste may run. */
	@get:StringRes
	var blockedReason by mutableIntStateOf(0)
		private set

	/** Re-reads why the open container refuses the armed rows. */
	fun refreshBlock() {
		blockJob?.cancel()
		blockJob = null
		val generation = ++blockGeneration
		val kind = referencing
		if (kind == null) {
			blockedReason = 0
			return
		}
		val target = host.path.lastOrNull()
		if (target == null) {
			blockedReason = R.string.fail_paste_target
			return
		}
		// snapshot on main, so the answer can only describe one container and one batch
		val src = host.batchRows().map { PasteSrc(it, it.item.id, it.parentId, it.isReference) }
		blockJob = scope.launch(io) {
			val facts = ItemRepository.pasteFacts(src.map { it.id }, target.id)
			// the read costs a graph walk per row, so a superseded one must not answer
			if (!isActive) return@launch
			// the posted write lands later, so the paste may have ended meanwhile
			host.postToUi {
				if (generation == blockGeneration && referencing == kind)
					blockedReason = blockOf(kind, src, facts)
			}
		}
	}

	/**
	 * The mute rule for one batch, the first refusal its writes would report.
	 * - a batch the target already holds would only lose the armed rows.
	 * - a cut home row still runs where the target holds only its reference.
	 */
	private fun blockOf(referencing: Boolean, src: List<PasteSrc>, facts: List<PasteFacts>): Int {
		var allPresent = true
		for (index in src.indices) {
			val oldParent = src[index].oldParent
			// a reference and a cut subject cross sets on purpose.
			val staysInSet = !referencing && oldParent != null && oldParent != ROOT_ID
			if (staysInSet && facts[index].otherSet) return R.string.fail_paste_other_subject
			if (facts[index].inside) return R.string.fail_paste_inside
			// a home row promotes the target's reference link, so it is not present
			val promotes = staysInSet && !src[index].reference && facts[index].reference
			if (!facts[index].present || promotes) allPresent = false
		}
		return if (allPresent) R.string.fail_paste_present else 0
	}

	/** What the armed paste holds, its kind and the rows it carries. */
	val armedReferences: Boolean get() = referencing == true

	val armedCount: Int get() = if (armed) host.batchRows().size else 0

	/** Arms the paste from the selection, where true means references, see [doPaste]. */
	fun move(reference: Boolean) {
		if (host.selectionCount() == 0) return
		val picked = host.items().filter(ItemRow::selected)
		host.clearSelection()
		armPaste(reference, picked)
	}

	/** A browse list re-reads, and a search list re-maps. */
	fun doPaste() {
		val kind = referencing ?: return
		// snapshot on main
		val src = host.batchRows().map { PasteSrc(it, it.item.id, it.parentId, it.isReference) }
		armPaste(null, emptyList())
		val np = host.path.lastOrNull()
		if (np == null) {
			UserMessages.showNotice(UiNotice.PasteNoTarget)
			return
		}
		val searchNow = host.searching
		scope.launch(io) {
			val outcome = performPaste(kind, src, np, searchNow)
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
			val oldParent = armed.oldParent
			// a present row skips where its write could only keep it as it is
			if (armed.id in present && (referencing || oldParent == null || oldParent == ROOT_ID)) {
				skipped = true
				continue
			}
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
		armPaste(null, emptyList())
	}
}
