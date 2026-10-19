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
 * Template-Method base of the file workers (imports and the shared export),
 * each on its own named thread — an import reads, parses and persists, while an
 * export walks, renders and writes. Both refresh the UI and notify at the end.
 */
abstract class FileWorker(
	protected val appContext: Context,
	private val postToUi: (Runnable) -> Unit,
	private val threadName: String,
) {
	/** Starts the raw worker thread — the view may be gone while it runs. */
	fun start(): Thread =
		Thread({ runTemplate() }, threadName).also { it.start() }

	// the template — run, report the failure, then settle.
	private fun runTemplate() {
		try {
			doRun()
		} catch (iae: IllegalArgumentException) {
			onParseFail(iae)
		} catch (e: Exception) {
			onLoadFail(e)
		} catch (e: StackOverflowError) {
			// pathologically nested input — a parse failure, not a process crash
			onParseFail(IllegalArgumentException("The file nests too deeply", e))
		}
		onSettled()
	}

	/** Read -> parse -> persist -> refresh/notify. Runs on the worker thread. */
	protected abstract fun doRun()

	/** Runs after the attempt, failure or not — the dir import's root reload. */
	protected open fun onSettled() {}

	protected open fun onParseFail(e: IllegalArgumentException) {
		Startup.onParseFail(e.message ?: "")
	}

	protected open fun onLoadFail(e: Exception) {
		Startup.onLoadFail(e, failureName())
	}

	protected abstract fun failureName(): String

	protected fun post(work: () -> Unit) = postToUi(Runnable { work() })

	// Reads the whole picked document — a failure already went out, skip then.
	protected fun readContent(uri: Uri, progress: FileProgress): String? =
		FileText.readText(uri, progress)

	// Persists the parsed stream under [anchorId] with the bar's row phase.
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
 * A word list landing in the open container — the parsed stream is saved
 * under it, then the open content is re-read from the database.
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
		// the resolver holds the pick-time snapshot — never `null` for a
		// container target. The root anchor below is the defensive default
		val self = openContainer()
		val anchor = self?.id ?: ROOT_ID
		FileGateway.importProgress(appContext, R.string.progress_import, uri).use { progress ->
			val content = readContent(uri, progress) ?: return
			// wrapped — the save loop drives the bar's row phase
			val outcome = persist(WordListImporter(self?.name ?: FileGateway.containerNameFrom(fileName), content), progress, anchor)
			post { if (self == null && atRoot()) onRootReload() else onRefreshed() }
			notifyImported(outcome)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * A word list picked at the root — the file's content lands as it is, with
 * no wrapper of the file's name. A saved container's own wrapper chapter
 * becomes a subject again and bare words land as the root's rows.
 *
 * The file's name only labels the parse errors (see [FileGateway.containerNameFrom]).
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
 * A tree JSON (HJSON) import — the codec parses the text into chapters,
 * the repository persists the stream in one transaction.
 * - the pick-time open container anchors the top-level chapters.
 * - none picked — they become subjects of the root.
 * - subject-level reload — the open content re-reads otherwise.
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
			// no open container is the root — the file's chapters become subjects there
			val anchor = openContainer()?.id ?: ROOT_ID
			val result = persist(HJsonImporter(content), progress, anchor)
			post { if (atRoot()) onRootReload() else onCurrentReload() }
			notifyImported(result)
		}
	}

	override fun failureName(): String = uri.toString()
}

/**
 * A subject-directory import from files — a one-time conversion with no
 * link back to the picked folder.
 * - every file parses before anything is written.
 * - a non-subject directory reports the load failure, leaving nothing behind.
 * - the root reload runs past validation, even on a failed import.
 * - never on a rejected pick.
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
			// the gateway reads the directory's files as pure text, the bytes
			// reporting to the bar exactly as the loader's own reads did
			// An unreadable file already reported itself through FileText —
			// returning quietly here keeps the one real failure report
			val subject = FileGateway.loadLegacySubject(dir, progress) ?: return
			val result = persist(LegacySubjectImporter(subject), progress)
			notifyImported(result)
		}
	}

	override fun onSettled() {
		if (attempted && atRoot()) post { onRootReload() }
	}

	// the directory worker reports every failure as a load failure
	override fun onParseFail(e: IllegalArgumentException) = onLoadFail(e)

	override fun failureName(): String =
		// the fallback re-queries the SAF — the first query may be exactly
		// what failed, so this runs guarded or the failure handler itself
		// would crash the process
		dirName ?: runCatching { DocumentFile.fromTreeUri(appContext, uri)?.name }.getOrNull()
			?: uri.toString()
}

/**
 * The one shared export path — the codec is the injected [ExportFormat]
 * strategy, so the word-list and tree-JSON target workers differ only in
 * the format they hand over.
 * - the containers are the caller's pick-time snapshot.
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
		// the callback touches UI state (the selection clear) — it runs on
		// the main thread like every other follow-up
		post { onExportStart() }
		val request = ExportRequest(uri, containers, format)
		val name = FileGateway.displayName(appContext, request.uri)
		FileGateway.titledProgress(appContext, R.string.progress_export, name).use { progress ->
			// wrapped — the walk drives the bar's row phase
			val text = ItemRepository.export(progress.exporter(format.exporter()), request.containers)
			val (words, chapters) = ItemRepository.subgraphStats(request.containers.map { it.id })
			FileGateway.writeExport(progress, request.uri, name, text, words, chapters)
		}
	}

	// the write's own IO failure already reports through FileText — anything
	// else reaching here is an export failure, not a load
	override fun onLoadFail(e: Exception) {
		Startup.onSaveFail(e, failureName())
	}

	// the writer's own refusals (an unrepresentable name) arrive here as
	// IllegalArgumentException — an export failure, not a parse failure
	override fun onParseFail(e: IllegalArgumentException) {
		Startup.onSaveFail(e, failureName())
	}

	override fun failureName(): String = uri.toString()
}
