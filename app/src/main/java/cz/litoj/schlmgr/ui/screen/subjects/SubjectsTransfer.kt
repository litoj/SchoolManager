package cz.litoj.schlmgr.ui.screen.subjects

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportDestination
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.data.transfer.ExportPipeline
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.popup.ExportPopup
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal typealias ExportStarter = (
	Context, (Runnable) -> Unit, ExportDestination, ExportFormat, () -> List<DbItem>, () -> Unit,
) -> Unit

private val startExportPipeline: ExportStarter = { ctx, post, destination, format, selected, onStart ->
	ExportPipeline(ctx, post, destination, format, selected, onStart).start()
}

/**
 * The import and export of the subjects screen.
 * - it covers the target picks, the dispatch and the landing.
 */
internal class SubjectsTransfer(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
	private val settings: Settings,
	private val startExport: ExportStarter = startExportPipeline,
) {

	interface Host : SubjectsHost {
		val ctx: Context

		fun setContent(container: DbItem?)

		fun requestScrollToBottom()
	}

	private data class ExportSession(val rows: List<DbItem>, val container: DbItem?)

	private var exportSession: ExportSession? = null

	/** The sheet where the user picks the format and its destination. */
	fun openExportSheet() {
		// the strip clears its selection next, so the callbacks own their own snapshot
		exportSession = ExportSession(exportRows(), host.path.lastOrNull())
		ExportPopup(settings, ::saveFile, ::shareFile)
	}

	/** The save dialog of the format, the picked document receives the rows. */
	fun saveFile(format: ExportFormat) {
		val session = exportSession ?: return
		val name = exportFileName(format, session.rows, session.container) ?: return
		host.emitRequest(
			if (format == ExportFormat.WORD) Request.PickWordExport(name)
			else Request.PickJsonExport(name)
		)
	}

	/** The cache copy of the format, straight into the share sheet of the system. */
	fun shareFile(format: ExportFormat) {
		val session = exportSession ?: return
		val name = exportFileName(format, session.rows, session.container) ?: return
		exportSession = null
		startExport(
			host.ctx, host::postToUi, ExportDestination.Share(name), format,
			{ session.rows }, host::clearSelection,
		)
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
			// a fix saved in the failure editor runs this very import over again
			worker.onReimport = ::onAnyFilePicked
			host.postToUi { worker.start() }
		}
	}

	fun onWordTargetPicked(uri: Uri) {
		val session = exportSession ?: return
		exportSession = null
		startExport(host.ctx, host::postToUi, ExportDestination.Document(uri), ExportFormat.WORD,
			{ session.rows }, host::clearSelection)
	}

	fun onJsonTargetPicked(uri: Uri) {
		val session = exportSession ?: return
		exportSession = null
		startExport(host.ctx, host::postToUi, ExportDestination.Document(uri), ExportFormat.JSON,
			{ session.rows }, host::clearSelection)
	}
}

/**
 * The file name of an export of [rows] picked in [container], null when the selection writes nothing.
 * - a multi selection inside a container is a partial export of it.
 * - a multi selection of subjects has no container, so the list names itself.
 */
internal fun exportFileName(format: ExportFormat, rows: List<DbItem>, container: DbItem?): String? {
	if (rows.isEmpty()) return null
	val stem = when {
		rows.size == 1 -> rows[0].name
		container != null -> container.name + PARTIAL_SUFFIX
		else -> "subjects"
	}
	return stem + format.suffix()
}

/** The suffix of a multi selection export of the open container. */
private const val PARTIAL_SUFFIX = " - partial"
