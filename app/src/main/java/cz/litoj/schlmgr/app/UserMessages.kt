package cz.litoj.schlmgr.app

import android.content.Context
import android.net.Uri
import android.util.Log
import cz.litoj.schlmgr.data.wordlist.WordListAutoFix
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.MessagePopup
import cz.litoj.schlmgr.ui.popup.MessageSource
import cz.litoj.schlmgr.ui.popup.WordListFixPopup

/** The transient user messages of the app, safe to report from any thread. */
object UserMessages {

	/**
	 * Reports one transient user message, see [GlobalDependencies.UiMessage.Text].
	 * Safe from any thread.
	 */
	@JvmStatic
	@JvmOverloads
	fun showMsg(msg: String, fullMsg: String, actionLabel: String? = null) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Text(msg, fullMsg, actionLabel))
	}

	/** Reports one transient user notice by meaning, and the display picks the text. */
	@JvmStatic
	fun showNotice(notice: UiNotice) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Notice(notice))
	}

	/** Reports one transient user notice with an undo, see [GlobalDependencies.UiMessage.Undoable]. */
	@JvmStatic
	fun showUndoable(notice: UiNotice, onUndo: () -> Unit) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Undoable(notice, onUndo))
	}

	/**
	 * Word-list parse failure with its own actionable file context.
	 * - [file] and [source] with a [retry] owner open the editor on the failing spot, and a
	 *   saved fix runs [retry] on the same document.
	 * - a failure of no reachable document stays a plain report of its source line.
	 */
	@JvmStatic
	fun onWordListParseFail(
		error: WordListParseException,
		file: Uri?,
		source: String?,
		retry: ((Uri) -> Unit)?,
	): DialogSpec {
		val content = ImportFailureExplanation.of(error)
		val title = GlobalDependencies.appContext.getString(R.string.import_error_title)
		if (file == null || source == null || retry == null) return MessagePopup(
			msg = content.guidance,
			copyText = content.text,
			title = title,
			source = MessageSource(content.location, content.source, error.problemIndex),
		)
		return WordListFixPopup(
			title = title,
			report = content,
			file = file,
			source = source,
			caret = ImportFailureExplanation.caretIn(source, error),
			onReimport = retry,
		)
	}

	/** Word-list parse failure with [msg] as both snackbar line and popup body. */
	@JvmStatic
	fun onParseFail(msg: String) = showMsg(msg, msg)

	@JvmStatic
	@JvmOverloads
	fun onImportSuccess(words: Int, chapters: Int, fixes: List<WordListAutoFix> = emptyList()) {
		val msg = stats(R.string.stat_imported, words, chapters) + autoFixSummary(fixes)
		showMsg(msg, msg + autoFixDetails(fixes))
	}

	/** The count of the self-repaired lines, nothing when the file needed no repair. */
	private fun autoFixSummary(fixes: List<WordListAutoFix>): String {
		if (fixes.isEmpty()) return ""
		return "\n" + GlobalDependencies.appContext.resources
			.getQuantityString(R.plurals.import_fixed_lines, fixes.size, fixes.size)
	}

	/** Every self-repaired line as it stood and as it landed. */
	private fun autoFixDetails(fixes: List<WordListAutoFix>): String {
		if (fixes.isEmpty()) return ""
		val ctx = GlobalDependencies.appContext
		return "\n" + fixes.joinToString("\n") {
			ctx.getString(R.string.import_fixed_line, it.lineNumber, it.before, it.after)
		}
	}

	/**
	 * Export result: the word and chapter statistics of what was written, and the display
	 * name of the picked target where the file landed.
	 */
	@JvmStatic
	fun onExportSuccess(uri: Uri, path: String, words: Int, chapters: Int, mime: String) {
		val msg = stats(R.string.stat_exported, words, chapters) + '\n' +
			path + GlobalDependencies.appContext.getString(R.string.action_sw_success)
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.ShareFile(msg, uri, mime))
	}

	/** The "Imported/Exported X words in Y chapters." sentence of both statistics. */
	private fun stats(verbRes: Int, words: Int, chapters: Int): String {
		val ctx = GlobalDependencies.appContext
		val res = ctx.resources
		return ctx.getString(verbRes,
			res.getQuantityString(R.plurals.stat_words, words, words),
			res.getQuantityString(R.plurals.stat_chapters, chapters, chapters))
	}

	/** File read failure, and the caller skips the failing document. */
	@JvmStatic
	fun onLoadFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = failMessage(ctx, name, R.string.fail_load, R.string.fail_load_src)
		showFail(e, msg)
	}

	/** File write failure, and the caller skips its success report. */
	@JvmStatic
	fun onSaveFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = failMessage(ctx, name, R.string.fail_save, R.string.fail_save_src)
		showFail(e, msg)
	}

	/** The two-line failure banner: what failed, then which file and why. */
	private fun failMessage(ctx: Context, name: String, head: Int, src: Int): String =
		ctx.getString(head) + '\n' + name + '\n' +
			ctx.getString(src) + name +
			ctx.getString(R.string.fail_type) + ctx.getString(R.string.fail_kind_file) + '\n'

	private fun showFail(e: Exception, msg: String) {
		showMsg(
			msg + (Throwables.getFirstCause(e) ?: e.toString()),
			msg + (e.message ?: "") + '\n' + Log.getStackTraceString(e)
		)
	}
}
