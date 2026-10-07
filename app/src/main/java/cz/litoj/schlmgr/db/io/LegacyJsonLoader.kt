package cz.litoj.schlmgr.db.io

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.ui.GlobalDependencies
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * One-time migration from the old file-based storage into the Room database,
 * and the app's SAF text I/O (the read/write the imports, exports and the
 * migration share).
 *
 * The old format: each subject is a directory with `main.json`,
 * `setts.dat` and a `Chapters/` folder; chapters marked as
 * "save chapters" were stored as separate `name[hash].json` files that
 * `main.json` referenced as empty shells.
 *
 * Per the approved simplification, this loader **writes the old data
 * directly into the database** — it parses the JSON into plain
 * [LegacyContent] trees and inserts item rows plus parent→child links. It
 * never touches any other model. The old files stay on disk untouched.
 *
 * Types are resolved by the simple class name (the qualifier changed with
 * the packages): `MainChapter`/`SaveChapter`/plain chapter →
 * `chapter`, `Word` → `word` (+ translation children), other
 * known leaves → `note`. Unknown types (references were never persisted
 * as rows) are skipped.
 */
object LegacyJsonLoader {

	/**
	 * Runs the migration if it is needed: the database is empty and old-format
	 * subject directories still exist. Each migrated subject's file tree is
	 * inserted straight into the database.
	 *
	 * @param sources the directories to scan for old-format subjects (the
	 * subjects dir first, then any imported subject dirs)
	 */
	@JvmStatic
	fun migrateIfNeeded(vararg sources: DocumentFile) {
		// Only an empty database gets the one-time conversion — anything
		// already there means the user's data is live.
		if (!ItemRepository.initialized || !ItemRepository.isEmpty()) return
		for (dir in sources) {
			try {
				if (!dir.exists() || !dir.isDirectory || dir.findFile("main.json") == null) continue
				insertSubjectDir(dir)
			} catch (e: Exception) {
				Startup.onLoadFail(e, dir.name ?: dir.uri.toString())
			}
		}
	}

	/**
	 * Parses one old-format subject directory straight into the database.
	 *
	 * @param dir the subject directory (containing `main.json`)
	 * @return the row id of the inserted subject root item, or -1 on failure
	 */
	@JvmStatic
	@Throws(Exception::class)
	fun insertSubjectDir(dir: DocumentFile): Int {
		// findFile, never a creating lookup: a missing file must not be born here
		val mainJson = dir.findFile("main.json") ?: return -1
		// the read reports its own failure — skipping the subject is enough
		if (!hasContent(mainJson)) return -1
		val root = LegacyContent.Reader(readText(mainJson) ?: return -1).mContent

		val subjectName = dir.name ?: return -1
		// container counters come from the words below, never from the file
		val rootId = ItemRepository.legacyInsert(
			subjectName, root.desc(), ItemKind.CHAPTER, 0, 0)
		// the subject is an ordinary child of the invisible root
		ItemRepository.legacyLinkAtEnd(ItemRepository.ROOT_ID, rootId)
		val registry = NameRegistry()
		insertChildren(root, rootId, registry)

		// the preserve-file deep chapters: each becomes a normal chapter row and
		// its saved children land under it
		val chaptersDir = dir.findFile("Chapters")
		if (chaptersDir != null) {
			for (file in chaptersDir.listFiles()) {
				val fileName = file.name ?: continue
				if (!fileName.endsWith(".json")) continue
				val json = chaptersDir.findFile(fileName) ?: continue
				if (!hasContent(json)) continue
				val chapterRoot = LegacyContent.Reader(readText(json) ?: continue).mContent
				val chName = rootChildName(chapterRoot)
				var chId = matchChildId(rootId, chName)
				if (chId == -1)
					chId = ItemRepository.legacyInsert(chName, chapterRoot.desc(), ItemKind.CHAPTER, 0, 0)
				insertChildren(chapterRoot, chId, registry)
				ItemRepository.recomputeSf(chId)
				if (chId > 0) ItemRepository.legacyLinkAtEnd(rootId, chId)
			}
		}
		ItemRepository.recomputeSf(rootId)
		return rootId
	}

	// ---------------------------------------------------------------- document text I/O

	/**
	 * Reads the whole document as UTF-8 text — the file read shared by the
	 * imports and the migration. A failure is reported through
	 * [Startup.onLoadFail] and `null` returned; callers skip the document.
	 */
	@JvmStatic
	fun readText(source: Uri): String? {
		try {
			val stream = GlobalDependencies.appContext.contentResolver.openInputStream(source)
				?: throw IOException("No stream for $source")
			val sb = StringBuilder(4096)
			InputStreamReader(stream, StandardCharsets.UTF_8).use { isr ->
				val buffer = CharArray(1024)
				var amount: Int
				while (isr.read(buffer).also { amount = it } != -1) {
					sb.append(buffer, 0, amount)
				}
			}
			return sb.toString()
		} catch (e: IOException) {
			Startup.onLoadFail(e, source.toString())
			return null
		}
	}

	private fun readText(file: DocumentFile): String? = readText(file.uri)

	/**
	 * Writes text as UTF-8 into the document — the file write shared by the
	 * exports. Returns whether the write succeeded; a failure is reported
	 * through [Startup.onSaveFail], so the caller can skip its success report.
	 */
	@JvmStatic
	@JvmOverloads
	fun writeText(target: Uri, content: String, append: Boolean = false): Boolean {
		try {
			val stream = GlobalDependencies.appContext.contentResolver.openOutputStream(
				target, if (append) "wa" else "wt")
				?: throw IOException("No stream for $target")
			OutputStreamWriter(stream, StandardCharsets.UTF_8).use { osw ->
				osw.write(content)
			}
			return true
		} catch (e: IOException) {
			Startup.onSaveFail(e, target.toString())
			return false
		}
	}

	/** Whether [file] holds anything: a folder, or a non-empty file. */
	private fun hasContent(file: DocumentFile): Boolean =
		file.exists() && (file.isDirectory || file.length() > 0)

	// ---------------------------------------------------------------- helpers

	/**
	 * The rows already inserted for one subject, by name — the old engine's
	 * per-subject name registry. Words and translations keep separate maps, so a
	 * name that exists as both kinds stays two rows.
	 */
	private class NameRegistry {
		val words: MutableMap<String, Int> = HashMap()
		val translations: MutableMap<String, Int> = HashMap()
	}

	private fun insertChildren(parentContent: LegacyContent, parentId: Int,
	                           registry: NameRegistry) {
		var position = ItemRepository.children(parentId).size
		for (child in parentContent.children()) {
			val type = child.typeName() ?: continue
			when (type) {
				"Word" -> {
					// words re-encountered across the walk (the old registry
					// merged them by name) reuse their row instead of a duplicate
					var wordId = registry.words[child.name()]
					if (wordId == null) {
						wordId = ItemRepository.legacyInsert(
							child.name(), child.desc(), ItemKind.WORD,
							child.passed(), child.failed())
						registry.words[child.name()] = wordId
					} else {
						// a repeated word keeps every distinct description
						ItemRepository.appendDescription(wordId, child.desc())
					}
					if (wordId != -1) ItemRepository.legacyLink(parentId, wordId, position++)
					// translations live as rows linked to the word row; a repeated
					// name reuses its row like a word does
					var trlPos = 0
					for (trl in child.children()) {
						var trlId = registry.translations[trl.name()]
						if (trlId == null) {
							trlId = ItemRepository.legacyInsert(
								trl.name(), trl.desc(), ItemKind.TRANSLATION,
								trl.passed(), trl.failed())
							registry.translations[trl.name()] = trlId
						} else ItemRepository.appendDescription(trlId, trl.desc())
						if (trlId != -1 && wordId != -1)
							ItemRepository.legacyLink(wordId, trlId, trlPos++)
					}
				}
				"Chapter", "SaveChapter" -> {
					val chId = ItemRepository.legacyInsert(
						child.name(), child.desc(), ItemKind.CHAPTER, 0, 0)
					if (chId != -1) {
						ItemRepository.legacyLink(parentId, chId, position++)
						insertChildren(child, chId, registry)
						ItemRepository.recomputeSf(chId)
					}
				}
				"Note" -> {
					val noteId = ItemRepository.legacyInsert(
						child.name(), child.desc(), ItemKind.NOTE,
						child.passed(), child.failed())
					if (noteId != -1) ItemRepository.legacyLink(parentId, noteId, position++)
				}
				else -> {
					// references were never persisted as rows — nothing to migrate
				}
			}
		}
	}

	/**
	 * The `setts.dat`/`main.json` name of a deeply-saved chapter
	 * file: the chapter name, or the file's own display name when the root
	 * isn't referencing one.
	 */
	private fun rootChildName(root: LegacyContent): String {
		return if (root.name().isEmpty()) "chapter" else root.name()
	}

	private fun matchChildId(parentId: Int, name: String): Int {
		for (child in ItemRepository.children(parentId))
			if (child.type == ItemKind.CHAPTER && child.name == name)
				return child.id
		return -1
	}
}
