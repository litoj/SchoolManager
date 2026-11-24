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
import kotlinx.coroutines.flow.MutableSharedFlow

/** The transient user messages of one application, safe to report from any thread. */
class UserMessages(private val appContext: Context) {

	/** Extra buffer capacity keeps reports non-blocking from worker threads. */
	val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)

	/** The reporters name what happened, while the display owns every notice string. */
	sealed interface UiMessage {
		/**
		 * A message with resolved text.
		 * - [text] is the snackbar line and the popup body.
		 * - [fullText] is the Copy payload.
		 * - a null [actionLabel] uses the default label.
		 */
		data class Text(val text: String, val fullText: String, val actionLabel: String? = null) : UiMessage

		/** A notice that the display resolves, see [UiNotice]. */
		data class Notice(val notice: UiNotice) : UiMessage

		/** An export result with a share action on the saved document. */
		data class ShareFile(val text: String, val uri: Uri, val mime: String) : UiMessage

		/** The share sheet for [uri] at once, the export the user asked to share. */
		data class SendFile(val uri: Uri, val mime: String) : UiMessage

		/** A notice with an undo action, where a newer one replaces it. */
		data class Undoable(val notice: UiNotice, val onUndo: () -> Unit) : UiMessage
	}

	/**
	 * Reports one transient user message, see [UiMessage.Text].
	 * Safe from any thread.
	 */
	fun showMsg(msg: String, fullMsg: String, actionLabel: String? = null) {
		messages.tryEmit(UiMessage.Text(msg, fullMsg, actionLabel))
	}

	/** Reports one transient user notice by meaning, and the display picks the text. */
	fun showNotice(notice: UiNotice) {
		messages.tryEmit(UiMessage.Notice(notice))
	}

	/** Reports one transient user notice with an undo, see [UiMessage.Undoable]. */
	fun showUndoable(notice: UiNotice, onUndo: () -> Unit) {
		messages.tryEmit(UiMessage.Undoable(notice, onUndo))
	}

	/** The sheet of a shared export opens at once, the copy already holds the text. */
	fun onShareReady(uri: Uri, mime: String) =
		messages.tryEmit(UiMessage.SendFile(uri, mime))

	/**
	 * Word-list parse failure with its own actionable file context.
	 * - [file] and [source] with a [retry] owner open the editor on the failing spot, and a
	 *   saved fix runs [retry] on the same document.
	 * - a failure of no reachable document stays a plain report of its source line.
	 */
	fun onWordListParseFail(
		error: WordListParseException,
		file: Uri?,
		source: String?,
		retry: ((Uri) -> Unit)?,
	): DialogSpec {
		val content = ImportFailureExplanation.of(error)
		val title = appContext.getString(R.string.import_error_title)
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
	fun onParseFail(msg: String) = showMsg(msg, msg)

	fun onImportSuccess(words: Int, chapters: Int, fixes: List<WordListAutoFix> = emptyList()) {
		val msg = stats(R.string.stat_imported, words, chapters) + autoFixSummary(fixes)
		showMsg(msg, msg + autoFixDetails(fixes))
	}

	/** The count of the self-repaired lines, nothing when the file needed no repair. */
	private fun autoFixSummary(fixes: List<WordListAutoFix>): String {
		if (fixes.isEmpty()) return ""
		return "\n" + appContext.resources
			.getQuantityString(R.plurals.import_fixed_lines, fixes.size, fixes.size)
	}

	/** Every self-repaired line as it stood and as it landed. */
	private fun autoFixDetails(fixes: List<WordListAutoFix>): String {
		if (fixes.isEmpty()) return ""
		val ctx = appContext
		return "\n" + fixes.joinToString("\n") {
			ctx.getString(R.string.import_fixed_line, it.lineNumber, it.before, it.after)
		}
	}

	/**
	 * Export result: the word and chapter statistics of what was written, and the display
	 * name of the picked target where the file landed.
	 */
	fun onExportSuccess(uri: Uri, path: String, words: Int, chapters: Int, mime: String) {
		val msg = stats(R.string.stat_exported, words, chapters) + '\n' +
			path + appContext.getString(R.string.action_sw_success)
		messages.tryEmit(UiMessage.ShareFile(msg, uri, mime))
	}

	/** The "Imported/Exported X words in Y chapters." sentence of both statistics. */
	private fun stats(verbRes: Int, words: Int, chapters: Int): String {
		val ctx = appContext
		val res = ctx.resources
		return ctx.getString(verbRes,
			res.getQuantityString(R.plurals.stat_words, words, words),
			res.getQuantityString(R.plurals.stat_chapters, chapters, chapters))
	}

	/** File read failure, and the caller skips the failing document. */
	fun onLoadFail(e: Exception, name: String) {
		val ctx = appContext
		val msg = failMessage(ctx, name, R.string.fail_load, R.string.fail_load_src)
		showFail(e, msg)
	}

	/** File write failure, and the caller skips its success report. */
	fun onSaveFail(e: Exception, name: String) {
		val ctx = appContext
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
