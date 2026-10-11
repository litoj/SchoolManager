package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.StreamItem

/**
 * One import or subject-merge run.
 * - three save phases over accumulator and session.
 * - stateless — repository owns transaction. Maps stream to rows.
 */
internal object ImportService {

	/**
	 * One import run outcome — public result plus row each stream item became.
	 * - subject merge re-linking reads map.
	 */
	internal class ImportOutcome(
		val result: ItemRepository.ImportResult,
		val savedRows: Map<StreamItem, Int>,
	)

	/** Saves an item stream within the caller's transaction. */
	suspend fun run(
		dao: ItemDao, importer: DataImporter, anchorId: Int,
	): ImportOutcome {
		requireWordsTranslated(importer)
		val session = ImportSession.load(dao, anchorId)
		val acc = ImportAccumulator()
		saveImportStream(session, importer, anchorId, acc)
		resolveImportReferences(session, importer, acc)
		acc.remapIds(session.flush())
		refreshImportCounters(session, dao, importer, acc, anchorId)
		return ImportOutcome(
			ItemRepository.ImportResult(acc.topLevel, acc.words, acc.chapters), acc.saved)
	}

	/**
	 * Stream shape valid — word children are named translations only, at least one.
	 * Violating file fails whole import before anything written.
	 */
	private fun requireWordsTranslated(importer: DataImporter) {
		val childrenOf = importer.items.groupBy({ it.parent }, { it.item })
		for (entry in importer.items) {
			if (entry.item.type != ItemKind.WORD) continue
			val children = childrenOf[entry] ?: emptyList()
			if (children.any { it.type != ItemKind.TRANSLATION }) throw IllegalArgumentException(
				"A word's children are its translations: '${entry.item.name}'")
			if (children.none { it.name.isNotEmpty() }) throw IllegalArgumentException(
				"A word without a translation: '${entry.item.name}' — make it a note instead")
		}
	}

	/**
	 * Save phase — every stream entry becomes row linked under parent, `null`
	 * parents anchor to the import target.
	 */
	private suspend fun saveImportStream(
		session: ImportSession, importer: DataImporter, anchorId: Int, acc: ImportAccumulator,
	) {
		for (entry in importer.items) {
			// first, before the row work — a skipped entry (an empty
			// translation) must not fall behind the bar's total
			importer.onItem(entry)
			val parent = entry.parent?.let(acc.saved::get) ?: anchorId
			val row = when (entry.item.type) {
				ItemKind.WORD -> {
					acc.words++
					ItemRepository.insertWord(session, InsertWordParams(parent, entry.item.name,
						entry.item.description, emptyList(),
						entry.item.passedTests, entry.item.failedTests))
				}
				ItemKind.TRANSLATION -> ItemRepository.addTranslations(session, AddTranslationsParams(
					session.subjectRootOf(parent) ?: parent, parent,
					listOf(NewTranslation(entry.item.name, entry.item.description,
						entry.item.passedTests, entry.item.failedTests)))).firstOrNull()
					?: continue // an empty name adds no row
				ItemKind.CHAPTER -> {
					acc.chapters++
					session.insertContainer(parent, entry.item)
				}
				// a note — saved like a container, counted as neither
				else -> session.insertContainer(parent, entry.item)
			}
			acc.saved[entry] = row.id
			acc.indexSavedRow(parent, entry.item.name, row.id, entry.item.type)
			if (entry.parent == null) acc.topLevel.add(row)
		}
	}

	/**
	 * Reference phase, against saved rows.
	 * - path names first root plus containers — target is named row under last.
	 * - unresolvable references skipped.
	 */
	private suspend fun resolveImportReferences(
		session: ImportSession, importer: DataImporter, acc: ImportAccumulator,
	) {
		val rootRow = acc.topLevel.firstOrNull()?.id
		for (ref in importer.references) {
			val parentRow = acc.saved[ref.parent] ?: continue
			var cur = rootRow ?: continue
			var resolved = true
			for (i in 1 until ref.path.size) {
				val next = acc.chaptersByParent[cur]?.get(ref.path[i])
				if (next == null) { resolved = false; break }
				cur = next
			}
			if (!resolved) continue
			val target = acc.rowsByParent[cur]?.get(ref.targetName) ?: continue
			// File may name ancestor of own position — cycle pre-order walks cannot leave.
			// Session graph knows pending links — database does not yet.
			if (parentRow != target && !session.linked(parentRow, target) &&
				!session.contains(target, parentRow))
				session.addReference(parentRow, target)
		}
	}

	/**
	 * Counter phase — every touched container derives counters from words below.
	 * - bottom-up through saved chapters, then anchor and containers above.
	 * - merged chapter outside holders follow.
	 * - counter adoption touches holders.
	 * - sums come from session graph — unloaded rows fall back to SQL.
	 */
	private suspend fun refreshImportCounters(
		session: ImportSession, dao: ItemDao, importer: DataImporter,
		acc: ImportAccumulator, anchorId: Int,
	) {
		val sums = SessionSums(session, dao)
		// built upfront — the bottom-up walk meets stream parents before visiting them,
		// so a guard fed by the loop itself never fires
		val streamIds = HashSet<Int>()
		for (entry in importer.items) {
			val id = acc.saved[entry] ?: continue
			if (entry.item.type == ItemKind.CHAPTER) streamIds.add(id)
		}
		for (entry in importer.items.asReversed()) {
			val id = acc.saved[entry] ?: continue
			if (entry.item.type != ItemKind.CHAPTER) continue
			recomputeContainerCounters(dao, sums.counts, id)
			for (parent in dao.parentsPlainFirst(id))
				if (parent != anchorId && parent !in streamIds)
					ItemRepository.recomputeCounterChain(dao, parent, sums.counts, sums.parents)
		}
		for (id in session.adopted) {
			if (dao.get(id)?.type != ItemKind.WORD) continue
			for (parent in dao.parentsPlainFirst(id))
				if (parent != anchorId && parent !in streamIds)
					ItemRepository.recomputeCounterChain(dao, parent, sums.counts, sums.parents)
		}
		// never the root — its recursive sum spans the whole database, its
		// counters show nowhere, and root-level subjects are stream chapters
		if (anchorId != ROOT_ID)
			ItemRepository.recomputeCounterChain(dao, anchorId, sums.counts, sums.parents)
	}

	/**
	 * Session-or-SQL pair for chain walks — in-memory sums for loaded rows, SQL for rest.
	 */
	private class SessionSums(session: ImportSession, dao: ItemDao) {
		val counts: suspend (Int) -> Counters =
			{ if (session.knows(it)) session.subgraphWordCounters(it) else dao.subgraphWordCounters(it) }
		val parents: suspend (Int) -> List<Int> =
			{ if (session.knows(it)) session.parentsOf(it) else dao.parentsPlainFirst(it) }
	}

	/**
	 * Stores container success/fail derived from word rows in subgraph.
	 * Incoming pick cannot diverge.
	 */
	private suspend fun recomputeContainerCounters(
		dao: ItemDao, counts: suspend (Int) -> Counters, containerId: Int,
	) {
		val fresh = counts(containerId)
		dao.updateCounters(containerId, fresh.passed, fresh.failed)
	}
}
