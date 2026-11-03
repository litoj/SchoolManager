package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.ui.viewmodel.ItemRow

/**
 * The doors every satellite of the subjects screen shares, see the satellite Host interfaces.
 * - one interface keeps the adapters of [MainExplorerViewModel] free of repetition.
 */
internal interface SubjectsHost {
	fun items(): List<ItemRow>

	val path: List<DbItem>

	val searching: Boolean

	fun emitRequest(request: MainExplorerViewModel.Request)

	fun postToUi(work: Runnable)

	fun clearSelection()

	fun reloadContent()

	fun refreshInfo()
}
