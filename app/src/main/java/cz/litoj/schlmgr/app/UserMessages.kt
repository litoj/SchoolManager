package cz.litoj.schlmgr.app

import android.content.Context
import android.util.Log
import cz.litoj.schlmgr.R

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

	/** Word-list parse failure with [msg] as both snackbar line and popup body. */
	@JvmStatic
	fun onParseFail(msg: String) = showMsg(msg, msg)

	@JvmStatic
	fun onImportSuccess(words: Int, chapters: Int) {
		val msg = stats(R.string.stat_imported, words, chapters)
		showMsg(msg, msg)
	}

	/**
	 * Export result: the word and chapter statistics of what was written, and the display
	 * name of the picked target where the file landed.
	 */
	@JvmStatic
	fun onExportSuccess(path: String, words: Int, chapters: Int) {
		val msg = stats(R.string.stat_exported, words, chapters) + '\n' +
			path + GlobalDependencies.appContext.getString(R.string.action_sw_success)
		showMsg(msg, msg)
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
