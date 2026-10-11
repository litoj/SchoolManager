package cz.litoj.schlmgr.data

import androidx.annotation.VisibleForTesting
import androidx.room.withTransaction
import cz.litoj.schlmgr.app.Progress
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.WordDao
import cz.litoj.schlmgr.data.db.dao.WordTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.data.db.AddTranslationsParams
import cz.litoj.schlmgr.data.db.DaoItemStore
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.Descriptions
import cz.litoj.schlmgr.data.db.FlipService
import cz.litoj.schlmgr.data.db.GraphWalk
import cz.litoj.schlmgr.data.db.ImportService
import cz.litoj.schlmgr.data.db.InsertWordParams
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemStore
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.KeptTranslation
import cz.litoj.schlmgr.data.db.MoveRequest
import cz.litoj.schlmgr.data.db.NewTranslation
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.ShiftRange
import cz.litoj.schlmgr.data.db.WordEdit

/**
 * Single gateway for all data access.
 *
 * - synchronous facade — callers hand over item streams
 * - database calls block on `Dispatchers.IO`
 * - export renders and walks on caller thread
 */
object ItemRepository {

	/** One bulk statement's id budget — below SQLite's 999 host-parameter cap. */
	private const val SQL_IDS_PER_STATEMENT = 900

	@Volatile
	private var db: ItemDatabase? = null

	/**
	 * Installs the caller-built database (no Android context here). Must run
	 * once on app startup, before the first query.
	 */
	@JvmStatic
	fun init(database: ItemDatabase) {
		db = database
	}

	/**
	 * Installs a caller-made database — the in-memory Room database of the unit
	 * tests, which need the real graph logic without the on-disk file.
	 */
	@VisibleForTesting
	internal fun initForTest(database: ItemDatabase) {
		db = database
	}

	/** True when database installed — callers skip data access when false. */
	@JvmStatic
	val initialized: Boolean get() = db != null

	/**
	 * Creates the root row when missing — once on startup, before reading
	 * the subject list. Parentless subjects stay unlinked.
	 */
	@JvmStatic
	fun ensureRoot(): Unit = query { it.itemDao().insertRoot(ROOT_ID) }

	private fun <T> query(block: suspend (ItemDatabase) -> T): T {
		val database = db ?: throw IllegalStateException("ItemRepository not initialized")
		return runBlocking(Dispatchers.IO) { block(database) }
	}

	// ------------------------------------------------------------ reading

	@JvmStatic
	fun subjects(): List<DbItem> = query { it.itemDao().getChildren(ROOT_ID) }

	@JvmStatic
	fun children(parentId: Int): List<DbItem> = query { it.itemDao().getChildren(parentId) }

	/**
	 * Parent set of every direct child — browse reference facts.
	 * A browse load shows children only, but needs every parent link so the
	 * strip can tell which rows are references and whether they still live
	 * elsewhere — one batch read instead of one per-row lookup.
	 */
	fun childLinksOf(parentId: Int): List<DbLink> = query { it.itemDao().childLinksOf(parentId) }

	/**
	 * The translations of every word child of the container, keyed by word
	 * id — one batch read for a browse load's default-flipped rows.
	 */
	fun childTranslations(parentId: Int): Map<Int, List<DbItem>> = query {
		it.wordDao().childTranslations(parentId).groupBy(WordTranslation::wordId) { it.item }
	}

	@JvmStatic
	fun get(id: Int): DbItem? = query { it.itemDao().get(id) }

	@JvmStatic
	@VisibleForTesting
	fun getByName(name: String, kind: ItemKind): List<DbItem> =
		query { it.itemDao().getByName(name, kind) }

	@JvmStatic
	fun translations(wordId: Int): List<DbItem> = query { it.wordDao().getTranslations(wordId) }

	@JvmStatic
	fun parentIds(childId: Int): List<Int> = query { it.itemDao().parentsOf(childId) }

	/**
	 * Container chain down to parent, subject first, root excluded.
	 * Multi-parent rows take first parent found.
	 */
	@JvmStatic
	fun pathTo(parentId: Int): List<DbItem> = query {
		val dao = it.itemDao()
		val chain = ArrayList<DbItem>()
		var cur = dao.get(parentId)
		// Single parent-row hop per level — root row ends walk.
		while (cur != null && cur.id != ROOT_ID) {
			chain.add(cur)
			cur = dao.anyParentOf(cur.id)
		}
		chain.reverse()
		chain
	}

	@JvmStatic
	fun subjectRootOf(itemId: Int): Int? = query { it.itemDao().subjectRootOf(itemId, ROOT_ID) }

	@JvmStatic
	fun subgraph(rootId: Int): List<DbItem> = query { it.itemDao().subgraph(rootId) }

	/**
	 * Distinct word and chapter counts of union, roots included.
	 * Shared rows count once per chunk — cosmetic only.
	 */
	@JvmStatic
	fun subgraphStats(roots: Collection<Int>): Pair<Int, Int> = query {
		var words = 0
		var chapters = 0
		for (chunk in roots.chunked(SQL_IDS_PER_STATEMENT))
			for (count in it.itemDao().subgraphTypeCounts(chunk))
				if (count.kind == ItemKind.WORD) words += count.amount
				else chapters += count.amount
		Pair(words, chapters)
	}

	fun subgraphLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphLinks(rootId) }

	/**
	 * Every link into subgraph — whole parent set, outside included.
	 * Preloaded for search — hit rows need no per-row lookup.
	 * Outside parents matter because a reference link's parent can sit outside
	 * the subgraph, and the search row must still know where to jump to.
	 */
	fun subgraphChildLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphChildLinks(rootId) }

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
	 * A word with translations as pairs. Same-named words merge instead of doubling.
	 * - every word carries translation — translation-less rows are notes.
	 */
	@JvmStatic
	fun createWord(
		parentId: Int,
		name: String,
		description: String?,
		translations: List<Pair<String, String?>>,
	): DbItem {
		require(translations.any { it.first.isNotEmpty() }) {
			"A word needs at least one translation — make a note instead"
		}
		return query {
			it.withTransaction {
				val dao = it.itemDao()
				val word = insertWord(DaoItemStore(dao, it.wordDao()), InsertWordParams(parentId, name, description,
					translations.map { NewTranslation(it.first, it.second) }))
				// a merge onto an existing word brings its counters into the
				// parent's derivation — no other create path changes them
				recomputeCounterChain(dao, parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
				word
			}
		}
	}

	// ------------------------------------------------------------ importing and exporting
	//
	// Saves converted item streams and reads them back,
	// parsing and rendering live with codecs.

	/**
	 * Saves item stream in one transaction, order preserved.
	 * - `null` parents take anchor.
	 * - words merge by name in scope, containers by name and type.
	 * - unresolvable references skipped.
	 */
	@JvmStatic
	fun import(importer: DataImporter, anchorId: Int = ROOT_ID): ImportResult = query {
		it.withTransaction { ImportService.run(it.itemDao(), importer, anchorId).result }
	}

	/** Root rows created or merged, plus stream counts. */
	class ImportResult(
		/** Rows stream roots became — subjects at root, anchor children below. */
		val topLevel: List<DbItem>,
		val words: Int,
		val chapters: Int,
	)

	/**
	 * Word selection would also change outside itself through plain links.
	 * Refuses to run over such word — caller reports list.
	 */
	class FlipConflict(val word: DbItem, val parents: List<DbItem>)

	/**
	 * Reads containers content as stream and renders it.
	 * Every walked row reports through exporter.
	 */
	@JvmStatic
	fun export(exporter: DataExporter, containers: List<DbItem>): String =
		exporter.render(containers.flatMap { streamOf(exporter, it) })

	/**
	 * Pre-order stream of container content — container first, then rows below.
	 * - preloads whole subtree in two queries.
	 */
	private fun streamOf(exporter: DataExporter, container: DbItem): List<StreamItem> {
		val root = StreamItem(container, null)
		val out = ArrayList<StreamItem>()
		out.add(root)
		exporter.onItem(root)
		val rows = query { it.itemDao().subgraph(container.id) }
		val itemsById = HashMap<Int, DbItem>(rows.size * 2)
		for (row in rows) itemsById[row.id] = row
		// Sort links by position — matches live per-node order.
		val children = HashMap<Int, MutableList<DbItem>>()
		for (link in query { it.itemDao().subgraphLinks(container.id) }
			.sortedWith(compareBy({ it.position }, { it.childId }))) {
			val child = itemsById[link.childId] ?: continue
			children.getOrPut(link.parentId) { ArrayList() }.add(child)
		}
		GraphWalk.collectInto(out, root,
			{ walked ->
				val kids = children[walked.id] ?: emptyList()
				// Words list translations only.
				if (walked.type == ItemKind.WORD) kids.filter { it.type == ItemKind.TRANSLATION }
				else kids
			},
			exporter::onItem)
		return out
	}

	/**
	 * Word-add merge rule for every item add.
	 * - same-named word in scope merges, new one otherwise.
	 */
	internal suspend fun insertWord(store: ItemStore, params: InsertWordParams): DbItem {
		val scope = store.subjectRootOf(params.parentId) ?: params.parentId
		val existing = store.findInSubject(scope, params.name, ItemKind.WORD)
		val word = if (existing != null) {
			store.mergeCounters(existing, params.passedTests, params.failedTests)
			store.mergeDescription(existing, params.description)
		} else store.insert(DbItem(
			name = params.name, description = params.description ?: "", type = ItemKind.WORD,
			passedTests = params.passedTests, failedTests = params.failedTests))
		if (!store.linked(params.parentId, word.id)) store.linkAtEnd(params.parentId, word.id)
		addTranslations(store, AddTranslationsParams(scope, word.id, params.translations))
		return word
	}

	/**
	 * Translation-add half of merge rule, for any store.
	 * - same-named translation in scope merges, new one otherwise.
	 * - empty names skipped. Rows return in given order.
	 */
	internal suspend fun addTranslations(store: ItemStore, params: AddTranslationsParams): List<DbItem> {
		val word = store.row(params.wordId) ?: return emptyList()
		val rows = ArrayList<DbItem>(params.translations.size)
		for (translation in params.translations) {
			if (translation.name.isEmpty()) continue
			val existing = store.findInSubject(params.scope, translation.name, ItemKind.TRANSLATION)
			val trl = if (existing != null) {
				// Merge adopts more-tested counters.
				store.mergeCounters(existing, translation.passedTests ?: word.passedTests,
					translation.failedTests ?: word.failedTests)
				store.mergeDescription(existing, translation.description)
			}
			else store.insert(DbItem(
				name = translation.name, description = translation.description ?: "",
				type = ItemKind.TRANSLATION,
				passedTests = translation.passedTests ?: word.passedTests,
				failedTests = translation.failedTests ?: word.failedTests))
			if (!store.linked(params.wordId, trl.id)) store.linkAtEnd(params.wordId, trl.id)
			rows.add(trl)
		}
		return rows
	}

	// ------------------------------------------------------------ editing

	/**
	 * Renames an item. A word colliding with another word of the same subject
	 * merges into it — links and translations move to the surviving row, the
	 * renamed row is deleted.
	 */
	@JvmStatic
	fun rename(id: Int, name: String): DbItem = query {
		it.withTransaction { renameInternal(it.itemDao(), it.wordDao(), id, name) }
	}

	private suspend fun renameInternal(dao: ItemDao, wordDao: WordDao, id: Int, name: String): DbItem {
		val item = dao.get(id) ?: throw IllegalArgumentException("No item $id")
		if (item.type == ItemKind.WORD) {
			val scope = dao.subjectRootOf(id, ROOT_ID) ?: id
			val target = wordDao.findWordInSubject(scope, name)
			if (target != null && target.id != id) return FlipService.mergeWords(dao, id, target)
		}
		dao.updateById(id, name, item.description)
		return dao.get(id)!!
	}

	@JvmStatic
	fun setDescription(id: Int, description: String?): Unit = query {
		val item = it.itemDao().get(id) ?: return@query
		// null clears — the column is non-null, an absent value is the empty text
		it.itemDao().updateById(id, item.name, description ?: "")
	}

	/**
	 * Whether edit forks — chapter shares word through plain link,
	 * editor renamed word and removed translation.
	 * - rename licenses fork — avoids duplicate row.
	 * - UI asks first to show reference popup.
	 */
	@JvmStatic
	fun editForks(edit: WordEdit): Boolean = query {
		it.withTransaction { forks(it.itemDao(), edit) }
	}

	private suspend fun forks(dao: ItemDao, edit: WordEdit): Boolean {
		val word = dao.get(edit.wordId) ?: return false
		val plainParents = dao.plainParentsOf(edit.wordId)
		return edit.parentId in plainParents && plainParents.any { it != edit.parentId } &&
			edit.removed.isNotEmpty() && edit.name != word.name
	}

	/** Items showing word through reference link — fork popup list. */
	@JvmStatic
	fun referencesOf(wordId: Int): List<DbItem> = query { it.itemDao().referenceParentsOf(wordId) }

	/**
	 * Applies committed word edit.
	 * - forks when shared, renamed and lost translation.
	 * - otherwise in place — rename-join keeps survivor.
	 * - refuses edit leaving word without translation.
	 */
	@JvmStatic
	fun editWord(edit: WordEdit): DbItem = query {
		// Name content counts — empty-named additions land nothing.
		if (edit.kept.isEmpty() && edit.additions.none { it.first.isNotEmpty() })
			throw IllegalArgumentException("A word without a translation: '${edit.name}'")
		it.withTransaction { editWordInternal(it.itemDao(), it.wordDao(), edit) }
	}

	private suspend fun editWordInternal(dao: ItemDao, wordDao: WordDao, edit: WordEdit): DbItem {
		dao.get(edit.wordId) ?: throw IllegalArgumentException("No item ${edit.wordId}")
		val scope = dao.subjectRootOf(edit.wordId, ROOT_ID) ?: edit.wordId
		val keptRows = edit.kept.map { kept ->
			val current = dao.get(kept.id) ?: throw IllegalArgumentException("No item ${kept.id}")
			kept to current
		}
		// Rows kept renames merged into — later same-name rows join.
		val joinedTargets = HashSet<Int>()
		if (forks(dao, edit)) return forkWord(dao, wordDao, edit, keptRows, scope, joinedTargets)
		// In-place half — removals first to avoid carrying them over.
		for (trl in edit.removed) {
			dao.deleteLink(edit.wordId, trl)
			if (dao.parentCount(trl) == 0) deleteGraph(dao, trl)
		}
		for ((kept, current) in keptRows)
			applyKeptTranslation(dao, wordDao, scope, kept, current, edit.wordId, edit.wordId, null, joinedTargets)
		val merged = renameInternal(dao, wordDao, edit.wordId, edit.name)
		// Rename-join carries deleted description over — editor value joins.
		val desc = if (merged.id != edit.wordId) Descriptions.merge(merged.description, edit.description)
		else edit.description ?: ""
		dao.updateById(merged.id, merged.name, desc)
		insertWord(DaoItemStore(dao, wordDao), InsertWordParams(edit.parentId, edit.name, desc,
			edit.additions.map { NewTranslation(it.first, it.second) }))
		return dao.get(merged.id)!!
	}

	/**
	 * Fork half of edit.
	 * - continuing word takes original slot, kept rows, additions and references.
	 * - original keeps removed translations, other chapters and counters.
	 */
	private suspend fun forkWord(
		dao: ItemDao, wordDao: WordDao, edit: WordEdit,
		keptRows: List<Pair<KeptTranslation, DbItem>>, scope: Int, joinedTargets: MutableSet<Int>,
	): DbItem {
		val slot = dao.linkPosition(edit.parentId, edit.wordId)
		val existing = wordDao.findWordInSubject(scope, edit.name)?.takeIf { it.id != edit.wordId }
		val fork = if (existing != null) {
			val merged = Descriptions.merge(existing.description, edit.description)
			if (merged != existing.description) dao.updateById(existing.id, existing.name, merged)
			existing
		} else dao.insert(
			DbItem(name = edit.name, description = edit.description ?: "", type = ItemKind.WORD)
		).let { dao.get(it.toInt())!! }
		dao.deleteLink(edit.parentId, edit.wordId)
		if (dao.linkPosition(edit.parentId, fork.id) == null)
			dao.insertLink(DbLink(edit.parentId, fork.id, slot ?: dao.nextPosition(edit.parentId)))
		// Editor order indexes fresh list — joined words append after existing.
		val base = if (existing != null)
			dao.getChildren(fork.id).count { it.type == ItemKind.TRANSLATION } else 0
		for ((index, row) in keptRows.withIndex())
			applyKeptTranslation(dao, wordDao, scope, row.first, row.second, edit.wordId, fork.id, base + index, joinedTargets)
		addTranslations(DaoItemStore(dao, wordDao), AddTranslationsParams(scope, fork.id,
			edit.additions.map { NewTranslation(it.first, it.second) }))
		// Kept references follow fork, removed ones keep original.
		for (parent in edit.referencesToFork) {
			if (dao.linkIsReference(parent, edit.wordId) != true) continue
			dao.deleteLink(parent, edit.wordId)
			if (dao.linkPosition(parent, fork.id) == null)
				dao.insertLink(DbLink(parent, fork.id, dao.nextPosition(parent), isReference = true))
			// Following parent word set changed — chain recomputes.
			recomputeCounterChain(dao, parent, dao::subgraphWordCounters, dao::parentsPlainFirst)
		}
		// Fork changes edited chapter word set — containers above recompute.
		recomputeCounterChain(dao, edit.parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		return dao.get(fork.id)!!
	}

	/**
	 * Applies one kept row.
	 * - rename onto existing translation joins that row.
	 * - shared rows stay — edited word gets own row.
	 * - joined descriptions merge.
	 */
	private suspend fun applyKeptTranslation(
		dao: ItemDao, wordDao: WordDao, scope: Int,
		kept: KeptTranslation, current: DbItem,
		oldOwnerId: Int, newOwnerId: Int, position: Int?, joinedTargets: MutableSet<Int>,
	) {
		var target = current.id
		if (kept.name == current.name) {
		// Sibling rename may have merged here — kept text joins live content.
			val live = dao.get(current.id)!!
			val desc = if (current.id in joinedTargets) Descriptions.merge(live.description, kept.description)
				else kept.description
			if (desc != live.description)
				dao.updateById(current.id, current.name, desc)
		} else {
			val existing = wordDao.findWordInSubject(scope, kept.name, ItemKind.TRANSLATION)
			if (existing != null && existing.id != current.id) {
				val desc = Descriptions.merge(
					Descriptions.merge(existing.description, current.description), kept.description)
				if (desc != existing.description) dao.updateById(existing.id, existing.name, desc)
			// Merge adopts more-tested counters.
				if (current.passedTests + current.failedTests > existing.passedTests + existing.failedTests)
					dao.updateCounters(existing.id, current.passedTests, current.failedTests)
				joinedTargets.add(existing.id)
				target = existing.id
			} else if (dao.parentCount(current.id) > 1) {
			// Replacement starts at zero counters.
				target = dao.insert(
					DbItem(name = kept.name, description = kept.description, type = ItemKind.TRANSLATION))
					.let { dao.get(it.toInt())!! }.id
			} else {
				dao.updateById(current.id, kept.name, kept.description)
			}
		}
		// Row leaves previous word on fork, join or shared replacement.
		if (oldOwnerId != newOwnerId || target != current.id) {
			dao.deleteLink(oldOwnerId, current.id)
			if (target != current.id && dao.parentCount(current.id) == 0) deleteGraph(dao, current.id)
		}
		if (dao.linkPosition(newOwnerId, target) == null) linkAt(dao, newOwnerId, target, position)
	}

	/**
	 * Swaps words with translations (chapters expand to words).
	 * - name collisions merge.
	 * - shared translations replaced, not orphaned.
	 * - references follow — check conflicts first.
	 */
	@JvmStatic
	fun flip(ids: List<Int>): Unit = query {
		val wordDao = it.wordDao()
		it.withTransaction { FlipService.flipIds(it.itemDao(), wordDao, ids, ROOT_ID) }
	}

	/**
	 * Words selection would also change outside itself.
	 * Read-only — caller aborts on non-empty result.
	 */
	@JvmStatic
	fun flipConflicts(actingParents: Collection<Int>, ids: List<Int>): List<FlipConflict> = query {
		FlipService.conflicts(it.itemDao(), actingParents, ids)
	}

	/**
	 * Moves item between containers of one subject, never copied.
	 * Null position appends at end.
	 * - [MoveResult.REFUSED_CYCLE]: move would close cycle.
	 * - [MoveResult.REFUSED_OTHER_SUBJECT]: target in another subject — use subject merge.
	 * - [MoveResult.ALREADY_PRESENT]: target already links row.
	 * - nothing changes on refusal.
	 */
	@JvmStatic
	@JvmOverloads
	fun move(
		childId: Int, oldParentId: Int, newParentId: Int, position: Int? = null,
	): MoveResult = query {
		it.withTransaction {
			val dao = it.itemDao()
			// Items stay in subject — moving into another subject refuses.
			if (dao.subjectRootOf(childId, ROOT_ID) != dao.subjectRootOf(newParentId, ROOT_ID))
				return@withTransaction MoveResult.REFUSED_OTHER_SUBJECT
			if (dao.linkPosition(newParentId, childId) != null)
				return@withTransaction MoveResult.ALREADY_PRESENT
			if (moveInternal(dao, MoveRequest(childId, oldParentId, newParentId, position)))
				MoveResult.MOVED else MoveResult.REFUSED_CYCLE
		}
	}

	/** Why [move] refused, when it did. */
	enum class MoveResult { MOVED, REFUSED_CYCLE, REFUSED_OTHER_SUBJECT, ALREADY_PRESENT }

	/**
	 * Moves whole subject into container of another subject.
	 * - subject row becomes chapter, same-named rows merge below.
	 * - reference links re-created, outside-shared rows survive.
	 * - returns `false` for target inside moved subject or root.
	 */
	@JvmStatic
	fun mergeSubject(sourceId: Int, targetParentId: Int): Boolean = query {
		it.withTransaction {
			val dao = it.itemDao()
			if (targetParentId == ROOT_ID) return@withTransaction false
			val source = dao.get(sourceId) ?: return@withTransaction false
			if (closesCycle(dao, sourceId, targetParentId)) return@withTransaction false
			// Plain-link containment tree, in link order — reference links ride separately.
			val rows = dao.subgraph(sourceId)
			val byId = HashMap<Int, DbItem>(rows.size * 2)
			for (row in rows) byId[row.id] = row
			val children = HashMap<Int, MutableList<DbItem>>()
			for (link in dao.subgraphLinks(sourceId)
				.sortedWith(compareBy({ it.position }, { it.childId }))) {
				val child = byId[link.childId] ?: continue
				if (link.isReference) continue
				children.getOrPut(link.parentId) { ArrayList() }.add(child)
			}
			// Stream root anchored at target.
			val items = ArrayList<StreamItem>()
			val rootItem = StreamItem(source, null)
			items.add(rootItem)
			GraphWalk.collectInto(items, rootItem, { walked ->
				val kids = children[walked.id] ?: emptyList()
				// Words list translations only.
				if (walked.type == ItemKind.WORD) kids.filter { it.type == ItemKind.TRANSLATION }
				else kids
			}, {})
			val outcome = ImportService.run(dao, StreamImporter(items), targetParentId)
			// Reference links follow rows — in-tree endpoints remap, outside ones keep row.
			val mergedOf = HashMap<Int, Int>()
			for (entry in items) mergedOf[entry.item.id] = outcome.savedRows[entry] ?: continue
			// Counter phase sums session links only — relinked parents recompute.
			val relinked = ArrayList<Int>()
			for (link in dao.subgraphChildLinks(sourceId).filter { it.isReference }) {
				val parent = mergedOf[link.parentId] ?: link.parentId
				val child = mergedOf[link.childId] ?: link.childId
				// Outside parent survives delete — its link would keep dying row alive.
				if (parent == link.parentId && child != link.childId)
					dao.deleteLink(link.parentId, link.childId)
				if (dao.linkPosition(parent, child) == null) {
					dao.insertLink(DbLink(parent, child, dao.nextPosition(parent), isReference = true))
					relinked.add(parent)
				}
			}
			// Re-created links add reachable words — chain recomputes with them.
			recomputeCounterChain(dao, targetParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			for (container in relinked.distinct())
				recomputeCounterChain(dao, container, dao::subgraphWordCounters, dao::parentsPlainFirst)
			// Moved content lives in target — source goes unless still referenced.
			dao.deleteLink(ROOT_ID, sourceId)
			if (dao.parentCount(sourceId) == 0) deleteGraph(dao, sourceId)
			true
		}
	}

	/** Ready item stream as import source — subject merge tree. */
	private class StreamImporter(override val items: List<StreamItem>) : DataImporter {
		override val references: List<StreamReference> = emptyList()
	}

	private suspend fun moveInternal(dao: ItemDao, req: MoveRequest): Boolean {
		if (closesCycle(dao, req.childId, req.newParentId)) return false
		dao.deleteLink(req.oldParentId, req.childId)
		linkAt(dao, req.newParentId, req.childId, req.position)
		// Moved subtree words trade one derivation for other.
		recomputeCounterChain(dao, req.oldParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		recomputeCounterChain(dao, req.newParentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		return true
	}

	/**
	 * True when linking child under target would close cycle —
	 * target sits inside child content, child itself included.
	 */
	private suspend fun closesCycle(dao: ItemDao, childId: Int, targetId: Int): Boolean =
		dao.subgraph(childId).any { it.id == targetId }

	/**
	 * Places existing item into another container through reference link.
	 * Any item, chapter included, can be referenced — whole content shows
	 * through link alone, with no per-item copies.
	 * - returns `true` without change when link already exists.
	 * - returns `false` when link would close cycle.
	 */
	@JvmStatic
	fun reference(childId: Int, parentId: Int): Boolean = query {
		it.withTransaction {
			val dao = it.itemDao()
			if (dao.linkPosition(parentId, childId) != null) return@withTransaction true
			if (closesCycle(dao, childId, parentId)) return@withTransaction false
			dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId), isReference = true))
			// Referenced subtree words join parent derivation.
			recomputeCounterChain(dao, parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
			true
		}
	}

	/** Whether parent-child link is reference. */
	@JvmStatic
	fun linkIsReference(parentId: Int, childId: Int): Boolean =
		query { it.itemDao().linkIsReference(parentId, childId) == true }

	/**
	 * Removes item from one container.
	 * - last parent gone — reachable graph dies, shared items survive.
	 * - each removed row reports to progress.
	 */
	@JvmStatic
	@JvmOverloads
	fun remove(childId: Int, parentId: Int?, progress: Progress? = null): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			// Word with no translations goes with last translation — only container links sever.
			if (parentId != null && isLastTranslation(dao, childId, parentId)) {
				val wordParents = dao.parentsPlainFirst(parentId)
				for (parent in wordParents) dao.deleteLink(parent, parentId)
				deleteGraph(dao, parentId, progress)
				for (parent in wordParents)
					recomputeCounterChain(dao, parent, dao::subgraphWordCounters, dao::parentsPlainFirst)
				return@withTransaction
			}
			if (parentId != null) dao.deleteLink(parentId, childId)
			if (dao.parentCount(childId) == 0) deleteGraph(dao, childId, progress)
			// Removed words leave parent derivation.
			if (parentId != null)
				recomputeCounterChain(dao, parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		}
	}

	/** Whether link is word's only translation. */
	private suspend fun isLastTranslation(dao: ItemDao, childId: Int, parentId: Int): Boolean =
		dao.get(childId)?.type == ItemKind.TRANSLATION &&
			dao.get(parentId)?.type == ItemKind.WORD &&
			dao.getChildren(parentId).none { it.type == ItemKind.TRANSLATION && it.id != childId }

	/**
	 * Deletes graph reachable from root, preserving shared items.
	 * - walk completes before deletion — items with outside parents survive.
	 * - one row per statement (SQLite host-parameter cap).
	 */
	private suspend fun deleteGraph(dao: ItemDao, rootId: Int, progress: Progress? = null) {
		// Reachable from root, each id visited once.
		val doomed = HashSet<Int>()
		doomed.add(rootId)
		val stack = ArrayDeque<Int>()
		stack.addLast(rootId)
		while (stack.isNotEmpty()) {
			val parent = stack.removeLast()
			for (child in dao.childIdsOf(parent))
				if (doomed.add(child)) stack.addLast(child)
		}
		// Deletable only when all parents deletable — parents read once until stable.
		val dead = HashSet<Int>()
		dead.add(rootId)
		val parents = HashMap<Int, List<Int>>()
		var changed = true
		while (changed) {
			changed = false
			for (x in doomed) {
				if (x in dead) continue
				if (parents.getOrPut(x) { dao.parentsOf(x) }.all { it in dead }) {
					dead.add(x)
					changed = true
				}
			}
		}
		for (id in dead) {
			dao.deleteLinksTouching(id)
			dao.deleteById(id)
			progress?.advance(1)
		}
	}

	/**
	 * Puts child at 0-based index — slots between shift by one.
	 * Raw positions carry gaps, so index maps onto occupying slot.
	 */
	@JvmStatic
	fun reorder(parentId: Int, childId: Int, newIndex: Int): Unit = query {
		it.withTransaction {
			val dao = it.itemDao()
			val links = dao.linksOf(parentId).sortedBy { it.position }
			val oldVisual = links.indexOfFirst { it.childId == childId }
			if (oldVisual < 0) return@withTransaction
			val target = newIndex.coerceIn(0, links.lastIndex)
			if (target == oldVisual) return@withTransaction
			val old = links[oldVisual].position
			val slot = links[target].position
			if (target < oldVisual) dao.shift(ShiftRange(parentId, 1, slot, old - 1))
			else dao.shift(ShiftRange(parentId, -1, old + 1, slot))
			dao.setLinkPosition(parentId, childId, slot)
		}
	}

	/**
	 * Persists manual child order — every id takes zero-based slot it holds.
	 * Unknown ids ignored, so stale entries cannot corrupt.
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
	 * Records one passed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordPass(ids: Collection<Int>): Unit = query {
		if (ids.isEmpty()) return@query
		it.withTransaction { for (chunk in ids.chunked(SQL_IDS_PER_STATEMENT)) it.itemDao().addPassed(chunk) }
	}

	/**
	 * Records one failed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordFail(ids: Collection<Int>): Unit = query {
		if (ids.isEmpty()) return@query
		it.withTransaction { for (chunk in ids.chunked(SQL_IDS_PER_STATEMENT)) it.itemDao().addFailed(chunk) }
	}

	/**
	 * Records one test answer on every given row (the tested word, its shown
	 * translation, and the whole chapter path of the test source — the
	 * hierarchy update the tests require).
	 */
	@JvmStatic
	fun bumpCounters(ids: Collection<Int>, passed: Boolean): Unit =
		if (passed) recordPass(ids) else recordFail(ids)

	/**
	 * Recomputes a container's stored success/fail from the words in its
	 * subgraph and returns it.
	 */
	@JvmStatic
	fun recomputeCounters(containerId: Int): Counters = query {
		val counts = it.itemDao().subgraphWordCounters(containerId)
		it.itemDao().updateCounters(containerId, counts.passed, counts.failed)
		counts
	}

	/**
	 * Recomputes counters of start and every container above it.
	 * - walks all parents, each container once — shared containers carry sums.
	 * - never root — sum spans whole database and shows nowhere.
	 * - callers supply sums and parents.
	 */
	internal suspend fun recomputeCounterChain(
		dao: ItemDao,
		start: Int,
		countersOf: suspend (Int) -> Counters,
		parentsOf: suspend (Int) -> List<Int>,
	) {
		val seen = HashSet<Int>()
		seen.add(start)
		val queue = ArrayDeque<Int>()
		queue.add(start)
		while (queue.isNotEmpty()) {
			val id = queue.removeFirst()
			if (id != ROOT_ID) {
				val counts = countersOf(id)
				dao.updateCounters(id, counts.passed, counts.failed)
			}
			for (parent in parentsOf(id)) {
				if (parent == ROOT_ID) continue
				if (seen.add(parent)) queue.add(parent)
			}
		}
	}

	// ------------------------------------------------------------ helpers

	/** Direct row insert — tests only (no merge, explicit counters). */
	@VisibleForTesting
	internal fun legacyInsert(
		name: String, description: String?, kind: ItemKind, passed: Int, failed: Int,
	): Int = query {
		it.itemDao().insert(
			DbItem(
				name = name, description = description ?: "", type = kind,
				passedTests = passed, failedTests = failed,
			)
		).toInt()
	}

	/** Direct link insert at the end of the parent's list — tests only. */
	@VisibleForTesting
	internal fun legacyLinkAtEnd(parentId: Int, childId: Int): Unit = query {
		val dao = it.itemDao()
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId)))
	}

	/** Links child into parent at position, or end when null. */
	internal suspend fun linkAt(dao: ItemDao, parentId: Int, childId: Int, position: Int?) {
		// Position is visual index — raw positions carry gaps.
		val links = dao.linksOf(parentId).sortedBy { it.position }
		val pos = when {
			position == null || position >= links.size -> dao.nextPosition(parentId)
			else -> links[position].position
		}
		// Occupants from target up make room.
		dao.shift(ShiftRange(parentId, 1, pos, dao.nextPosition(parentId) - 1))
		dao.insertLink(DbLink(parentId, childId, pos))
	}
}
