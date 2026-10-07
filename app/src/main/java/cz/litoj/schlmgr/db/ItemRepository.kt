package cz.litoj.schlmgr.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.db.dao.ItemDao
import cz.litoj.schlmgr.db.dao.WordDao
import cz.litoj.schlmgr.db.dao.Sf
import cz.litoj.schlmgr.db.io.JsonChapter
import cz.litoj.schlmgr.db.io.WordListChapter

/**
 * The app's only data gateway: a synchronous facade over the Room database.
 *
 * The schema is the user-specified single-table design:
 *  - `item` — one row per chapter/word/translation/note
 *    (name, description, type, passed_tests, failed_tests);
 *  - `item_link` — the one M:N relation (parent, child, position) for chapter
 *    children, word translations and items placed in more chapters.
 *  - every subject is a child of the single invisible root row ([ROOT_ID]), so a
 *    subject is an ordinary child of a container — reorderable and uniform.
 *
 * Every method blocks (`runBlocking`) — the UI layer is thread-based and its
 * callers already run on background threads. [init] must be called once from
 * app startup before any other call.
 */
object ItemRepository {

	@Volatile
	private var db: ItemDatabase? = null

	/** Must be called once on app startup (before the first query). */
	@JvmStatic
	fun init(context: Context) {
		if (db == null) synchronized(this) {
			if (db == null) db = ItemDatabase.newInstance(context.applicationContext)
		}
	}

	/**
	 * Installs a caller-made database — the in-memory Room database of the unit
	 * tests, which need the real graph logic without the on-disk file.
	 */
	@VisibleForTesting
	internal fun initForTest(database: ItemDatabase) {
		db = database
	}

	/** True when [init] has been called — callers no-op data access when false (unit tests). */
	@JvmStatic
	val initialized: Boolean get() = db != null

	/**
	 * The reserved id of the single invisible root row. All subjects are its children, so
	 * the explorer treats the subjects screen like any other container. `0` is safe: Room
	 * autoincrement starts at `1`.
	 */
	const val ROOT_ID = 0

	/**
	 * Creates the root row when missing. Idempotent — run it once on startup before
	 * reading the subject list. Old parentless subjects are deliberately left unlinked.
	 */
	@JvmStatic
	fun ensureRoot(): Unit = query { it.itemDao().insertRoot(ROOT_ID) }

	private fun <T> query(block: suspend (ItemDatabase) -> T): T {
		val database = db ?: throw IllegalStateException("ItemRepository not initialized")
		return runBlocking(Dispatchers.IO) { block(database) }
	}

	// ------------------------------------------------------------ reading

	@JvmStatic
	fun isEmpty(): Boolean = query { it.itemDao().getChildren(ROOT_ID).isEmpty() }

	/** The subjects: the root's children, in link order. */
	@JvmStatic
	fun subjects(): List<DbItem> = query { it.itemDao().getChildren(ROOT_ID) }

	/** Children of [parentId], ordered by the link position. */
	@JvmStatic
	fun children(parentId: Int): List<DbItem> = query { it.itemDao().getChildren(parentId) }

	@JvmStatic
	fun get(id: Int): DbItem? = query { it.itemDao().get(id) }

	@JvmStatic
	fun getByName(name: String, kind: ItemKind): List<DbItem> =
		query { it.itemDao().getByName(name, kind) }

	/** The translations of a word, in link order. */
	@JvmStatic
	fun translations(wordId: Int): List<DbItem> = query { it.wordDao().getTranslations(wordId) }

	@JvmStatic
	fun parentIds(childId: Int): List<Int> = query { it.itemDao().parentsOf(childId) }

	/**
	 * The container chain from the subject down to [parentId] (subject first,
	 * [parentId] last) — the browsing path of a container, excluding the invisible
	 * root (so [ROOT_ID] yields an empty chain). A parent with more containers above
	 * it uses the first one found.
	 */
	@JvmStatic
	fun pathTo(parentId: Int): List<DbItem> = query {
		val dao = it.itemDao()
		val chain = ArrayList<DbItem>()
		var cur = dao.get(parentId)
		while (cur != null && cur.id != ROOT_ID) {
			chain.add(cur)
			val up = dao.parentsOf(cur.id).firstOrNull() ?: break
			if (up == ROOT_ID) break
			cur = dao.get(up)
		}
		chain.reverse()
		chain
	}

	/** The subject that contains [itemId] (the ancestor whose parent is the root). */
	@JvmStatic
	fun subjectRootOf(itemId: Int): Int? = query { it.itemDao().subjectRootOf(itemId, ROOT_ID) }

	/** All rows reachable from [rootId] — the subject scope. */
	@JvmStatic
	fun subgraph(rootId: Int): List<DbItem> = query { it.itemDao().subgraph(rootId) }

	/**
	 * All links between the rows of [subgraph] — the other half of a container's
	 * preloaded content (see [ItemDao.subgraphLinks]).
	 */
	fun subgraphLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphLinks(rootId) }

	// ------------------------------------------------------------ creating

	@JvmStatic
	fun createSubject(name: String, description: String?): DbItem = query {
		it.withTransaction {
			val dao = it.itemDao()
			val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.CHAPTER)).toInt()
			linkAt(dao, ROOT_ID, id, null)
			dao.get(id)!!
		}
	}

	@JvmStatic
	@JvmOverloads
	fun createChapter(parentId: Int, name: String, description: String?, position: Int? = null): DbItem =
		query {
			it.withTransaction {
				val dao = it.itemDao()
				val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.CHAPTER)).toInt()
				linkAt(dao, parentId, id, position)
				dao.get(id)!!
			}
		}

	@JvmStatic
	@JvmOverloads
	fun createNote(parentId: Int, name: String, description: String?, position: Int? = null): DbItem =
		query {
			it.withTransaction {
				val dao = it.itemDao()
				val id = dao.insert(DbItem(name = name, description = description ?: "", type = ItemKind.NOTE)).toInt()
				linkAt(dao, parentId, id, position)
				dao.get(id)!!
			}
		}

	/**
	 * A word with [translations] as (name, description) pairs. Preserves the
	 * engine's name-merge: a word with the same name in the same subject gets
	 * the new parent link and the missing translations instead of a second row.
	 *
	 * @return the word row (existing when merged)
	 */
	@JvmStatic
	fun createWord(
		parentId: Int,
		name: String,
		description: String?,
		translations: List<Pair<String, String?>>,
	): DbItem = query {
		it.withTransaction {
			insertWord(it.itemDao(), it.wordDao(), parentId, name, description,
				translations.map { NewTranslation(it.first, it.second) })
		}
	}

	/**
	 * Inserts a parsed word-list tree ([WordListReader] output) under [parentId],
	 * in one transaction, with the same name-merge as [createWord].
	 */
	@JvmStatic
	fun importWordList(parentId: Int, content: WordListChapter): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			val wordDao = it.wordDao()
			importChapter(dao, wordDao, parentId, content)
		}
	}

	/**
	 * Inserts a parsed tree JSON export ([JsonWordList] output) in one
	 * transaction. Each element of [subjects] becomes a subject under the root;
	 * its chapters become nested chapters.
	 *
	 * Chapters, words and translations merge by name with the rows already in
	 * their scope, so a re-import never duplicates them. The counters of a word
	 * or translation row created from the file take the file's `s`/`f`; a
	 * container's stored counters are never trusted from the file — they are
	 * derived from the words below it, as [recomputeSf] does on every read.
	 */
	@JvmStatic
	fun importJson(subjects: List<JsonChapter>): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			val wordDao = it.wordDao()
			for (subject in subjects) importJsonContainer(dao, wordDao, ROOT_ID, subject)
		}
	}

	private suspend fun importChapter(
		dao: ItemDao, wordDao: WordDao, parentId: Int, content: WordListChapter,
	) {
		for (word in content.words)
			insertWord(dao, wordDao, parentId, word.name, word.description,
				word.translations.map { NewTranslation(it.name, it.description) })
		for (child in content.children) {
			val id = dao.insert(
				DbItem(name = child.name, description = child.description ?: "", type = ItemKind.CHAPTER)
			).toInt()
			linkAt(dao, parentId, id, null)
			importChapter(dao, wordDao, id, child)
			recomputeContainerSf(dao, id)
		}
		recomputeContainerSf(dao, parentId)
	}

	/**
	 * Inserts one container of an imported tree under [parentId] and recurses.
	 * A chapter of the same name already under [parentId] is reused and its
	 * description merged, so importing the same file twice adds no chapter
	 * shells.
	 */
	private suspend fun importJsonContainer(
		dao: ItemDao, wordDao: WordDao, parentId: Int, chapter: JsonChapter,
	) {
		val existing = dao.getChildren(parentId)
			.firstOrNull { it.type == ItemKind.CHAPTER && it.name == chapter.name }
		val container = if (existing != null) {
			val merged = mergeDescription(existing.description, chapter.description)
			if (merged != existing.description) {
				dao.updateById(existing.id, existing.name, merged)
				dao.get(existing.id)!!
			} else existing
		} else {
			// the file's container counters are ignored: words are the only trusted
			// counters, the container's are derived once its subtree is in
			val id = dao.insert(
				DbItem(name = chapter.name, description = chapter.description ?: "", type = ItemKind.CHAPTER)
			).toInt()
			linkAt(dao, parentId, id, null)
			dao.get(id)!!
		}
		for (word in chapter.words)
			insertWord(dao, wordDao, container.id, word.name, word.description,
				word.translations.map {
					NewTranslation(it.name, it.description, it.passed, it.failed)
				},
				word.passed, word.failed)
		for (child in chapter.chapters) importJsonContainer(dao, wordDao, container.id, child)
		recomputeContainerSf(dao, container.id)
	}

	/**
	 * Stores [containerId]'s success/fail derived from the word rows in its
	 * subgraph — the container's own stored counters (possibly from a file)
	 * are never trusted.
	 */
	private suspend fun recomputeContainerSf(dao: ItemDao, containerId: Int) {
		val sf = dao.subgraphWordSf(containerId)
		dao.updateSf(containerId, sf.passed, sf.failed)
	}

	/** One translation to insert; a `null` counter pair inherits the word's counters. */
	private class NewTranslation(
		val name: String,
		val description: String?,
		val passed: Int? = null,
		val failed: Int? = null,
	)

	private suspend fun insertWord(
		dao: ItemDao, wordDao: WordDao, parentId: Int,
		name: String, description: String?,
		translations: List<NewTranslation>,
		passed: Int = 0, failed: Int = 0,
	): DbItem {
		val scope = dao.subjectRootOf(parentId, ROOT_ID) ?: parentId
		val existing = wordDao.findWordInSubject(scope, name)
		val word = if (existing != null) {
			val merged = mergeDescription(existing.description, description)
			if (merged != existing.description) {
				dao.updateById(existing.id, existing.name, merged)
				dao.get(existing.id)!!
			} else existing
		} else dao.insert(
			DbItem(
				name = name, description = description ?: "", type = ItemKind.WORD,
				passedTests = passed, failedTests = failed,
			)
		).let { id ->
			dao.get(id.toInt())!!
		}
		if (dao.linkPosition(parentId, word.id) == null) linkAt(dao, parentId, word.id, null)
		addTranslations(wordDao, dao, scope, word.id, translations)
		return word
	}

	/**
	 * Adds translations to a word, merging by name inside the subject scope:
	 * an existing translation row is linked to the word, a new one is created
	 * (inheriting the word's current success/fail counts unless the source
	 * carries its own counters, as the tree JSON does).
	 */
	private suspend fun addTranslations(
		wordDao: WordDao, dao: ItemDao, scope: Int,
		wordId: Int, translations: List<NewTranslation>,
	) {
		val word = dao.get(wordId) ?: return
		for (translation in translations) {
			if (translation.name.isEmpty()) continue
			val existing = wordDao.findWordInSubject(scope, translation.name, ItemKind.TRANSLATION)
			val trl = if (existing != null) {
				val merged = mergeDescription(existing.description, translation.description)
				if (merged != existing.description) dao.updateById(existing.id, existing.name, merged)
				existing
			} else dao.insert(
				DbItem(
					name = translation.name, description = translation.description ?: "",
					type = ItemKind.TRANSLATION,
					passedTests = translation.passed ?: word.passedTests,
					failedTests = translation.failed ?: word.failedTests,
				)
			).let { id -> dao.get(id.toInt())!! }
			if (dao.linkPosition(wordId, trl.id) == null) linkAt(dao, wordId, trl.id, null)
		}
	}

	// ------------------------------------------------------------ editing

	/**
	 * Renames an item. A word whose new name collides with another word of the
	 * same subject merges into it: all parent links and translations move to
	 * the surviving row, the renamed row is deleted — the engine's `setName`
	 * behaviour.
	 *
	 * @return the row that now carries the name (the merge target for words)
	 */
	@JvmStatic
	fun rename(id: Int, name: String): DbItem = query {
		it.withTransaction {
			val dao = it.itemDao()
			val item = dao.get(id) ?: throw IllegalArgumentException("No item $id")
			if (item.type == ItemKind.WORD) {
				val scope = dao.subjectRootOf(id, ROOT_ID) ?: id
				val target = it.wordDao().findWordInSubject(scope, name)
				if (target != null && target.id != id) return@withTransaction mergeWords(dao, id, target)
			}
			dao.updateById(id, name, item.description)
			dao.get(id)!!
		}
	}

	@JvmStatic
	fun setDescription(id: Int, description: String?): Unit = query {
		val item = it.itemDao().get(id) ?: return@query
		it.itemDao().updateById(id, item.name, description)
	}

	/**
	 * Flips the given rows between the word and translation roles: every word
	 * becomes a translation of each of its translations, and each translation
	 * becomes a word that takes over the old word's containers. A chapter stands
	 * for every word below it, recursively.
	 *
	 * Both role changes keep the subject's (name, type) pairs unique — the same
	 * name merge an import uses: a translation whose name is already a word of
	 * the subject merges into that word, and a word whose name is already a
	 * translation of the subject merges into that row, so the word count may
	 * grow (a word with several translations) or shrink (several words sharing
	 * a translation).
	 *
	 * A translation row shared by several words keeps serving the words that are
	 * not being flipped: they receive a replacement row, because a word without
	 * a translation is invalid.
	 */
	@JvmStatic
	fun flip(ids: List<Int>): Unit = query {
		val wordDao = it.wordDao()
		it.withTransaction { flipIds(it.itemDao(), wordDao, ids) }
	}

	private suspend fun flipIds(dao: ItemDao, wordDao: WordDao, ids: List<Int>) {
		for (id in ids) {
			val item = dao.get(id) ?: continue
			when (item.type) {
				ItemKind.WORD -> flipWord(dao, wordDao, item)
				ItemKind.CHAPTER ->
					for (word in wordsUnder(dao, item.id)) flipWord(dao, wordDao, word)
				else -> {}
			}
		}
	}

	/** The words of a container's subgraph — a chapter expands to every word below it. */
	private suspend fun wordsUnder(dao: ItemDao, containerId: Int): List<DbItem> {
		val words = ArrayList<DbItem>()
		for (child in dao.getChildren(containerId)) {
			when (child.type) {
				ItemKind.WORD -> words.add(child)
				ItemKind.CHAPTER -> words.addAll(wordsUnder(dao, child.id))
				else -> {}
			}
		}
		return words
	}

	private suspend fun flipWord(dao: ItemDao, wordDao: WordDao, word: DbItem) {
		val translations = dao.getChildren(word.id).filter { it.type == ItemKind.TRANSLATION }
		if (translations.isEmpty()) return
		val scope = dao.subjectRootOf(word.id, ROOT_ID) ?: word.id
		val parents = dao.parentsOf(word.id)
		// The old word becomes a translation: the subject's row of that (name,
		// translation) pair absorbs it — one row per pair per subject, like the
		// import merge. The flipped translations are excluded from the search:
		// they leave the translation role below.
		val flippedIds = translations.mapTo(HashSet()) { it.id }
		val existingTrl = wordDao.findWordInSubject(scope, word.name, ItemKind.TRANSLATION)
			?.takeIf { it.id !in flippedIds && it.id != word.id }
		val trlTargetId = existingTrl?.id ?: word.id

		for (trl in translations) {
			// a translation that already is a word of the subject absorbs the duplicate row
			val existing = wordDao.findWordInSubject(scope, trl.name)
				?.takeIf { it.id != trl.id && it.id != word.id }
			// the other words sharing this row keep a translation of this name: one
			// replacement row serves them all, so the sharing itself survives.
			// A translation named like the old word already gets its row above.
			val sharers = dao.parentsOf(trl.id).filter { it != word.id }
			if (sharers.isNotEmpty()) {
				val replacement = if (trl.name == word.name) trlTargetId else dao.insert(
					DbItem(name = trl.name, description = trl.description, type = ItemKind.TRANSLATION)
				).toInt()
				for (sharer in sharers) {
					dao.deleteLink(sharer, trl.id)
					if (dao.linkPosition(sharer, replacement) == null)
						dao.insertLink(DbLink(sharer, replacement, dao.nextPosition(sharer)))
				}
			}
			val newWordId = if (existing != null) {
				val merged = mergeDescription(existing.description, trl.description)
				if (merged != existing.description) dao.updateById(existing.id, existing.name, merged)
				dao.deleteLinksTouching(setOf(trl.id))
				dao.deleteById(trl.id)
				existing.id
			} else {
				dao.updateType(trl.id, ItemKind.WORD)
				// the word→translation link must go: kept, it would cycle with the
				// translation→word link created below
				dao.deleteLink(word.id, trl.id)
				trl.id
			}
			// the new word takes over the old word's containers
			for (parent in parents)
				if (dao.linkPosition(parent, newWordId) == null)
					dao.insertLink(DbLink(parent, newWordId, dao.nextPosition(parent)))
			// the old word's translation role lands on the subject's row of the name
			if (dao.linkPosition(newWordId, trlTargetId) == null)
				dao.insertLink(DbLink(newWordId, trlTargetId, dao.nextPosition(newWordId)))
		}
		// the old word leaves the containers; it is a translation now
		for (parent in parents) dao.deleteLink(parent, word.id)
		if (existingTrl != null) {
			// the surviving row takes the description; the old word row itself is gone
			val merged = mergeDescription(existingTrl.description, word.description)
			if (merged != existingTrl.description) dao.updateById(existingTrl.id, existingTrl.name, merged)
			dao.deleteLinksTouching(setOf(word.id))
			dao.deleteById(word.id)
		} else {
			dao.updateType(word.id, ItemKind.TRANSLATION)
		}
	}

	/** Merges [from] (the renamed row) into [target] and deletes [from]. */
	private suspend fun mergeWords(dao: ItemDao, from: Int, target: DbItem): DbItem {
		// parent links move to the surviving word (keep each container once)
		for (parent in dao.parentsOf(from))
			if (dao.linkPosition(parent, target.id) == null)
				dao.insertLink(DbLink(parent, target.id, dao.nextPosition(parent)))
		// translations union: move the old word's translations to the survivor
		for (trl in dao.getChildren(from))
			if (dao.linkPosition(target.id, trl.id) == null)
				dao.insertLink(DbLink(target.id, trl.id, dao.nextPosition(target.id)))
		val desc = dao.get(from)?.description
		dao.deleteLinksTouching(setOf(from))
		dao.deleteById(from)
		val merged = mergeDescription(target.description, desc)
		if (merged != target.description) dao.updateById(target.id, target.name, merged)
		return dao.get(target.id)!!
	}

	/**
	 * Moves an item between containers: the (old parent → child) link is
	 * re-pointed. The item itself is never copied.
	 */
	@JvmStatic
	@JvmOverloads
	fun move(childId: Int, oldParentId: Int, newParentId: Int, position: Int? = null): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			dao.deleteLink(oldParentId, childId)
			linkAt(dao, newParentId, childId, position)
		}
	}

	/** Places an existing item into another container through a reference link (the former Reference). */
	@JvmStatic
	fun reference(childId: Int, parentId: Int): Boolean = query {
		val dao = it.itemDao()
		// A chapter reference is refused: the words under it would be
		// reachable through every path that reaches the chapter, so each of
		// its words would need its own references too — the duplication the
		// legacy engine forbade.
		if (dao.get(childId)?.type == ItemKind.CHAPTER) return@query false
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId), isReference = true))
		true
	}

	/** Whether the (parent, child) link is a reference link — see [reference]. */
	@JvmStatic
	fun linkIsReference(parentId: Int, childId: Int): Boolean =
		query { it.itemDao().linkIsReference(parentId, childId) == true }

	/**
	 * Removes an item from one container. When that was its last parent, the
	 * whole graph reachable from it dies — shared items survive through their
	 * other parents (see [ItemDao.deleteGraph]).
	 */
	@JvmStatic
	fun remove(childId: Int, parentId: Int?): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			if (parentId != null) dao.deleteLink(parentId, childId)
			if (dao.parentCount(childId) == 0) dao.deleteGraph(childId)
		}
	}

	/**
	 * Puts a child at [newIndex] (0-based) of [parentId]'s list; positions
	 * between the old and new slot shift by one.
	 */
	@JvmStatic
	fun reorder(parentId: Int, childId: Int, newIndex: Int): Unit = query {
		val dao = it.itemDao()
		val old = dao.linkPosition(parentId, childId) ?: return@query
		if (newIndex < old) dao.shiftPositions(parentId, 1, newIndex, old - 1)
		else if (newIndex > old) dao.shiftPositions(parentId, -1, old + 1, newIndex)
		else return@query
		dao.setLinkPosition(parentId, childId, newIndex)
	}

	/**
	 * Persists the manual child order of [parentId] — every id takes the zero-based
	 * slot it holds in [orderedChildIds]. Ids that are not children of [parentId] are
	 * ignored, so a stale entry cannot corrupt another container.
	 */
	@JvmStatic
	fun setOrder(parentId: Int, orderedChildIds: List<Int>): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			orderedChildIds.forEachIndexed { index, id -> dao.setLinkPosition(parentId, id, index) }
		}
	}

	// ------------------------------------------------------------ test stats

	/**
	 * Records one test answer on every given row (the tested word, its shown
	 * translation, and the whole chapter path of the test source — the
	 * hierarchy update the tests require).
	 */
	@JvmStatic
	fun bumpSf(ids: Collection<Int>, passed: Boolean): Unit = query {
		if (ids.isEmpty()) return@query
		if (passed) it.itemDao().addPassed(ids) else it.itemDao().addFailed(ids)
	}

	/**
	 * Recomputes a container's stored success/fail from the words in its
	 * subgraph (the former `refreshSF`) and returns it.
	 */
	@JvmStatic
	fun recomputeSf(containerId: Int): Sf = query {
		val sf = it.itemDao().subgraphWordSf(containerId)
		it.itemDao().updateSf(containerId, sf.passed, sf.failed)
		sf
	}

	// ------------------------------------------------------------ helpers

	/**
	 * Combines a stored description with a freshly loaded one: the loaded value is
	 * appended after a newline when it is not already contained in the stored value.
	 * A word/translation loaded again (from another chapter or another import) then
	 * keeps every distinct description instead of silently dropping all but one.
	 */
	@JvmStatic
	fun mergeDescription(stored: String, incoming: String?): String {
		if (incoming.isNullOrEmpty()) return stored
		if (stored.contains(incoming)) return stored
		return if (stored.isEmpty()) incoming else stored + '\n' + incoming
	}

	/** Appends [description] to the row's stored description (see [mergeDescription]). */
	@JvmStatic
	fun appendDescription(id: Int, description: String?): Unit = query {
		if (description.isNullOrEmpty()) return@query
		val dao = it.itemDao()
		val item = dao.get(id) ?: return@query
		val merged = mergeDescription(item.description, description)
		if (merged != item.description) dao.updateById(id, item.name, merged)
	}

	/** Direct row insert — legacy migration only (no merge, explicit sf). */
	@JvmStatic
	fun legacyInsert(
		name: String, description: String?, kind: ItemKind, passed: Int, failed: Int,
	): Int = query {
		it.legacyInsertDao().insertItem(
			DbItem(
				name = name, description = description ?: "", type = kind,
				passedTests = passed, failedTests = failed,
			)
		).toInt()
	}

	/**
	 * Direct link insert with an explicit position — legacy migration only. A link
	 * that already exists stays untouched: a name re-encountered during the walk
	 * re-uses its row, so re-linking it must not duplicate the child under its parent.
	 */
	@JvmStatic
	fun legacyLink(parentId: Int, childId: Int, position: Int): Unit = query {
		if (it.itemDao().linkPosition(parentId, childId) == null)
			it.legacyInsertDao().insertLink(DbLink(parentId, childId, position))
	}

	/** Direct link insert at the end of the parent's list — legacy migration only. */
	@JvmStatic
	fun legacyLinkAtEnd(parentId: Int, childId: Int): Unit = query {
		val dao = it.itemDao()
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId)))
	}

	/** Links [childId] into [parentId] at [position] (or the end when null). */
	private suspend fun linkAt(dao: ItemDao, parentId: Int, childId: Int, position: Int?) {
		val pos = position ?: dao.nextPosition(parentId)
		dao.insertLink(DbLink(parentId, childId, pos))
	}
}
