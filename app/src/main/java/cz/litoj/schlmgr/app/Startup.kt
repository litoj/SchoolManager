package cz.litoj.schlmgr.app

import android.content.Context
import androidx.annotation.VisibleForTesting
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import cz.litoj.schlmgr.testing.TestEngine

/**
 * App startup and the file outcome reports of the transfer layer, called from
 * [cz.litoj.schlmgr.ui.activity.MainActivity] each time it is created.
 */
object Startup {

	@JvmStatic
	fun init(context: Context) {
		// every activity creation runs this, and only the first builds the
		// process-wide state. A rotation must not leak another database
		if (ItemRepository.initialized) return
		GlobalDependencies.appContext = context.applicationContext
		// the app layer owns the database build, and the repository stays a
		// pure data gateway with no Android context
		ItemRepository.init(ItemDatabase.newInstance(context.applicationContext))
		// before any read, since every subject hangs under the invisible root
		ItemRepository.ensureRoot()
		CrashReporter.install()
		migrateStoredSettings()
		applyStoredOptions()
		recordAppVersion()
	}

	/**
	 * The compatibility checker, converting a stored format left by an older version.
	 * - it runs before the options apply, once per old key.
	 * - the conversion removes the key.
	 * - the description switch became a line limit: on converts to 5 lines, off to 0.
	 */
	@VisibleForTesting
	internal fun migrateStoredSettings() {
		if (!AppSettings.has("doShowDesc")) return
		AppSettings.set("descLines", if (AppSettings.getBool("doShowDesc", true)) DescLinesOn else 0)
		AppSettings.remove("doShowDesc")
	}

	private const val DescLinesOn = 5

	/**
	 * Pushes the stored options into the config statics of the engine.
	 * - the defaults of the getters are the first-launch values.
	 * - nothing writes them until the user changes a setting.
	 */
	private fun applyStoredOptions() {
		TestEngine.amount = AppSettings.getInt("testAmount", 10)
		TestEngine.setDefaultTime(AppSettings.getInt("defaultTestTime", TestEngine.DEFAULT_TIME_SECONDS))
		TestEngine.isClever = AppSettings.getBool("isClever", true)
		TestEngine.isReversed = AppSettings.getBool("reverseTest", false)
		WordListWriter.setWordTranslationSplitter(AppSettings.getString("exportWordSplit", "="))
	}

	/**
	 * The compatibility checker, tracking the last-run app version.
	 * - a future format change can hang its migration on the comparison.
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

	// the transfer layer files are work in progress and still call the file
	// outcome reports here, so each forwards to [UserMessages] until that
	// layer moves

	/** Word-list parse failure with [msg] as both snackbar line and popup body. */
	@JvmStatic
	fun onParseFail(msg: String) = UserMessages.onParseFail(msg)

	@JvmStatic
	fun onImportSuccess(words: Int, chapters: Int) = UserMessages.onImportSuccess(words, chapters)

	/**
	 * Export result: the word and chapter statistics of what was written, and the display
	 * name of the picked target where the file landed.
	 */
	@JvmStatic
	fun onExportSuccess(path: String, words: Int, chapters: Int) =
		UserMessages.onExportSuccess(path, words, chapters)

	/** File read failure, and the caller skips the failing document. */
	@JvmStatic
	fun onLoadFail(e: Exception, name: String) = UserMessages.onLoadFail(e, name)

	/** File write failure, and the caller skips its success report. */
	@JvmStatic
	fun onSaveFail(e: Exception, name: String) = UserMessages.onSaveFail(e, name)

}
