package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ImportResult
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ROOT_ID

/**
 * Template-Method base of the file workers, imports and the shared export.
 * - each worker runs on its own named thread.
 */
abstract class FileWorker(
	protected val appContext: Context,
	private val postToUi: (Runnable) -> Unit,
	private val threadName: String,
) {
	/**
	 * The import result callback, set before start.
	 * - it runs on the worker thread.
	 * - the import mover uses it to open a single imported container.
	 */
	var onImported: ((ImportResult) -> Unit)? = null

	/** The view may be gone while the worker runs, so nothing binds the thread to it. */
	fun start(): Thread =
		Thread({ runTemplate() }, threadName).also { it.start() }

	private fun runTemplate() {
		try {
			doRun()
		} catch (iae: IllegalArgumentException) {
			when (iae) {
				is WordListParseException -> onWordListParseFail(iae)
				else -> onParseFail(iae)
			}
		} catch (e: Exception) {
			onLoadFail(e)
		} catch (e: StackOverflowError) {
			// pathologically nested input, a parse failure rather than a process crash
			onParseFail(IllegalArgumentException("The file nests too deeply", e))
		}
	}

	/** Read -> parse -> persist -> refresh/notify. Runs on the worker thread. */
	protected abstract fun doRun()

	protected open fun onWordListParseFail(e: WordListParseException) {
		Startup.onWordListParseFail(e)
	}

	protected open fun onParseFail(e: IllegalArgumentException) {
		Startup.onParseFail(e.message ?: "")
	}

	protected open fun onLoadFail(e: Exception) {
		Startup.onLoadFail(e, failureName())
	}

	protected abstract fun failureName(): String

	protected fun post(work: () -> Unit) = postToUi(Runnable { work() })

	// reads the whole picked document, and a failure already went out, so skip then
	protected fun readContent(uri: Uri, progress: FileProgress): String? =
		FileText.readText(uri, progress)

	// the save loop drives the row phase of the bar
	protected fun persist(
		importer: DataImporter, progress: FileProgress,
		anchorId: Int = ROOT_ID,
	): ImportResult =
		ItemRepository.import(progress.importer(importer), anchorId)

	protected fun notifyImported(result: ImportResult) {
		Startup.onImportSuccess(result.words, result.chapters)
		onImported?.invoke(result)
	}
}

/**
 * The parsed stream is saved under the open container, and then the open content re-reads
 * from the database.
 */
class WordIntoContainerImport(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val fileName: String,
	private val openContainer: () -> DbItem?,
	private val atRoot: () -> Boolean,
	private val onRootReload: () -> Unit,
	private val onRefreshed: () -> Unit,
) : FileWorker(appContext, postToUi, "Word list import") {

	override fun doRun() {
		// the resolver holds the pick-time snapshot, never null for a container
		// target. The root anchor below is the defensive default
		val self = openContainer()
		val anchor = self?.id ?: ROOT_ID
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			// wrapped, since the save loop drives the row phase of the bar
			val outcome = persist(WordListImporter(fileName.ifEmpty { uri.toString() }, content), progress, anchor)
			post { if (self == null && atRoot()) onRootReload() else onRefreshed() }
			notifyImported(outcome)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * A word list picked at the root: the file content lands as it is, with no wrapper.
 * - a saved container wrapper chapter becomes a subject again.
 * - bare words land as the rows of the root.
 * - the file name only labels the parse errors.
 * - the importer parses the whole text before anything is written.
 * - the subjects screen reloads instead of adding rows.
 */
class WordAsSubjectImport(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val onRootReload: () -> Unit,
) : FileWorker(appContext, postToUi, "Subject file import") {

	override fun doRun() {
		val fileName = FileGateway.displayName(appContext, uri, uri.toString())
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			val result = persist(WordListImporter(fileName, content), progress)
			post { onRootReload() }
			notifyImported(result)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * A tree JSON import in HJSON form: the codec parses the text into chapters.
 * - the repository persists the stream in one transaction.
 * - the pick-time open container anchors the top-level chapters.
 * - with none picked, they become subjects of the root.
 * - a subject-level reload re-reads the open content otherwise.
 */
class JsonTreeImport(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val openContainer: () -> DbItem?,
	private val atRoot: () -> Boolean,
	private val onRootReload: () -> Unit,
	private val onCurrentReload: () -> Unit,
) : FileWorker(appContext, postToUi, "JSON import") {

	override fun doRun() {
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			val fileName = FileGateway.displayName(appContext, uri, uri.toString())
			val importer = try {
				HJsonImporter(content)
			} catch (e: IllegalArgumentException) {
				throw IllegalArgumentException("$fileName: ${e.message}", e)
			}
			// no open container means the root, where the file chapters become subjects
			val anchor = openContainer()?.id ?: ROOT_ID
			val result = persist(importer, progress, anchor)
			post { if (atRoot()) onRootReload() else onCurrentReload() }
			notifyImported(result)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * The one shared export path, with the codec as the injected [ExportFormat] strategy.
 * - the word-list and tree-JSON target workers differ only in the format they hand over.
 * - the selected rows are the pick-time snapshot of the caller.
 */
class ExportPipeline(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val format: ExportFormat,
	private val selected: () -> List<DbItem>,
	private val onExportStart: () -> Unit,
) : FileWorker(appContext, postToUi, format.threadName()) {

	override fun doRun() {
		val rows = selected()
		if (rows.isEmpty()) return
		// the callback touches UI state, the selection clear, so it runs on
		// the main thread like every other follow-up
		post { onExportStart() }
		val request = ExportRequest(uri, rows, format)
		val name = FileGateway.displayName(appContext, request.uri)
		FileGateway.titledProgress(appContext, R.string.progress_export, name).use { progress ->
			// wrapped, since the stream handover targets the bar at the object count
			val text = ItemRepository.export(progress.exporter(format.exporter()), request.rows)
			val (words, chapters) = ItemRepository.subgraphStats(request.rows.map { it.id })
			FileGateway.writeExport(request.uri, name, text, words, chapters, format.mimeType())
		}
	}

	// the IO failure of the write already reports through FileText, so anything
	// else reaching here is an export failure, not a load
	override fun onLoadFail(e: Exception) {
		Startup.onSaveFail(e, failureName())
	}

	// the refusals of the writer, such as an unrepresentable name, arrive here as
	// IllegalArgumentException, an export failure rather than a parse failure
	override fun onParseFail(e: IllegalArgumentException) {
		Startup.onSaveFail(e, failureName())
	}

	override fun failureName(): String = uri.toString()
}
