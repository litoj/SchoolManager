package cz.litoj.schlmgr.app

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.wordlist.WordListAutoFix
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListWriter
import cz.litoj.schlmgr.testing.TestEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * App startup and the file outcome reports of the transfer layer, called by
 * every activity entry before its first composition.
 */
object Startup {

	lateinit var dependencies: AppDependencies
		private set

	@JvmStatic
	fun init(context: Context): AppDependencies {
		// every activity creation runs this, and only the first builds the
		// process-wide state. A rotation must not leak another database
		if (::dependencies.isInitialized) return dependencies
		val database = ItemDatabase.newInstance(context.applicationContext)
		dependencies = AppDependencies(context.applicationContext, database)
		GlobalDependencies.appContext = dependencies.appContext
		// activity startup is synchronous, and the first read needs the invisible root
		runBlocking(Dispatchers.IO) { dependencies.graphQueries.ensureRoot() }
		CrashReporter.install(dependencies.settings)
		migrateStoredSettings(dependencies.settings)
		applyStoredOptions(dependencies.settings)
		recordAppVersion(dependencies.settings)
		return dependencies
	}

	/**
	 * The compatibility checker, converting a stored format left by an older version.
	 * - it runs before the options apply, once per old key.
	 * - each conversion removes its key, so it never runs twice.
	 */
	@VisibleForTesting
	internal fun migrateStoredSettings(settings: Settings) {
		migrateDescriptionSwitch(settings)
		migrateSecondsPerChar(settings)
	}

	/** The description switch became a line limit: on converts to 5 lines, off to 0. */
	private fun migrateDescriptionSwitch(settings: Settings) {
		if (!settings.has("doShowDesc")) return
		settings.set("descLines", if (settings.getBool("doShowDesc", true)) DescLinesOn else 0)
		settings.remove("doShowDesc")
	}

	/**
	 * The seconds per word became the seconds per answer character.
	 * - the value keeps its ratio to the old default of 18, so a changed setting stays changed.
	 * - the result keeps one decimal, the precision of the field.
	 */
	private fun migrateSecondsPerChar(settings: Settings) {
		if (!settings.has("defaultTestTime")) return
		val written = settings.getInt("defaultTestTime", OldWordSeconds)
		val scaled = written * TestEngine.DEFAULT_SECONDS_PER_CHAR / OldWordSeconds
		settings.set("testSecondsPerChar", (scaled * 10).roundToInt() / 10f)
		settings.remove("defaultTestTime")
	}

	private const val DescLinesOn = 5
	private const val OldWordSeconds = 18

	/**
	 * Pushes the stored options into the config statics of the engine.
	 * - the defaults of the getters are the first-launch values.
	 * - nothing writes them until the user changes a setting.
	 */
	private fun applyStoredOptions(settings: Settings) {
		TestEngine.applyOptions(
			settings.getBool("isClever", true),
			settings.getInt("testAmount", 10))
		TestEngine.setSecondsPerChar(
			settings.getFloat("testSecondsPerChar", TestEngine.DEFAULT_SECONDS_PER_CHAR))
		TestEngine.applyReversed(settings.getBool("reverseTest", false))
		TestEngine.applyRequireAllVariants(settings.getBool("testRequireAllVariants", false))
		TestEngine.applyIgnoredMistakes(settings.getString("testIgnoredMistakes", ""))
		WordListWriter.setWordTranslationSplitter(settings.getString("exportWordSplit", "="))
	}

	/**
	 * The compatibility checker, tracking the last-run app version.
	 * - a future format change can hang its migration on the comparison.
	 * - recording the current version is the only check.
	 */
	private fun recordAppVersion(settings: Settings) {
		val stored = settings.getInt("version", 0)
		if (stored < appVersionCode()) settings.set("version", appVersionCode())
	}

	private fun appVersionCode(): Int = try {
		dependencies.appContext.packageManager
			.getPackageInfo(dependencies.appContext.packageName, 0).longVersionCode.toInt()
	} catch (e: android.content.pm.PackageManager.NameNotFoundException) {
		throw IllegalStateException("Own package not found", e)
	}

	// the transfer layer files are work in progress and still call the file
	// outcome reports here, so each forwards to the application message owner

	/** The cache copy of a share, handed to the sheet the user asked for. */
	@JvmStatic
	fun onShareReady(uri: Uri, mime: String) = dependencies.userMessages.onShareReady(uri, mime)

	/** Word-list parse failure with the picked [file] edited in place, fixed text re-imported. */
	@JvmStatic
	fun onWordListParseFail(
		error: WordListParseException,
		file: Uri?,
		source: String?,
		retry: ((Uri) -> Unit)?,
	) = dependencies.userMessages.onWordListParseFail(error, file, source, retry)

	/** Word-list parse failure with [msg] as both snackbar line and popup body. */
	@JvmStatic
	fun onParseFail(msg: String) = dependencies.userMessages.onParseFail(msg)

	@JvmStatic
	@JvmOverloads
	fun onImportSuccess(words: Int, chapters: Int, fixes: List<WordListAutoFix> = emptyList()) =
		dependencies.userMessages.onImportSuccess(words, chapters, fixes)

	/**
	 * Export result: the word and chapter statistics of what was written, and the display
	 * name of the picked target where the file landed.
	 */
	@JvmStatic
	fun onExportSuccess(uri: Uri, path: String, words: Int, chapters: Int, mime: String) =
		dependencies.userMessages.onExportSuccess(uri, path, words, chapters, mime)

	/** File read failure, and the caller skips the failing document. */
	@JvmStatic
	fun onLoadFail(e: Exception, name: String) = dependencies.userMessages.onLoadFail(e, name)

	/** File write failure, and the caller skips its success report. */
	@JvmStatic
	fun onSaveFail(e: Exception, name: String) = dependencies.userMessages.onSaveFail(e, name)

}
