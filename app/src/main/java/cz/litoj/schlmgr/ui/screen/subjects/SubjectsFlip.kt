package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.FlipAnalysis
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.FlipConflictsResult
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The flip flow of the subjects screen.
 * - a chapter stands for the words under it.
 * - relations across the selection and the outside pass the conflict popup first.
 */
internal class SubjectsFlip(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {
		fun items(): List<ItemRow>

		val searching: Boolean

		/** The open chain, where the acting parent sits in the last entry. */
		val path: List<DbItem>

		fun emitRequest(request: Request)

		fun postToUi(work: Runnable)

		fun clearSelection()

		fun reloadContent()

		fun refreshInfo()
	}

	/**
	 * A flip run between the selection and the first gate.
	 * - it blocks a second start until [continueFlip], or until an empty selection clears it.
	 */
	private var starting = false

	fun flipSelected() {
		if (starting) return
		starting = true
		val selected = host.items().filter(ItemRow::selected)
		if (selected.isEmpty()) {
			starting = false
			return
		}
		val ids = selected.map { it.item.id }
		// the open container, or every hit parent in search.
		val actingParents = if (host.searching) selected.mapNotNull { it.parentId }.distinct()
		else listOf(host.path.lastOrNull()?.id ?: ROOT_ID)
		scope.launch(io) {
			val analysis = ItemRepository.startFlip(actingParents, ids)
			host.postToUi { continueFlip(analysis) }
		}
	}

	private fun continueFlip(analysis: FlipAnalysis) {
		starting = false
		if (analysis.conflicts.isEmpty()) {
			scope.launch(io) {
				ItemRepository.commitFlip(analysis)
				host.postToUi {
					host.clearSelection()
					host.reloadContent()
					host.refreshInfo()
				}
			}
			return
		}
		host.emitRequest(Request.ResolveFlipConflicts(analysis))
	}

	/**
	 * A false verdict keeps the popup open.
	 * - a kept row without a name refuses with a notice.
	 */
	fun commitFlipResolutions(analysis: FlipAnalysis, result: FlipConflictsResult): Boolean {
		if (result.kept.any { it.name.trim().isEmpty() }) {
			UserMessages.showNotice(UiNotice.FlipBlankName)
			return false
		}
		val resolutions = result.kept.map { FlipResolution(it.source.id, false, it.name, it.description) } +
			result.removed.map { FlipResolution(it.id, true, it.name, it.description) }
		// the popup is gone but the run is not, so the next start waits
		starting = true
		scope.launch(io) {
			val next = ItemRepository.advanceFlip(analysis, resolutions)
			host.postToUi { continueFlip(next) }
		}
		return true
	}
}
