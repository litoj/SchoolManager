package cz.litoj.schlmgr.app

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import cz.litoj.schlmgr.testing.TestEngine
import java.util.IdentityHashMap

/**
 * App startup and user messaging — called from
 * [cz.litoj.schlmgr.ui.activity.MainActivity] each time it is created.
 */
object Startup {

	@JvmStatic
	fun init(context: Context) {
		// every activity creation runs this — only the first builds the
		// process-wide state (a rotation must not leak another database)
		if (ItemRepository.initialized) return
		GlobalDependencies.appContext = context.applicationContext
		// the app layer owns the database build — the repository stays a
		// pure data gateway with no Android context
		ItemRepository.init(ItemDatabase.newInstance(context.applicationContext))
		// before any read — the invisible root every subject hangs under
		ItemRepository.ensureRoot()
		installCrashHandler()
		migrateStoredSettings()
		applyStoredOptions()
		recordAppVersion()
	}

	/**
	 * Installs the process-wide crash sink (see [onUncaught]) without calling
	 * the platform handler. This one owns report and exit, so delegating would
	 * double-report and double-kill.
	 */
	@SuppressLint("DefaultUncaughtExceptionDelegation")
	private fun installCrashHandler() {
		Thread.setDefaultUncaughtExceptionHandler { t, e -> onUncaught(t, e) }
	}

	/**
	 * The compatibility checker — converts a stored format left by an older
	 * version. Runs before the options apply, once per old key: the conversion
	 * removes it.
	 * - the description switch became a line limit: `on` converts to 5 lines, `off` to 0.
	 */
	@VisibleForTesting
	internal fun migrateStoredSettings() {
		if (!AppSettings.has("doShowDesc")) return
		AppSettings.set("descLines", if (AppSettings.getBool("doShowDesc", true)) DescLinesOn else 0)
		AppSettings.remove("doShowDesc")
	}

	/** The line limit the old description switch's `on` state converts to. */
	private const val DescLinesOn = 5

	/**
	 * Pushes the stored options into the engine's config statics. The
	 * getters' defaults are the first-launch values — nothing writes them
	 * until the user changes a setting.
	 */
	private fun applyStoredOptions() {
		TestEngine.amount = AppSettings.getInt("testAmount", 10)
		TestEngine.setDefaultTime(AppSettings.getInt("defaultTestTime", TestEngine.DEFAULT_TIME_SECONDS))
		TestEngine.isClever = AppSettings.getBool("isClever", true)
		TestEngine.isReversed = AppSettings.getBool("reverseTest", false)
		WordListWriter.setWordTranslationSplitter(AppSettings.getString("exportWordSplit", "="))
	}

	/**
	 * The compatibility checker — tracks the last-run app version, so a future
	 * format change can hang its migration on the comparison.
	 * - recording the current version is the only check.
	 */
	private fun recordAppVersion() {
		val stored = AppSettings.getInt("version", 0)
		if (stored < appVersionCode()) AppSettings.set("version", appVersionCode())
	}

	private fun appVersionCode(): Int = try {
		GlobalDependencies.appContext.packageManager
			.getPackageInfo(GlobalDependencies.appContext.packageName, 0).longVersionCode.toInt()
	} catch (e: android.content.pm.PackageManager.NameNotFoundException) {
		throw IllegalStateException("Own package not found", e)
	}

	private fun isDebugBuild(): Boolean =
		(GlobalDependencies.appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

	// ------------------------------------------------------------ crash handling

	/**
	 * The stored report's hard cap — a StackOverflowError fills tens of
	 * thousands of frames, and the multi-megabyte report then breaks
	 * everything moving it through binder (e.g. the popup's clipboard copy).
	 * A few hundred KB keeps the report readable and under the ~1 MB limit.
	 */
	private const val MAX_REPORT_CHARS = 256 * 1024

	/**
	 * The process-wide uncaught-exception sink. Must never throw and must
	 * persist synchronously (`commit()`, not `apply()`), so the next launch
	 * reliably finds the report and shows it.
	 */
	private fun onUncaught(t: Thread, e: Throwable) {
		try {
			// the trace's head is where the error started — the cap cuts only the tail.
			// Building the trace can itself fail — the message alone is still a report
			val fullMsg = runCatching { boundReport("$t\n${Log.getStackTraceString(e)}") }
				.getOrElse { "${e.toString()}\n[the trace was lost: ${it.message}]" }
			val report = GlobalDependencies.appContext.getString(R.string.exception_handler) +
				(getFirstCause(e) ?: e.toString()) + "\n\n$fullMsg"
			AppSettings.setNow("uncaughtException", report)
			if (isDebugBuild()) Log.e("Unexpected failure", fullMsg)
		} catch (_: Throwable) {
			// the handler itself must stay inert — the kill below still ends the process
		} finally {
			// killProcess (not exit(0) alone) — a plain clean-exit code can leave the
			// activity record half-launched, which showed as a dead white screen
			android.os.Process.killProcess(android.os.Process.myPid())
			System.exit(10)
		}
	}

	/** Keeps the report under the cap, cutting the trace's tail. */
	@JvmStatic
	fun boundReport(msg: String): String =
		if (msg.length <= MAX_REPORT_CHARS) msg
		else msg.substring(0, MAX_REPORT_CHARS) + "\n[truncated]"

	/** Stored crash report from the previous run, or `null` when there is none. */
	@JvmStatic
	fun getCrashReport(): String? =
		AppSettings.getString("uncaughtException")

	/** Message of the chain's innermost cause, or `null` when it has none. */
	@JvmStatic
	fun getFirstCause(e: Throwable): String? {
		// a cyclic chain ends at the deepest distinct node — the seen-set
		// stops the walk where it starts repeating
		val seen = IdentityHashMap<Throwable, Unit>()
		var deepest = e
		var t = e
		while (seen.put(t, Unit) == null) {
			deepest = t
			t = t.cause ?: return deepest.message
		}
		return deepest.message
	}

	// ------------------------------------------------------------ messaging

	/** Reports one transient user message (see [GlobalDependencies.UiMessage.Text]). Safe from any thread. */
	@JvmStatic
	@JvmOverloads
	fun showMsg(msg: String, fullMsg: String, actionLabel: String? = null) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Text(msg, fullMsg, actionLabel))
	}

	/** Reports one transient user notice by meaning — the display picks the text. */
	@JvmStatic
	fun showNotice(notice: UiNotice) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage.Notice(notice))
	}

	/** Word-list parse failure — [msg] is both snackbar line and popup body. */
	@JvmStatic
	fun onParseFail(msg: String) = showMsg(msg, msg)

	/**
	 * Import result — the word and chapter statistics of what landed in the
	 * database.
	 */
	@JvmStatic
	fun onImportSuccess(words: Int, chapters: Int) {
		val msg = stats(R.string.stat_imported, words, chapters)
		showMsg(msg, msg)
	}

	/**
	 * Export result — the word and chapter statistics of what was written, and
	 * where the file landed (the picked target's display name).
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

	/** File read failure — the caller skips the failing document. */
	@JvmStatic
	fun onLoadFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = failMessage(ctx, name, R.string.fail_load, R.string.fail_load_src)
		showFail(e, msg)
	}

	/** File write failure — the caller skips its success report. */
	@JvmStatic
	fun onSaveFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = failMessage(ctx, name, R.string.fail_save, R.string.fail_save_src)
		showFail(e, msg)
	}

	/** The two-line failure banner — what failed, then which file and why. */
	private fun failMessage(ctx: Context, name: String, head: Int, src: Int): String =
		ctx.getString(head) + '\n' + name + '\n' +
			ctx.getString(src) + name +
			ctx.getString(R.string.fail_type) + ctx.getString(R.string.fail_kind_file) + '\n'

	private fun showFail(e: Exception, msg: String) {
		showMsg(
			msg + (getFirstCause(e) ?: e.toString()),
			msg + (e.message ?: "") + '\n' + Log.getStackTraceString(e)
		)
	}

}
