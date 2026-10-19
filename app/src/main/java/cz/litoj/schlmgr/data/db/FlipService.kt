package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.ItemRepository.FlipConflict
import cz.litoj.schlmgr.data.db.dao.ItemDao
import cz.litoj.schlmgr.data.db.dao.WordDao

/**
 * The flip state machine — words and translations swap roles, a chapter stands
 * for every word below it. Stateless — the repository owns the transaction.
 */
internal object FlipService {

	/**
	 * The words a flip would touch that also live outside the selection — a word
	 * (selected itself or under a selected chapter) whose plain links reach a
	 * chapter outside the acting parents and the selected subtrees.
	 * References never conflict — they follow the flip.
	 */
	suspend fun conflicts(dao: ItemDao, actingParents: Collection<Int>, ids: List<Int>): List<FlipConflict> {
		// the containers the flip selection covers — the acting ones plus every
		// selected chapter's subtree
		val covered = HashSet<Int>()
		covered.addAll(actingParents)
		val words = ArrayList<DbItem>()
		for (id in ids) {
			val item = dao.get(id) ?: continue
			when (item.type) {
				ItemKind.WORD -> words.add(item)
				ItemKind.CHAPTER -> for (row in dao.subgraph(id)) {
					covered.add(row.id)
					if (row.type == ItemKind.WORD) words.add(row)
				}
				else -> {}
			}
		}
		val out = ArrayList<FlipConflict>()
		val seen = HashSet<Int>()
		for (word in words) {
			if (!seen.add(word.id)) continue
			val outside = dao.plainParentsOf(word.id).filter { it !in covered }
			if (outside.isNotEmpty())
				out.add(FlipConflict(word, outside.mapNotNull { dao.get(it) }))
		}
		return out
	}

	suspend fun flipIds(dao: ItemDao, wordDao: WordDao, ids: List<Int>, rootId: Int) {
		// gather first, flip second.
		// A chapter's word set must come from the pre-flip state — an earlier flip's products would flip right back, and a word selected beside its chapter would flip twice.
		val targets = LinkedHashSet<DbItem>()
		for (id in ids) {
			val item = dao.get(id) ?: continue
			when (item.type) {
				ItemKind.WORD -> targets.add(item)
				// the same query [conflicts] validated the selection with — the
				// two traversals cannot disagree
				ItemKind.CHAPTER -> for (row in dao.subgraph(id))
					if (row.type == ItemKind.WORD) targets.add(row)
				else -> {}
			}
		}
		// every container whose word set the flip changes — the flipped words'
		// parents, plain and reference alike, plus the merged rows' holders
		val touched = HashSet<Int>()
		for (word in targets) {
			touched.addAll(dao.parentsOf(word.id))
			touched.addAll(flipWord(dao, wordDao, word, rootId))
		}
		for (container in touched)
			ItemRepository.recomputeCounterChain(dao, container, dao::subgraphWordCounters, dao::parentsPlainFirst)
	}

	suspend fun mergeWords(dao: ItemDao, from: Int, target: DbItem): DbItem {
		// both words' parents see their word sets change
		val touched = (dao.parentsOf(from) + dao.parentsOf(target.id)).distinct()
		val fromRow = dao.get(from)
		// a reference link stays one — a referencing chapter must not own plainly
		for (parent in dao.parentsOf(from))
			if (dao.linkPosition(parent, target.id) == null)
				dao.insertLink(DbLink(parent, target.id, dao.nextPosition(parent),
					isReference = dao.linkIsReference(parent, from) == true))
		// translations union under the name rule — a same-named translation
		// of the survivor merges (description joins, the more-tested
		// counters survive), no second row links
		val survivorTrls = dao.getChildren(target.id)
			.filter { it.type == ItemKind.TRANSLATION }.toMutableList()
		val orphans = ArrayList<DbItem>()
		for (trl in dao.getChildren(from).filter { it.type == ItemKind.TRANSLATION }) {
			val clash = survivorTrls.firstOrNull { it.name == trl.name }
			if (clash == null) {
				if (dao.linkPosition(target.id, trl.id) == null) {
					dao.insertLink(DbLink(target.id, trl.id, dao.nextPosition(target.id)))
					survivorTrls.add(trl)
				}
			} else if (clash.id != trl.id) {
				val merged = Descriptions.merge(clash.description, trl.description)
				if (merged != clash.description) dao.updateById(clash.id, clash.name, merged)
				if (trl.passedTests + trl.failedTests > clash.passedTests + clash.failedTests)
					dao.updateCounters(clash.id, trl.passedTests, trl.failedTests)
				// its link dies with the loser word below — the row itself
				// only when no other word holds it
				orphans.add(trl)
			}
		}
		val desc = fromRow?.description
		dao.deleteLinksTouching(from)
		dao.deleteById(from)
		for (orphan in orphans)
			if (dao.parentCount(orphan.id) == 0) dao.deleteById(orphan.id)
		val merged = Descriptions.merge(target.description, desc)
		if (merged != target.description) dao.updateById(target.id, target.name, merged)
		// the more-tested counters survive — the import merge's rule
		if (fromRow != null &&
			fromRow.passedTests + fromRow.failedTests > target.passedTests + target.failedTests)
			dao.updateCounters(target.id, fromRow.passedTests, fromRow.failedTests)
		for (container in touched)
			ItemRepository.recomputeCounterChain(dao, container, dao::subgraphWordCounters, dao::parentsPlainFirst)
		return dao.get(target.id)!!
	}

	/**
	 * Swaps one word with its translations, merging duplicates by subject
	 * name. Returns the containers of rows the merges changed beyond the
	 * word's own parents — their derivations follow the adopted counters.
	 */
	private suspend fun flipWord(
		dao: ItemDao, wordDao: WordDao, word: DbItem, rootId: Int,
	): List<Int> {
		val translations = dao.getChildren(word.id).filter { it.type == ItemKind.TRANSLATION }
		if (translations.isEmpty()) return emptyList()
		val scope = dao.subjectRootOf(word.id, rootId) ?: word.id
		val parents = dao.parentsOf(word.id)
		// the references follow the flip (below) — their chapters get the new
		// words as reference links. The plain links are the ones the new
		// words take over directly.
		val referenceParents = dao.referenceParentsOf(word.id)
		val referenceIds = referenceParents.mapTo(HashSet()) { it.id }
		// The flipped word becomes a translation — the subject's row of that (name,
		// translation) pair absorbs it, one row per pair per subject like the
		// import merge. The flipped translations are excluded from the search —
		// they leave the translation role below.
		val flippedIds = translations.mapTo(HashSet()) { it.id }
		val existingTrl = wordDao.findWordInSubject(scope, word.name, ItemKind.TRANSLATION)
			?.takeIf { it.id !in flippedIds && it.id != word.id }
		val trlTargetId = existingTrl?.id ?: word.id
		val newWords = ArrayList<Int>()
		val mergeTouched = ArrayList<Int>()

		for (trl in translations) {
			// a translation that already is a word of the subject absorbs the duplicate row
			val existing = wordDao.findWordInSubject(scope, trl.name)
				?.takeIf { it.id != trl.id && it.id != word.id }
			// the other words sharing this row keep a translation of this name — one
			// replacement row serves them all. It starts at zero counters — the row's
			// stats stay with the flipped row, and one named like the flipped word
			// already got its row above
			val sharers = dao.parentsOf(trl.id).filter { it != word.id }
			if (sharers.isNotEmpty()) {
				val replacement = if (trl.name == word.name) trlTargetId else dao.insert(
					DbItem(name = trl.name, description = trl.description, type = ItemKind.TRANSLATION)
				).toInt()
				for (sharer in sharers) {
					// read the kind before the delete removes the evidence
					val wasReference = dao.linkIsReference(sharer, trl.id) == true
					dao.deleteLink(sharer, trl.id)
					// a reference link stays one — a referencing chapter must
					// not own the replacement plainly
					if (dao.linkPosition(sharer, replacement) == null)
						dao.insertLink(DbLink(sharer, replacement, dao.nextPosition(sharer),
							isReference = wasReference))
				}
			}
			val newWordId = if (existing != null) {
				mergeFlipRow(dao, existing, trl)
				mergeTouched.addAll(dao.parentsOf(existing.id))
				existing.id
			} else {
				dao.updateType(trl.id, ItemKind.WORD)
				// the word→translation link must go — kept, it would cycle with the
				// translation→word link created below
				dao.deleteLink(word.id, trl.id)
				trl.id
			}
			// the new word takes over the flipped word's plain containers
			for (parent in parents)
				if (parent !in referenceIds && dao.linkPosition(parent, newWordId) == null)
					dao.insertLink(DbLink(parent, newWordId, dao.nextPosition(parent)))
			// the flipped word's translation role lands on the subject's row of the name
			if (dao.linkPosition(newWordId, trlTargetId) == null)
				dao.insertLink(DbLink(newWordId, trlTargetId, dao.nextPosition(newWordId)))
			newWords.add(newWordId)
		}
		// the flipped word leaves the containers — it is a translation now
		for (parent in parents) dao.deleteLink(parent, word.id)
		// the references follow the flip before the merge below may delete
		// the flipped row and its links outright — each referencing chapter shows
		// the new words, still as references
		for (ref in referenceParents)
			for (newWord in newWords)
				if (dao.linkPosition(ref.id, newWord) == null)
					dao.insertLink(DbLink(ref.id, newWord, dao.nextPosition(ref.id), isReference = true))
		if (existingTrl != null) {
			mergeFlipRow(dao, existingTrl, word)
		} else {
			dao.updateType(word.id, ItemKind.TRANSLATION)
		}
		return mergeTouched
	}

	/**
	 * One flip's name collision — [loser] joins [survivor] — the description
	 * merges and the more-tested counters survive (every merge site's rule),
	 * then the loser row is deleted.
	 */
	private suspend fun mergeFlipRow(dao: ItemDao, survivor: DbItem, loser: DbItem) {
		val merged = Descriptions.merge(survivor.description, loser.description)
		if (merged != survivor.description) dao.updateById(survivor.id, survivor.name, merged)
		if (loser.passedTests + loser.failedTests > survivor.passedTests + survivor.failedTests)
			dao.updateCounters(survivor.id, loser.passedTests, loser.failedTests)
		dao.deleteLinksTouching(loser.id)
		dao.deleteById(loser.id)
	}
}
