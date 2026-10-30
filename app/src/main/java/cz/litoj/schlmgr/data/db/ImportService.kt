package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.DataImporter
import cz.litoj.schlmgr.data.StreamItem
import cz.litoj.schlmgr.data.StreamReference

/** The outcome of one import, with the saved word and chapter totals. */
class ImportResult(
	/** Rows the stream roots became: subjects at root, anchor children below. */
	val topLevel: List<DbItem>,
	val words: Int,
	val chapters: Int,
)

/**
 * - three save phases over the accumulator and the session.
 * - stateless: the repository owns the transaction.
 */
internal object ImportService {

	/**
	 * - the subject merge reads the map for re-linking.
	 */
	internal class ImportOutcome(
		val result: ImportResult,
		val savedRows: Map<StreamItem, Int>,
	)

	suspend fun run(
		dao: ItemDao, importer: DataImporter, anchorId: Int,
	): ImportOutcome {
		requireWordsTranslated(importer)
		requireNamed(importer)
		val session = ImportSession.load(dao, anchorId)
		val acc = ImportAccumulator()
		saveImportStream(session, importer, anchorId, acc)
		resolveImportReferences(session, importer, acc)
		acc.remapIds(session.flush())
		refreshImportCounters(session, dao, importer, acc, anchorId)
		return ImportOutcome(
			ImportResult(acc.topLevel, acc.words, acc.chapters), acc.saved)
	}

	/**
	 * Stream shape valid: word children are named translations only, at least one.
	 * A violating file fails the whole import before anything is written.
	 */
	private fun requireWordsTranslated(importer: DataImporter) {
		val childrenOf = importer.items.groupBy({ it.parent }, { it.item })
		for (entry in importer.items) {
			if (entry.item.type != ItemKind.WORD) continue
			val children = childrenOf[entry] ?: emptyList()
			if (children.any { it.type != ItemKind.TRANSLATION }) throw IllegalArgumentException(
				"A word's children are its translations: '${entry.item.name}'")
			// a blank name carries no translation once trimmed
			if (children.none { it.name.trim().isNotEmpty() }) throw IllegalArgumentException(
				"A word without a translation: '${entry.item.name}' — make it a note instead")
		}
	}

	/**
	 * Every non-translation item carries a name.
	 * A violating file fails the whole import before anything is written.
	 * A translation may stay blank, since the save skips it.
	 */
	private fun requireNamed(importer: DataImporter) {
		for (entry in importer.items) {
			if (entry.item.type == ItemKind.TRANSLATION) continue
			if (entry.item.name.trim().isEmpty()) throw IllegalArgumentException(
				"An item without a name: '${entry.item.name}'")
		}
	}

	/**
	 * Save phase: every stream entry becomes a row linked under its parent.
	 * Entries with a null parent anchor to the import target.
	 */
	private suspend fun saveImportStream(
		session: ImportSession, importer: DataImporter, anchorId: Int, acc: ImportAccumulator,
	) {
		for (entry in importer.items) {
			// first, before the row work, since a skipped entry such as an empty
			// translation must not fall behind the total of the bar
			importer.onItem(entry)
			val parent = entry.parent?.let(acc.saved::get) ?: anchorId
			val row = when (entry.item.type) {
				ItemKind.WORD -> WordMerge.insertWord(session, InsertWordParams(parent, entry.item.name,
					entry.item.description, emptyList(),
					entry.item.passedTests, entry.item.failedTests))
				ItemKind.TRANSLATION -> WordMerge.addTranslations(session, AddTranslationsParams(
					session.subjectRootOf(parent) ?: parent, parent,
					listOf(NewTranslation(entry.item.name, entry.item.description,
						entry.item.passedTests, entry.item.failedTests)))).firstOrNull()
					?: continue // an empty name adds no row
				// a chapter and a note both save as containers
				else -> session.insertContainer(parent, entry.item)
			}
			acc.countRow(row.id, entry.item.type)
			acc.saved[entry] = row.id
			acc.indexSavedRow(parent, entry.item.name, row.id, entry.item.type)
			if (entry.parent == null) acc.topLevel.add(row)
		}
	}

	/**
	 * Reference phase, against saved rows.
	 * - path names its own root plus the containers below.
	 * - the target is the named row under the last.
	 * - an empty path names a root itself.
	 * - a path naming no saved root falls back to the first.
	 * - unresolvable references skipped.
	 */
	private suspend fun resolveImportReferences(
		session: ImportSession, importer: DataImporter, acc: ImportAccumulator,
	) {
		for (ref in importer.references) {
			val parentRow = acc.saved[ref.parent] ?: continue
			val target = resolveTargetRow(ref, acc) ?: continue
			// a file may name an ancestor of its own position, a cycle the pre-order
			// walks cannot leave. The session graph knows pending links, the database does not.
			if (parentRow != target && !session.linked(parentRow, target) &&
				!session.contains(target, parentRow))
				session.addReference(parentRow, target)
		}
	}

	private fun resolveTargetRow(ref: StreamReference, acc: ImportAccumulator): Int? {
		// an empty path names a root itself, so the target is a top-level row
		if (ref.path.isEmpty())
			return acc.topLevel.firstOrNull { it.name == ref.targetName }?.id
		// a multi-root file resolves through any of them, since the export
		// anchors each path at the root of the target
		var cur = acc.topLevel.firstOrNull { it.name == ref.path.first() }?.id
			?: acc.topLevel.firstOrNull()?.id ?: return null
		for (i in 1 until ref.path.size) {
			cur = acc.chaptersByParent[cur]?.get(ref.path[i]) ?: return null
		}
		return acc.rowsByParent[cur]?.get(ref.targetName)
	}

	/**
	 * Counter phase: every touched container derives counters from the words below.
	 * - bottom-up through the saved chapters, then the anchor and the containers above.
	 * - merged chapter holders outside the stream follow.
	 * - counter adoption touches the holders.
	 * - sums come from the session graph, and unloaded rows fall back to SQL.
	 */
	private suspend fun refreshImportCounters(
		session: ImportSession, dao: ItemDao, importer: DataImporter,
		acc: ImportAccumulator, anchorId: Int,
	) {
		val sums = SessionSums(session, dao)
		// built upfront, since the bottom-up walk meets stream parents before visiting them,
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
					CounterChain.recomputeCounterChain(dao, parent, sums.counts, sums.parents)
		}
		for (id in session.adopted) {
			if (dao.get(id)?.type != ItemKind.WORD) continue
			for (parent in dao.parentsPlainFirst(id))
				if (parent != anchorId && parent !in streamIds)
					CounterChain.recomputeCounterChain(dao, parent, sums.counts, sums.parents)
		}
		// never the root: its recursive sum spans the whole database, its counters
		// show nowhere, and root-level subjects are stream chapters
		if (anchorId != ROOT_ID)
			CounterChain.recomputeCounterChain(dao, anchorId, sums.counts, sums.parents)
	}

	/**
	 * Session-or-SQL pair for chain walks: in-memory sums for loaded rows, SQL for the rest.
	 */
	private class SessionSums(session: ImportSession, dao: ItemDao) {
		val counts: suspend (Int) -> Counters =
			{ if (session.knows(it)) session.subgraphWordCounters(it) else dao.subgraphWordCounters(it) }
		val parents: suspend (Int) -> List<Int> =
			{ if (session.knows(it)) session.parentsOf(it) else dao.parentsPlainFirst(it) }
	}

	/**
	 * The counters come from the rows below, so the incoming pick cannot diverge.
	 */
	private suspend fun recomputeContainerCounters(
		dao: ItemDao, counts: suspend (Int) -> Counters, containerId: Int,
	) {
		val fresh = counts(containerId)
		dao.updateCounters(containerId, fresh.passed, fresh.failed)
	}
}
