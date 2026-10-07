package cz.litoj.schlmgr.app

import android.content.Context
import android.util.Log
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.io.WordListWriter
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.ui.explorer.model.ItemRow
import java.io.File
import java.util.IdentityHashMap

/**
 * App startup and user messaging — the successor of the former engine's
 * `AndroidIOSystem` platform layer. Called once from [cz.litoj.schlmgr.ui.activity.MainActivity].
 */
object Startup {

	@JvmStatic
	fun init(context: Context) {
		GlobalDependencies.appContext = context.applicationContext
		ItemRepository.init(context)
		Thread.setDefaultUncaughtExceptionHandler { t, e -> onUncaught(t, e) }
		applyStoredOptions()
		recordAppVersion()
	}

	/**
	 * Pushes the stored options into the engine's config statics. The
	 * getters' defaults are the first-launch values — nothing writes them
	 * until the user changes a setting.
	 */
	private fun applyStoredOptions() {
		ItemRow.defFlip = AppSettings.getBool("flipWord", true)
		ItemRow.flipAllOnClick = AppSettings.getBool("flipAllOnClick", false)
		ItemRow.parse = AppSettings.getBool("parseNames", true)
		ItemRow.showDesc = AppSettings.getBool("doShowDesc", false)
		TestEngine.amount = AppSettings.getInt("testAmount", 10)
		TestEngine.setDefaultTime(AppSettings.getInt("defaultTestTime", 18))
		TestEngine.isClever = AppSettings.getBool("isClever", true)
		TestEngine.isReversed = AppSettings.getBool("reverseTest", false)
		WordListWriter.setWordSplitter(AppSettings.getString("exportWordSplit", ";"))
	}

	/**
	 * The compatibility checker: tracks the app version the user last ran, so
	 * a future format change can hang its migration on the comparison. The
	 * package rename cut off every earlier lineage — every install of this
	 * package starts fresh — so recording the current version is the only
	 * check that remains.
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
	 * The stored report's hard cap: a StackOverflowError (a recursive back
	 * dispatch once did this) fills tens of thousands of frames, and the
	 * multi-megabyte report then broke everything that moved it through binder
	 * (the clipboard copy in the report popup). A few hundred KB keeps the
	 * report readable and under the ~1 MB binder limit.
	 */
	private const val MAX_REPORT_CHARS = 256 * 1024

	/**
	 * The process-wide uncaught-exception sink. It must never throw and must
	 * persist the report synchronously: the prefs write uses `commit()` (not
	 * `apply()`) and finishes before the process dies, so the next launch
	 * reliably finds it and shows the [CrashReportPopup]. The legacy version
	 * read the (possibly dead) activity, posted a toast it then killed with
	 * `System.exit(0)` and could lose the async write — this one touches only
	 * the application context and the synchronous store.
	 */
	private fun onUncaught(t: Thread, e: Throwable) {
		try {
			// the trace's head is where the error started — the tail (a deep
			// recursion repeating itself) is what gets cut by the cap
			val fullMsg = boundReport("$t\n${Log.getStackTraceString(e)}")
			val report = GlobalDependencies.appContext.getString(R.string.exception_handler) +
				(getFirstCause(e) ?: e.toString()) + "\n\n$fullMsg"
			GlobalDependencies.appContext.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
				.putString("uncaughtException", report)
				.commit()
			if (isDebugBuild()) Log.e("Unexpected failure", fullMsg)
		} catch (_: Throwable) {
			// the handler itself must stay inert — the kill below still ends the process
		} finally {
			// killProcess (not exit(0) alone): a plain clean-exit code can leave the
			// activity record half-launched, which showed as a dead white screen
			android.os.Process.killProcess(android.os.Process.myPid())
			System.exit(10)
		}
	}

	/** Keeps the report under [MAX_REPORT_CHARS], cutting the trace's tail. */
	@JvmStatic
	fun boundReport(msg: String): String =
		if (msg.length <= MAX_REPORT_CHARS) msg
		else msg.substring(0, MAX_REPORT_CHARS) + "\n[truncated]"

	/**
	 * @return the crash report of an unexpected failure from a previous session,
	 * or `null` when there is none
	 */
	@JvmStatic
	fun getCrashReport(): String? =
		GlobalDependencies.appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
			.getString("uncaughtException", null)

	@JvmStatic
	fun getFirstCause(e: Throwable): String? {
		// a cause chain can be cyclic (an exception wrapping one of its
		// ancestors) — the seen-set ends the walk instead of looping forever
		val seen = IdentityHashMap<Throwable, Unit>()
		var t = e
		while (seen.put(t, Unit) == null) {
			t = t.cause ?: return t.message
		}
		return t.message
	}

	// ------------------------------------------------------------ messaging

	/**
	 * Reports one transient user message: a snackbar with a full-text popup
	 * action — the former `defaultReacts` messaging surface. Safe to call
	 * from any thread; whichever activity is started shows it.
	 */
	@JvmStatic
	fun showMsg(msg: String, fullMsg: String) {
		GlobalDependencies.messages.tryEmit(GlobalDependencies.UiMessage(msg, fullMsg))
	}

	/** Word-list parse failure — the former `SimpleReader:fail` reaction. */
	@JvmStatic
	fun onParseFail(msg: String) = showMsg(msg, msg)

	/** Word-list import result — the former `SimpleReader:success` reaction. */
	@JvmStatic
	fun onImportSuccess(counts: IntArray) {
		val ctx = GlobalDependencies.appContext
		val msg = ctx.getString(R.string.loaded) +
			'\n' + ctx.getString(R.string.loaded_chaps) + ": " + counts[0] +
			'\n' + ctx.getString(R.string.help_create_word) + ": " + counts[1] +
			'\n' + ctx.getString(R.string.data_translations) + ": " + counts[2]
		showMsg(msg, msg)
	}

	/** Word-list export result — the former `SimpleWriter:success` reaction. */
	@JvmStatic
	fun onExportSuccess(path: String) {
		val msg = visibleInternalPath(path) +
			GlobalDependencies.appContext.getString(R.string.action_sw_success)
		showMsg(msg, msg)
	}

	/** Legacy file read failure — the former `ContainerFile:load` reaction. */
	@JvmStatic
	fun onLoadFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = ctx.getString(R.string.fail_load) + '\n' + name + '\n' +
			ctx.getString(R.string.fail_load_src) + name +
			ctx.getString(R.string.fail_type) + "file:\n"
		showMsg(
			msg + getFirstCause(e),
			msg + e.message + '\n' + Log.getStackTraceString(e)
		)
	}

	/** Legacy file write failure — the former `ContainerFile:save` reaction. */
	@JvmStatic
	fun onSaveFail(e: Exception, name: String) {
		val ctx = GlobalDependencies.appContext
		val msg = ctx.getString(R.string.fail_save) + '\n' + name + '\n' +
			ctx.getString(R.string.fail_save_src) + name +
			ctx.getString(R.string.fail_type) + "file:\n"
		showMsg(
			msg + getFirstCause(e),
			msg + e.message + '\n' + Log.getStackTraceString(e)
		)
	}

	// ------------------------------------------------------------ helpers

	/** The default legacy subjects directory: `<external files>/School objects`. */
	@JvmStatic
	val defaultSubjectsDir: File
		get() = File(GlobalDependencies.appContext.getExternalFilesDir(null), "School objects")

	@JvmStatic
	fun visibleInternalPath(path: String): String {
		val defDir = defaultSubjectsDir.absolutePath
		return if (!path.contains(defDir)) path else path.substring(defDir.length)
	}
}
