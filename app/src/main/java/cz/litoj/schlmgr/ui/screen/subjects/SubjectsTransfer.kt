package cz.litoj.schlmgr.ui.screen.subjects

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.FileGateway
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.data.transfer.LegacyDirImport
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Import/export — target picks, dispatch, landing.
 */
internal class SubjectsTransfer(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {
		val ctx: Context

		fun items(): List<ItemRow>

		val path: List<DbItem>

		fun emitRequest(request: Request)

		fun postToUi(work: Runnable)

		fun clearSelection()

		fun setContent(container: DbItem?)

		fun reloadContent()

		fun refreshInfo()
	}

	fun exportWordFile() {
		host.emitRequest(Request.PickWordExport(exportName() + ".txt"))
	}

	fun exportJsonFile() {
		host.emitRequest(Request.PickJsonExport(exportName()))
	}

	private fun selectedContainers(): List<DbItem> =
		host.items().filter { it.selected && it.item.type == ItemKind.CHAPTER }.map { it.item }

	/**
	 * Selection containers, else open or all subjects. Only containers export.
	 */
	internal fun exportContainers(): List<DbItem> {
		val selected = selectedContainers()
		if (selected.isNotEmpty()) return selected
		val open = host.path.lastOrNull()
		return if (open != null) listOf(open)
		else host.items().filter { it.item.type == ItemKind.CHAPTER }.map { it.item }
	}

	private fun exportName(): String {
		val containers = exportContainers()
		return if (containers.size == 1) containers[0].name else "subjects"
	}

	/** Dispatches picked file via [ImporterFactory]. */
	fun onAnyFilePicked(uri: Uri) {
		val target = ImporterFactory.targetFor(host.path.lastOrNull())
		val open = host.path.lastOrNull()
		val atRoot = host.path.isEmpty()
		scope.launch(io) {
			val name = FileGateway.displayName(host.ctx, uri, "")
			// the document's own shape decides its format — the name may
			// resolve to nothing on some providers
			val jsonContent = FileGateway.startsAsJson(host.ctx, uri)
			val worker = ImporterFactory.create(
				host.ctx, host::postToUi, uri, name, jsonContent, target, { open }, { atRoot },
				{ host.setContent(null) },
				{ host.reloadContent() },
				{
					host.reloadContent()
					host.refreshInfo()
				},
			)
			host.postToUi { worker.start() }
		}
	}

	fun onWordTargetPicked(uri: Uri) {
		val selected = exportContainers()
		ExportPipeline(host.ctx, host::postToUi, uri, ExportFormat.WORD, { selected }, host::clearSelection).start()
	}

	fun onJsonTargetPicked(uri: Uri) {
		val selected = exportContainers()
		ExportPipeline(host.ctx, host::postToUi, uri, ExportFormat.JSON, { selected }, host::clearSelection).start()
	}

	fun onDirPicked(uri: Uri) {
		val atRoot = host.path.isEmpty()
		LegacyDirImport(host.ctx, host::postToUi, uri, { atRoot }) { host.setContent(null) }.start()
	}
}
