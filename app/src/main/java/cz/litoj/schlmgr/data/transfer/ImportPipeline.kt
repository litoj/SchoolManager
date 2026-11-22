package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.wordlist.WordListAutoFix
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

	/**
	 * The door that runs this import again on a file the user has fixed, set before start.
	 * - the failure editor offers Save where a retry has an owner.
	 */
	var onReimport: ((Uri) -> Unit)? = null

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

	/** The document a failure of this worker came from, the file the editor fixes. */
	protected open val sourceUri: Uri? get() = null

	/** The text of that document, the content the editor shows for it. */
	protected var sourceText: String? = null
		private set

	protected open fun onWordListParseFail(e: WordListParseException) {
		Startup.onWordListParseFail(e, sourceUri, sourceText, onReimport)
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
		FileText.readText(uri, progress).also { sourceText = it }

	// the save loop drives the row phase of the bar
	protected fun persist(
		importer: DataImporter, progress: FileProgress,
		anchorId: Int = ROOT_ID,
	): ImportResult =
		ItemRepository.import(progress.importer(importer), anchorId)

	protected fun notifyImported(result: ImportResult, fixes: List<WordListAutoFix> = emptyList()) {
		// the stored file needs no editor any more, so its held grant leaves here
		sourceUri?.let { FileGateway.releaseEdit(appContext, it) }
		Startup.onImportSuccess(result.words, result.chapters, fixes)
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
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			// wrapped, since the save loop drives the row phase of the bar
			val importer = WordListImporter(
				fileName.ifEmpty { uri.toString() }, content, wordListWrapper(fileName, self))
			val outcome = persist(importer, progress, importAnchor(importer, self))
			post { if (self == null && atRoot()) onRootReload() else onRefreshed() }
			notifyImported(outcome, importer.autoFixes)
		}
	}

	override val sourceUri: Uri? get() = uri

	override fun failureName(): String = uri.toString()
}

/** The suffixes a picked document name can carry, so the stem drops only a real suffix. */
private val fileSuffixes = setOf("txt", "text", "md", "csv", "json", "hjson")

/**
 * The chapter that holds a bare word-list top level, named by the file stem.
 * - a name without a known suffix keeps its dots, so no part of it is lost.
 * - an unnamed file gets none.
 * - a target already named so gets none, since it takes the content itself.
 */
private fun wordListWrapper(fileName: String, target: DbItem?): String? {
	val dot = fileName.lastIndexOf('.')
	val stem = if (dot > 0 && fileName.substring(dot + 1).lowercase() in fileSuffixes)
		fileName.substring(0, dot) else fileName
	return stem.trim().takeIf { it.isNotBlank() && it != target?.name }
}

/**
 * The anchor of an import into [target], the root without one.
 * - a file whose one top-level container bears the target name anchors at the target home.
 * - so that container merges into the target by name instead of nesting inside it.
 */
private fun importAnchor(importer: DataImporter, target: DbItem?): Int {
	val self = target ?: return ROOT_ID
	val top = importer.items.filter { it.parent == null }.singleOrNull()?.item
	if (top == null || top.type != self.type || top.name != self.name) return self.id
	return ItemRepository.plainParentOf(self.id) ?: self.id
}

/**
 * A word list picked at the root, where its top-level chapters become subjects.
 * - the importer parses the whole text before anything is written.
 * - the subjects screen reloads instead of adding rows.
 */
class WordAsSubjectImport(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val fileName: String,
	private val onRootReload: () -> Unit,
) : FileWorker(appContext, postToUi, "Subject file import") {

	override fun doRun() {
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			val importer = WordListImporter(
				fileName.ifEmpty { uri.toString() }, content, wordListWrapper(fileName, null))
			val result = persist(importer, progress)
			post { onRootReload() }
			notifyImported(result, importer.autoFixes)
		}
	}

	override val sourceUri: Uri? get() = uri

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
			val result = persist(importer, progress, importAnchor(importer, openContainer()))
			post { if (atRoot()) onRootReload() else onCurrentReload() }
			notifyImported(result)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * The one shared export path, with the codec as the injected [ExportFormat] strategy.
 * - the word-list and tree-JSON workers differ only in the format they hand over.
 * - the destination picks between the picked document and the cache copy of a share.
 * - the selected rows are the pick-time snapshot of the caller.
 */
class ExportPipeline(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val destination: ExportDestination,
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
		val name = exportName()
		FileGateway.titledProgress(appContext, R.string.progress_export, name).use { progress ->
			// wrapped, since the stream handover targets the bar at the object count
			val text = ItemRepository.export(progress.exporter(format.exporter()), rows)
			when (destination) {
				is ExportDestination.Document -> {
					val (words, chapters) = ItemRepository.subgraphStats(rows.map { it.id })
					FileGateway.writeExport(destination.uri, name, text, words, chapters, format.mimeType())
				}
				// a share reports itself through the sheet, so it needs no statistics
				is ExportDestination.Share -> Startup.onShareReady(
					FileGateway.shareCopy(appContext, destination.fileName, text), format.mimeType(),
				)
			}
		}
	}

	/** The picked document names itself, the share carries the name of its copy. */
	private fun exportName(): String = when (destination) {
		is ExportDestination.Document -> FileGateway.displayName(appContext, destination.uri)
		is ExportDestination.Share -> destination.fileName
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

	override fun failureName(): String = exportName()
}
