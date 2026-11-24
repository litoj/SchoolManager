package cz.litoj.schlmgr.data

import androidx.annotation.VisibleForTesting
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.WordTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import cz.litoj.schlmgr.data.db.CounterChain
import cz.litoj.schlmgr.data.db.DaoItemStore
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.FlipAnalysis
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.GraphDelete
import cz.litoj.schlmgr.data.db.ImportResult
import cz.litoj.schlmgr.data.db.SQL_IDS_PER_STATEMENT
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.LinkOrder
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit

/**
 * Blocking fixture over the injected data services.
 * - its database calls stay outside the main source, whose callers own their scopes.
 */
object ItemRepository {

	@Volatile
	private var db: ItemDatabase? = null

	/**
	 * Installs the caller-built database, since no Android context exists here.
	 * Must run once on app startup, before the first query.
	 */
	@JvmStatic
	fun init(database: ItemDatabase) {
		db = database
	}

	/**
	 * The unit tests need the real graph logic without the on-disk file.
	 */
	@VisibleForTesting
	internal fun initForTest(database: ItemDatabase) {
		db = database
	}

	/** Drops the test database, so a later test cannot read a closed one. */
	@VisibleForTesting
	internal fun resetForTest() {
		db = null
	}

	/** Callers skip data access when false. */
	@JvmStatic
	val initialized: Boolean get() = db != null

	/**
	 * Must run once on startup, before reading the subject list.
	 * Parentless subjects stay unlinked.
	 */
	@JvmStatic
	fun ensureRoot(): Unit = query { it.itemDao().insertRoot(ROOT_ID) }

	private fun <T> query(block: suspend (ItemDatabase) -> T): T {
		val database = db ?: throw IllegalStateException("ItemRepository not initialized")
		return runBlocking(Dispatchers.IO) { block(database) }
	}

	internal fun counterService(): CounterService =
		CounterService(db ?: throw IllegalStateException("ItemRepository not initialized"))

	private fun itemEditor(): ItemEditor =
		ItemEditor(db ?: throw IllegalStateException("ItemRepository not initialized"))

	private fun itemTransferService(): ItemTransferService =
		ItemTransferService(db ?: throw IllegalStateException("ItemRepository not initialized"))

	private fun flipRunner(): FlipRunner =
		FlipRunner(db ?: throw IllegalStateException("ItemRepository not initialized"))

	@JvmStatic
	fun subjects(): List<DbItem> = query { it.itemDao().getChildren(ROOT_ID) }

	@JvmStatic
	fun children(parentId: Int): List<DbItem> = query { it.itemDao().getChildren(parentId) }

	/**
	 * Parent set of every direct child, the browse reference facts.
	 * - a browse load shows the children only, but needs every parent link.
	 * - the strip then knows which rows are references and whether they still live elsewhere.
	 * - one batch read replaces one lookup per row.
	 */
	fun childLinksOf(parentId: Int): List<DbLink> = query { it.itemDao().childLinksOf(parentId) }

	/**
	 * One batch read for the default-flipped rows of a browse load.
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

	/** One batch read of the prompt sets for all tested words. */
	@JvmStatic
	fun translationsForWords(wordIds: Collection<Int>): Map<Int, List<DbItem>> = query { db ->
		// every batch stays inside the host parameter cap, though the visible map merges them
		wordIds.chunked(SQL_IDS_PER_STATEMENT)
			.flatMap { batch -> db.wordDao().translationsForWords(batch) }
			.groupBy(WordTranslation::wordId) { it.item }
	}

	@JvmStatic
	fun parentIds(childId: Int): List<Int> = query { it.itemDao().parentsOf(childId) }

	/** The rows that link to [childId] in link order, the words of a translation. */
	@JvmStatic
	fun parentRows(childId: Int): List<DbItem> = query { it.itemDao().parentRowsOf(childId) }

	/** The plain home of a container, null for a row only references hold. */
	@JvmStatic
	fun plainParentOf(childId: Int): Int? = query { it.itemDao().plainParentsOf(childId).firstOrNull() }

	/**
	 * Container chain down to parent, subject first, root excluded.
	 * Multi-parent rows take first parent found.
	 */
	@JvmStatic
	fun pathTo(parentId: Int): List<DbItem> = query {
		val dao = it.itemDao()
		val chain = ArrayList<DbItem>()
		var cur = dao.get(parentId)
		while (cur != null && cur.id != ROOT_ID) {
			chain.add(cur)
			cur = dao.anyParentOf(cur.id)
		}
		chain.reverse()
		chain
	}

	@JvmStatic
	fun subgraph(rootId: Int): List<DbItem> = query { it.itemDao().subgraph(rootId) }

	/**
	 * Distinct word and chapter counts of the union, the roots included.
	 * Shared rows count once per chunk, which is cosmetic only.
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
	 * Every link into the subgraph, the whole parent set with the outside included.
	 * - the search preloads it, so hit rows need no per-row lookup.
	 * - an outside parent matters because a reference link can sit outside the subgraph.
	 * - the search row must still know where to jump to.
	 */
	fun subgraphChildLinks(rootId: Int): List<DbLink> = query { it.itemDao().subgraphChildLinks(rootId) }

	@JvmStatic
	fun createSubject(name: String, description: String?): DbItem = query {
		itemEditor().createSubject(name, description)
	}

	@JvmStatic
	@JvmOverloads
	fun createChapter(parentId: Int, name: String, description: String?, position: Int? = null): DbItem = query {
		itemEditor().createChapter(parentId, name, description, position)
	}

	@JvmStatic
	@JvmOverloads
	fun createNote(parentId: Int, name: String, description: String?, position: Int? = null): DbItem = query {
		itemEditor().createNote(parentId, name, description, position)
	}

	/**
	 * A word with translations as pairs, and same-named words merge instead of doubling.
	 * - every word carries a translation, since a row without one is a note.
	 */
	@JvmStatic
	fun createWord(
		parentId: Int,
		name: String,
		description: String?,
		translations: List<Pair<String, String?>>,
	): DbItem = query {
		itemEditor().createWord(parentId, name, description, translations)
	}

	//
	// saves converted item streams and reads them back, while parsing and rendering
	// live with the codecs.

	/**
	 * Saves an item stream in one transaction, order preserved.
	 * - null parents take the anchor.
	 * - words merge by name in scope, containers by name and type.
	 * - unresolvable references are skipped.
	 */
	@JvmStatic
	fun import(importer: DataImporter, anchorId: Int = ROOT_ID): ImportResult = query {
		DataExchangeService(it).import(importer, anchorId)
	}

	/**
	 * Reads the container content as a stream and renders it.
	 * - the whole walked stream hands over through [DataExporter.onStream] before the render.
	 * - its size is the target of the progress bar, with each item one written object.
	 */
	@JvmStatic
	fun export(exporter: DataExporter, containers: List<DbItem>): String = query {
		DataExchangeService(it).export(exporter, containers)
	}

	/**
	 * Renames an item.
	 * - a blank name refuses, since no export format round-trips it.
	 * - a word colliding with another word of the same subject merges into it.
	 * - links and translations move to the surviving row.
	 * - the renamed row is deleted.
	 */
	@JvmStatic
	fun rename(id: Int, name: String): DbItem = query {
		itemEditor().rename(id, name)
	}

	@JvmStatic
	fun setDescription(id: Int, description: String?): Unit = query {
		itemEditor().setDescription(id, description)
	}

	/**
	 * Whether the edit forks, when a shared word is renamed and loses a translation.
	 * - the word is shared when a chapter holds it through a plain link.
	 * - the rename licenses the fork, which avoids a duplicate row.
	 * - a word that no longer exists throws, like the edit itself.
	 * - the UI asks first to show the reference popup.
	 */
	@JvmStatic
	fun editForks(edit: WordEdit): Boolean = query {
		itemEditor().editForks(edit)
	}

	/** Items showing the word through a reference link, the fork popup list. */
	@JvmStatic
	fun referencesOf(wordId: Int): List<DbItem> = query { itemEditor().referencesOf(wordId) }

	/**
	 * Applies a committed word edit.
	 * - it forks when the word is shared, renamed and lost a translation.
	 * - otherwise it edits in place, and a rename-join keeps the survivor.
	 */
	@JvmStatic
	fun editWord(edit: WordEdit): DbItem = query {
		itemEditor().editWord(edit)
	}

	/**
	 * Swaps words with translations and expands chapters to words in one pass.
	 * - no conflict loop of [startFlip], [advanceFlip] and [commitFlip] runs here.
	 * - name collisions merge.
	 * - shared translations are replaced, not orphaned.
	 * - references follow the flip.
	 */
	@JvmStatic
	fun flip(ids: List<Int>): Unit = query {
		flipRunner().flip(ids)
	}

	/**
	 * Starts a flip run with the plan and the first round of relations that
	 * span the selection and the outside. See FlipConflict.
	 */
	@JvmStatic
	fun startFlip(actingParents: Collection<Int>, ids: List<Int>): FlipAnalysis = query {
		flipRunner().start(actingParents, ids)
	}

	/**
	 * Applies one popup round and re-analyzes, the loop step between
	 * [startFlip] and [commitFlip].
	 */
	@JvmStatic
	fun advanceFlip(analysis: FlipAnalysis, resolutions: List<FlipResolution>): FlipAnalysis = query {
		flipRunner().advance(analysis, resolutions)
	}

	/** Runs the resolved plan, the custom versions first, then the flip. */
	@JvmStatic
	fun commitFlip(analysis: FlipAnalysis): Unit = query {
		flipRunner().commit(analysis)
	}

	/**
	 * Moves an item between containers of one subject, never copied.
	 * - a null position appends at the end.
	 * - [MoveResult.REFUSED_CYCLE]: the move would close a cycle.
	 * - [MoveResult.REFUSED_OTHER_SUBJECT]: the target sits in another subject.
	 * - use the subject merge for that case.
	 * - [MoveResult.ALREADY_PRESENT]: the target already links the row.
	 * - a home row replaces the target's reference link, which keeps its slot.
	 * - nothing changes on refusal.
	 */
	@JvmStatic
	@JvmOverloads
	fun move(
		childId: Int, oldParentId: Int, newParentId: Int, position: Int? = null,
	): ItemRepository.MoveResult = query {
		MoveResult.valueOf(itemTransferService().move(childId, oldParentId, newParentId, position).name)
	}

	enum class MoveResult { MOVED, REFUSED_CYCLE, REFUSED_OTHER_SUBJECT, ALREADY_PRESENT }

	/**
	 * Moves a whole subject into a container of another subject.
	 * - the subject row becomes a chapter, and same-named rows merge below.
	 * - reference links re-create, and outside-shared rows survive.
	 * - returns false for an invalid target or a cycle found after reference remapping.
	 */
	@JvmStatic
	fun mergeSubject(sourceId: Int, targetParentId: Int): Boolean = query {
		itemTransferService().mergeSubject(sourceId, targetParentId)
	}

	/**
	 * What one container would do to one row of a paste batch.
	 * - [present]: the container already links the row.
	 * - [reference]: that existing link is a reference.
	 * - [inside]: the container sits inside the row content, the row itself included.
	 * - [otherSet]: the row and the container sit in different subjects.
	 * - the facts the write guards test, so a preview and a write cannot disagree by rule.
	 */
	data class PasteFacts(
		val present: Boolean,
		val reference: Boolean,
		val inside: Boolean,
		val otherSet: Boolean,
	)

	/**
	 * The facts of every row in batch order, read in one block.
	 * - the inside walk follows reference links, like the guards do.
	 */
	@JvmStatic
	fun pasteFacts(childIds: List<Int>, targetId: Int): List<PasteFacts> = query {
		itemTransferService().pasteFacts(childIds, targetId).map {
			PasteFacts(it.present, it.reference, it.inside, it.otherSet)
		}
	}

	/**
	 * Places an existing item into another container through a reference link.
	 * - any item, a chapter included, can be referenced.
	 * - the whole content shows through the link alone, with no per-item copies.
	 * - no counter changes, because a reference never counts toward the rate of the parent.
	 * - returns true without change when the link already exists.
	 * - returns false when the link would close a cycle.
	 * - a referenced row keeps its plain home, because subject walks take plain links first.
	 * - a cross-subject reference never changes the subject of a home-holding row.
	 */
	@JvmStatic
	fun reference(childId: Int, parentId: Int): Boolean = query {
		itemTransferService().reference(childId, parentId)
	}

	/** Adds every link in one transaction, so no cycle can leave a half-done pick. */
	@JvmStatic
	fun referenceAll(childIds: Collection<Int>, parentId: Int): Boolean = query {
		itemTransferService().referenceAll(childIds, parentId)
	}

	@JvmStatic
	fun linkIsReference(parentId: Int, childId: Int): Boolean =
		query { it.itemDao().linkIsReference(parentId, childId) == true }

	/**
	 * Removes an item from one container.
	 * - a last parent gone means the reachable graph dies, while shared items survive.
	 * - each removed row reports to progress.
	 */
	@JvmStatic
	@JvmOverloads
	fun remove(childId: Int, parentId: Int?, progress: Progress? = null): Unit = query {
		itemTransferService().remove(childId, parentId, progress)
	}

	/**
	 * Puts the child at a 0-based index, and the slots between shift by one.
	 * Raw positions carry gaps, so the index maps onto the occupying slot.
	 */
	@JvmStatic
	fun reorder(parentId: Int, childId: Int, newIndex: Int): Unit = query {
		itemTransferService().reorder(parentId, childId, newIndex)
	}

	/**
	 * Persists the manual child order.
	 * See [LinkOrder.setOrder] for the slot rules.
	 */
	@JvmStatic
	fun setOrder(parentId: Int, orderedChildIds: List<Int>): Unit = query {
		itemTransferService().setOrder(parentId, orderedChildIds)
	}

	/**
	 * Records one passed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordPass(ids: Collection<Int>): Unit = query {
		counterService().recordPass(ids)
	}

	/**
	 * Records one failed answer on every given row.
	 * - ids chunked, each row bumps once per chunk.
	 */
	@JvmStatic
	fun recordFail(ids: Collection<Int>): Unit = query {
		counterService().recordFail(ids)
	}

	/**
	 * Records one test answer on every given row: the shown translations of the tested word.
	 * Containers derive their counters through [bumpWordCounters].
	 */
	@JvmStatic
	fun bumpCounters(ids: Collection<Int>, passed: Boolean): Unit =
		if (passed) recordPass(ids) else recordFail(ids)

	/**
	 * Records one test answer on the word and on every container whose sums hold it.
	 * - these are the plain ancestors of the word.
	 * - reference links never count.
	 * - a shared container meets the word through several parents but holds it once.
	 */
	@JvmStatic
	fun bumpWordCounters(wordId: Int, passed: Boolean): Unit = query {
		counterService().bumpWordCounters(wordId, passed)
	}

	/**
	 * Recomputes the stored success/fail of a container from the words in its subgraph.
	 * The fresh counters are returned as well.
	 */
	@JvmStatic
	fun recomputeCounters(containerId: Int): Counters = query {
		CounterChain.recomputeCounters(it.itemDao(), containerId)
	}

	/**
	 * Recomputes the counters of [start] and of every container above it.
	 * - it walks all parents, each container once, and shared containers carry sums.
	 * - never the root, since its sum spans the whole database and shows nowhere.
	 * - callers supply the sums and the parents.
	 */
	internal suspend fun recomputeCounterChain(
		dao: ItemDao,
		start: Int,
		countersOf: suspend (Int) -> Counters,
		parentsOf: suspend (Int) -> List<Int>,
	) = CounterChain.recomputeCounterChain(dao, start, countersOf, parentsOf)

	/** Direct row insert for tests only, with no merge and explicit counters. */
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

	@VisibleForTesting
	internal fun legacyLinkAtEnd(parentId: Int, childId: Int): Unit = query {
		val dao = it.itemDao()
		dao.insertLink(DbLink(parentId, childId, dao.nextPosition(parentId)))
	}
}
