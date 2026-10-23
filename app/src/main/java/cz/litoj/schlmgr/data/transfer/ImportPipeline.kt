package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.FileProgress
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.data.json.HJsonImporter
import cz.litoj.schlmgr.data.legacy.LegacyJsonLoader
import cz.litoj.schlmgr.data.legacy.LegacySubjectImporter
import cz.litoj.schlmgr.data.wordlist.WordListImporter
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.db.DbItem
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
	/** The view may be gone while the worker runs, so nothing binds the thread to it. */
	fun start(): Thread =
		Thread({ runTemplate() }, threadName).also { it.start() }

	private fun runTemplate() {
		try {
			doRun()
		} catch (iae: IllegalArgumentException) {
			onParseFail(iae)
		} catch (e: Exception) {
			onLoadFail(e)
		} catch (e: StackOverflowError) {
			// pathologically nested input, a parse failure rather than a process crash
			onParseFail(IllegalArgumentException("The file nests too deeply", e))
		}
		onSettled()
	}

	/** Read -> parse -> persist -> refresh/notify. Runs on the worker thread. */
	protected abstract fun doRun()

	/** Runs after the attempt, failure or not, for the root reload of the dir import. */
	protected open fun onSettled() {}

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
	): ItemRepository.ImportResult =
		ItemRepository.import(progress.importer(importer), anchorId)

	protected fun notifyImported(result: ItemRepository.ImportResult) {
		Startup.onImportSuccess(result.words, result.chapters)
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
			val outcome = persist(WordListImporter(self?.name ?: FileGateway.containerNameFrom(fileName), content), progress, anchor)
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
 * - the file name only labels the parse errors, see [FileGateway.containerNameFrom].
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
		val name = FileGateway.containerNameFrom(FileGateway.displayName(appContext, uri, ""))
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			val result = persist(WordListImporter(name, content), progress)
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
			// no open container means the root, where the file chapters become subjects
			val anchor = openContainer()?.id ?: ROOT_ID
			val result = persist(HJsonImporter(content), progress, anchor)
			post { if (atRoot()) onRootReload() else onCurrentReload() }
			notifyImported(result)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * A subject-directory import from files, a one-time conversion with no link back to the folder.
 * - every file parses before anything is written.
 * - a non-subject directory reports the load failure, leaving nothing behind.
 * - the root reload runs past validation, even on a failed import.
 * - it never runs on a rejected pick.
 */
class LegacyDirImport(
	appContext: Context,
	postToUi: (Runnable) -> Unit,
	private val uri: Uri,
	private val atRoot: () -> Boolean,
	private val onRootReload: () -> Unit,
) : FileWorker(appContext, postToUi, "Legacy dir import") {

	private var attempted = false

	private var dirName: String? = null

	override fun doRun() {
		val dir = DocumentFile.fromTreeUri(appContext, uri)
		val name = dir?.name ?: uri.toString()
		dirName = name
		if (dir == null || !LegacyJsonLoader.isSubjectDir(dir)) {
			Startup.onLoadFail(
				IllegalArgumentException("Not an old-format subject directory"), name)
			return
		}
		attempted = true
		FileGateway.titledProgress(appContext, R.string.progress_import, name).use { progress ->
			progress.bytes(LegacyJsonLoader.subjectSize(dir))
			// the gateway reads the directory files as pure text, so the bytes report to the bar.
			// - an unreadable file already reported itself through FileText.
			// - returning quietly here keeps the one real failure report.
			val subject = FileGateway.loadLegacySubject(dir, progress) ?: return
			val result = persist(LegacySubjectImporter(subject), progress)
			notifyImported(result)
		}
	}

	override fun onSettled() {
		if (attempted && atRoot()) post { onRootReload() }
	}

	// a directory read failure is no parse failure, so it reports as a load failure
	override fun onParseFail(e: IllegalArgumentException) = onLoadFail(e)

	override fun failureName(): String =
		// the fallback re-queries the SAF, since the first query may be exactly
		// what failed. Guarded, or the failure handler itself would crash the process
		dirName ?: runCatching { DocumentFile.fromTreeUri(appContext, uri)?.name }.getOrNull()
			?: uri.toString()
}

/**
 * The one shared export path, with the codec as the injected [ExportFormat] strategy.
 * - the word-list and tree-JSON target workers differ only in the format they hand over.
 * - the containers are the pick-time snapshot of the caller.
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
		val containers = selected()
		if (containers.isEmpty()) return
		// the callback touches UI state, the selection clear, so it runs on
		// the main thread like every other follow-up
		post { onExportStart() }
		val request = ExportRequest(uri, containers, format)
		val name = FileGateway.displayName(appContext, request.uri)
		FileGateway.titledProgress(appContext, R.string.progress_export, name).use { progress ->
			// wrapped, since the stream handover targets the bar at the object count
			val text = ItemRepository.export(progress.exporter(format.exporter()), request.containers)
			val (words, chapters) = ItemRepository.subgraphStats(request.containers.map { it.id })
			FileGateway.writeExport(request.uri, name, text, words, chapters)
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
