package cz.litoj.schlmgr.ui.screen.subjects

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.data.transfer.LegacyDirImport
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The import and export of the subjects screen.
 * - it covers the target picks, the dispatch and the landing.
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

		fun requestScrollToBottom()

		fun refreshInfo()
	}

	/**
	 * The file name of an export of the selected rows, null when none is writable.
	 * - see [exportRows] for what a selection may hold.
	 */
	private fun exportFileName(): String? {
		val rows = exportRows()
		if (rows.isEmpty()) return null
		val open = host.path.lastOrNull()
		return when {
			rows.size == 1 -> rows[0].name
			// a multi selection inside a container is a partial export of it
			open != null -> open.name + PARTIAL_SUFFIX
			else -> "subjects"
		}
	}

	/** The word-list export of the selected rows. */
	fun exportWordFile() {
		val name = exportFileName() ?: return
		host.emitRequest(Request.PickWordExport(name + ".txt"))
	}

	/** The tree-JSON export of the selected rows. */
	fun exportJsonFile() {
		val name = exportFileName() ?: return
		host.emitRequest(Request.PickJsonExport(name))
	}

	/**
	 * The selected rows, an export always covers a selection.
	 * - a translation row has no line of any format, so it never joins.
	 */
	internal fun exportRows(): List<DbItem> =
		host.items()
			.filter { it.selected && it.item.type != ItemKind.TRANSLATION }
			.map { it.item }

	/** Dispatches a picked file, the provider reads run on io. */
	fun onAnyFilePicked(uri: Uri) {
		val open = host.path.lastOrNull()
		val atRoot = host.path.isEmpty()
		scope.launch(io) {
			val worker = ImporterFactory.createPickedImport(
				host.ctx, host::postToUi, uri, "",
				{ open }, { atRoot },
				{
					host.requestScrollToBottom()
					host.setContent(null)
				},
				{
					host.requestScrollToBottom()
					host.reloadContent()
				},
				{
					host.requestScrollToBottom()
					host.reloadContent()
					host.refreshInfo()
				},
			)
			host.postToUi { worker.start() }
		}
	}

	fun onWordTargetPicked(uri: Uri) {
		val selected = exportRows()
		ExportPipeline(host.ctx, host::postToUi, uri, ExportFormat.WORD, { selected }, host::clearSelection).start()
	}

	fun onJsonTargetPicked(uri: Uri) {
		val selected = exportRows()
		ExportPipeline(host.ctx, host::postToUi, uri, ExportFormat.JSON, { selected }, host::clearSelection).start()
	}

	fun onDirPicked(uri: Uri) {
		val atRoot = host.path.isEmpty()
		LegacyDirImport(host.ctx, host::postToUi, uri, { atRoot }) {
			host.requestScrollToBottom()
			host.setContent(null)
		}.start()
	}
}

/** The suffix of a multi selection export of the open container. */
private const val PARTIAL_SUFFIX = " - partial"
