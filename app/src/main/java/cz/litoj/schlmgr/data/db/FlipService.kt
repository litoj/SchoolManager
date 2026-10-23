package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * The flip state machine, where words and translations swap roles.
 * - a chapter stands for every word below it.
 * - the conflict loop runs first and repeats until no relation spans the flip set.
 * - each spanning relation asks the user to remove, detach a copy, or keep it.
 * - a mutual pair trades places instead of running through the merges.
 * - stateless, the repository owns the transaction.
 */
internal object FlipService {

	/**
	 * Starts a flip run with the plan and the first conflict round.
	 * - chapters stand for every word below them, as in [flipIds].
	 * - one [FlipScope] read holds every row and link the gathering and analysis need.
	 */
	suspend fun start(dao: ItemDao, actingParents: Collection<Int>, ids: List<Int>): FlipAnalysis {
		val scope = FlipScope.of(dao, ids)
		// a linked flip set keeps the selection order, so popup rows stay stable across rounds
		val plan = FlipPlan(LinkedHashSet(), HashSet(), HashMap(), LinkedHashSet())
		plan.seeds.addAll(ids)
		plan.covered.addAll(actingParents)
		for (id in ids) {
			val item = scope.row(id) ?: continue
			when (item.type) {
				ItemKind.WORD -> plan.flipSet.add(item.id)
				ItemKind.CHAPTER -> for (row in scope.subtree(id)) {
					plan.covered.add(row.id)
					if (row.type == ItemKind.WORD) plan.flipSet.add(row.id)
				}
				else -> {}
			}
		}
		return FlipAnalysis(plan, analyze(scope, plan))
	}

	/**
	 * The relations that still span the flip set and the outside, one popup row each.
	 * - a set word held by containers outside the covered range would change them too.
	 * - a translation of a set word held by outside words loses its sharing to a copy.
	 * - overridden rows are already detached, so their relations count as resolved.
	 */
	private fun analyze(scope: FlipScope, plan: FlipPlan): List<FlipConflict> {
		val out = ArrayList<FlipConflict>()
		val seenTrls = HashSet<Int>()
		for (wordId in plan.flipSet) {
			val word = scope.row(wordId) ?: continue
			if (word.id !in plan.overrides) {
				val outside = scope.parents(wordId).filter { it !in plan.covered }
				if (outside.isNotEmpty())
					out.add(FlipConflict(word, outside.mapNotNull(scope::row), listOf(word)))
			}
			for (trl in scope.children(wordId).filter { it.type == ItemKind.TRANSLATION }) {
				if (!seenTrls.add(trl.id) || trl.id in plan.overrides) continue
				// a chapter keeps the row through the flip, so only words count as holders
				val holders = scope.parentRows(trl.id).filter { it.type == ItemKind.WORD }
				val scheduled = holders.filter { it.id in plan.flipSet }
				val outside = holders.filter { it.id !in plan.flipSet }
				if (scheduled.isNotEmpty() && outside.isNotEmpty())
					out.add(FlipConflict(trl, outside, scheduled))
			}
		}
		return out
	}

	/**
	 * Applies one popup round and re-analyzes the mutated plan.
	 * - resolutions pair with the round conflicts by row.
	 * - a remove drops the words linked to the row from the flip set.
	 * - an edit records a custom version, so the flip copies the row and the other side keeps it.
	 * - an untouched word keeps the relation, so its outside links join the covered range.
	 * - an untouched translation keeps it too, so its outside words join the flip set.
	 */
	suspend fun advance(dao: ItemDao, analysis: FlipAnalysis, resolutions: List<FlipResolution>): FlipAnalysis {
		val plan = analysis.plan
		val byRow = HashMap<Int, FlipConflict>()
		for (conflict in analysis.conflicts) byRow[conflict.row.id] = conflict
		for (resolution in resolutions) {
			val conflict = byRow[resolution.rowId] ?: continue
			when {
				resolution.removed -> plan.flipSet.removeAll(conflict.scheduled.map { it.id }.toSet())
				resolution.name != conflict.row.name || resolution.description != conflict.row.description ->
					plan.overrides[conflict.row.id] = FlipOverride(resolution.name, resolution.description)
				conflict.row.type == ItemKind.WORD ->
					plan.covered.addAll(conflict.outside.map { it.id })
				else -> {
					// kept outside words must flip too, so the read covers their structure
					plan.seeds.addAll(conflict.outside.map { it.id })
					plan.flipSet.addAll(conflict.outside.map { it.id })
				}
			}
		}
		return FlipAnalysis(plan, analyze(FlipScope.of(dao, plan.seeds), plan))
	}

	/**
	 * Runs the resolved plan inside the transaction of the caller.
	 * - custom versions land before the flip, so the flip works on the copy.
	 */
	suspend fun execute(dao: ItemDao, plan: FlipPlan) {
		for ((rowId, override) in plan.overrides) {
			val row = dao.get(rowId) ?: continue
			if (row.type == ItemKind.WORD) forkWord(dao, plan, row, override)
			else copyTranslation(dao, plan, row, override)
		}
		flipIds(dao, plan.flipSet.toList())
	}

	/**
	 * The custom version of a word, a raw copy that takes over the covered links.
	 * - plain and reference links alike, slots preserved.
	 * - translations stay shared, so no side loses them.
	 * - the copy flips in place of the word, the word keeps its outside life.
	 * - it lives only inside the transaction, then becomes a translation.
	 */
	private suspend fun forkWord(dao: ItemDao, plan: FlipPlan, word: DbItem, override: FlipOverride) {
		// a later round may have dropped the word, its fork would flip for nothing
		if (word.id !in plan.flipSet) return
		val fork = dao.insert(DbItem(name = override.name.trim(), description = override.description,
			type = ItemKind.WORD, passedTests = word.passedTests, failedTests = word.failedTests)).toInt()
		for (trl in dao.getChildren(word.id).filter { it.type == ItemKind.TRANSLATION })
			dao.insertLink(DbLink(fork, trl.id, dao.nextPosition(fork)))
		for (parent in dao.parentsOf(word.id)) {
			if (parent !in plan.covered) continue
			val wasReference = dao.linkIsReference(parent, word.id) == true
			val slot = dao.linkPosition(parent, word.id)
			dao.deleteLink(parent, word.id)
			dao.insertLink(DbLink(parent, fork, slot ?: dao.nextPosition(parent), isReference = wasReference))
		}
		plan.flipSet.remove(word.id)
		plan.flipSet.add(fork)
	}

	/**
	 * The custom version of a translation, a raw copy with the edited contents.
	 * - it takes over the links from scheduled words, outside words keep the row.
	 * - the copy flips in place of the row.
	 */
	private suspend fun copyTranslation(dao: ItemDao, plan: FlipPlan, trl: DbItem, override: FlipOverride) {
		val holders = dao.parentsOf(trl.id).filter { it in plan.flipSet }
		if (holders.isEmpty()) return
		val copy = dao.insert(DbItem(name = override.name.trim(), description = override.description,
			type = ItemKind.TRANSLATION, passedTests = trl.passedTests, failedTests = trl.failedTests)).toInt()
		for (holder in holders) {
			val slot = dao.linkPosition(holder, trl.id)
			dao.deleteLink(holder, trl.id)
			dao.insertLink(DbLink(holder, copy, slot ?: dao.nextPosition(holder)))
		}
	}

	/**
	 * Flips the targets of the selection in dependency order.
	 * - targets are each selected word and every word of a selected chapter.
	 * - one [FlipScope] read serves the whole gathering.
	 */
	suspend fun flipIds(dao: ItemDao, ids: List<Int>) {
		// gather first, flip second, so chapter words come from the pre-flip state:
		// - products of an earlier flip would flip right back.
		// - a word selected beside its chapter would flip twice.
		val scope = FlipScope.of(dao, ids)
		val targets = LinkedHashSet<DbItem>()
		for (id in ids) {
			val item = scope.row(id) ?: continue
			when (item.type) {
				ItemKind.WORD -> targets.add(item)
				ItemKind.CHAPTER -> for (row in scope.subtree(id))
					if (row.type == ItemKind.WORD) targets.add(row)
				else -> {}
			}
		}
		// every container whose word set the flip changes:
		// - parents of flipped words, plain and reference alike.
		// - holders of merged rows.
		val touched = HashSet<Int>()
		// mutual pairs leave the machinery, they flip by trading places
		for (word in rewireMutuals(dao, scope, targets))
			touched.addAll(dao.parentsOf(word.id))
		for (word in flipOrder(scope, targets)) {
			touched.addAll(dao.parentsOf(word.id))
			touched.addAll(flipWord(dao, scope, word))
		}
		for (container in touched)
			CounterChain.recomputeCounterChain(dao, container, dao::subgraphWordCounters, dao::parentsPlainFirst)
	}

	/**
	 * The mutual pairs among the targets, where each pair flips by trading places.
	 * - a pair holds exactly the name of the other as its single translation.
	 * - pairs leave [targets], the merges never run over them.
	 * - returns the reworded words.
	 */
	private suspend fun rewireMutuals(dao: ItemDao, scope: FlipScope, targets: LinkedHashSet<DbItem>): List<DbItem> {
		val byName = HashMap<String, MutableList<DbItem>>()
		for (word in targets) byName.getOrPut(word.name) { ArrayList() }.add(word)
		val rewired = ArrayList<DbItem>()
		val handled = HashSet<Int>()
		for (word in targets) {
			if (word.id in handled) continue
			val partner = mutualPartner(scope, byName, word) ?: continue
			handled.add(word.id)
			handled.add(partner.id)
			rewirePair(dao, word, partner)
			rewired.add(word)
			rewired.add(partner)
		}
		for (word in rewired) targets.remove(word)
		return rewired
	}

	/**
	 * The one mutual partner of a word within the targets, or null when the pair is not exact.
	 * - each word carries the name of the other as its single translation.
	 * - both share one subject, because the trade moves plain links and stays inside it.
	 * - extra translations, same-named candidates or another subject fall back to the merges.
	 */
	private suspend fun mutualPartner(
		scope: FlipScope,
		byName: Map<String, List<DbItem>>,
		word: DbItem,
	): DbItem? {
		val trls = scope.children(word.id).filter { it.type == ItemKind.TRANSLATION }
		if (trls.size != 1) return null
		val candidates = byName[trls.single().name] ?: return null
		if (candidates.size != 1) return null
		val partner = candidates.single()
		if (partner.id == word.id) return null
		val partnerTrls = scope.children(partner.id).filter { it.type == ItemKind.TRANSLATION }
		if (partnerTrls.size != 1 || partnerTrls.single().name != word.name) return null
		// a failed walk answers nothing. Two unknown subjects are not one
		val subject = scope.subjectRootOf(word.id) ?: return null
		if (subject != scope.subjectRootOf(partner.id)) return null
		return partner
	}

	/**
	 * The flip of one mutual pair, done by trading places.
	 * - the rows trade parent links, with slots and link kinds preserved.
	 * - they trade descriptions and success rates, names and translations stay.
	 * - each chapter keeps its pair with the roles traded, no row dies, no merge runs.
	 */
	private suspend fun rewirePair(dao: ItemDao, a: DbItem, b: DbItem) {
		val aHolds = dao.linksInto(a.id)
		val bHolds = dao.linksInto(b.id)
		for (link in aHolds) dao.deleteLink(link.parentId, a.id)
		for (link in bHolds) dao.deleteLink(link.parentId, b.id)
		for (link in aHolds) dao.insertLink(DbLink(link.parentId, b.id, link.position, link.isReference))
		for (link in bHolds) dao.insertLink(DbLink(link.parentId, a.id, link.position, link.isReference))
		// descriptions and counters trade with the places, so each chapter slot
		// keeps its own data under the swapped name
		dao.updateById(a.id, a.name, b.description)
		dao.updateById(b.id, b.name, a.description)
		dao.updateCounters(a.id, b.passedTests, b.failedTests)
		dao.updateCounters(b.id, a.passedTests, a.failedTests)
	}

	suspend fun mergeWords(dao: ItemDao, from: Int, target: DbItem): DbItem {
		// the parents of both words see their word sets change
		val touched = (dao.parentsOf(from) + dao.parentsOf(target.id)).distinct()
		val fromRow = dao.get(from)
		// a reference link stays one, a referencing chapter must not gain a plain link
		for (parent in dao.parentsOf(from))
			if (dao.linkPosition(parent, target.id) == null)
				dao.insertLink(DbLink(parent, target.id, dao.nextPosition(parent),
					isReference = dao.linkIsReference(parent, from) == true))
		// translations union under the name rule, a same-named translation
		// of the survivor merges, so no second row links
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
				// its link dies with the loser word below, the row itself only
				// when no other word holds it
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
		// the more-tested counters survive, as in the import merge
		if (fromRow != null &&
			fromRow.passedTests + fromRow.failedTests > target.passedTests + target.failedTests)
			dao.updateCounters(target.id, fromRow.passedTests, fromRow.failedTests)
		for (container in touched)
			CounterChain.recomputeCounterChain(dao, container, dao::subgraphWordCounters, dao::parentsPlainFirst)
		return dao.get(target.id)!!
	}

	/**
	 * Swaps one word with its translations, merging duplicates by subject name.
	 * - structural reads stay live because earlier writes change them.
	 * - name lookups and subject roots read through [scope].
	 * - returns merge-touched containers beyond the word parents, their counters follow.
	 */
	private suspend fun flipWord(
		dao: ItemDao, scope: FlipScope, word: DbItem,
	): List<Int> {
		val translations = dao.getChildren(word.id).filter { it.type == ItemKind.TRANSLATION }
		if (translations.isEmpty()) return emptyList()
		val subjectRoot = scope.subjectRootOf(word.id) ?: word.id
		val parents = dao.parentsOf(word.id)
		// references follow the flip, so their chapters get the new words as
		// reference links, while the new words take over the plain links
		val referenceParents = dao.referenceParentsOf(word.id)
		val referenceIds = referenceParents.mapTo(HashSet()) { it.id }
		// the flipped word becomes a translation, absorbed by the subject row of the name:
		// - one row per name pair per subject, as in the import merge.
		// - flipped translations are excluded, they leave the role below.
		val flippedIds = translations.mapTo(HashSet()) { it.id }
		val existingTrl = scope.wordInSubject(subjectRoot, word.name, ItemKind.TRANSLATION)
			?.takeIf { it.id !in flippedIds && it.id != word.id }
		val trlTargetId = existingTrl?.id ?: word.id
		val newWords = ArrayList<Int>()
		val mergeTouched = ArrayList<Int>()

		for (trl in translations) {
			// the row that flips is the original, or a copy when other words share it:
			// - sharers keep the row untouched, so an outside sharer loses nothing.
			// - a scheduled sharer flip merges the kept row into the copy word.
			var flipRow = trl
			val sharers = dao.parentsOf(trl.id).filter { it != word.id }
			if (sharers.isNotEmpty() && trl.name != word.name) {
				val slot = dao.linkPosition(word.id, trl.id)
				val copy = dao.insert(DbItem(name = trl.name, description = trl.description,
					type = ItemKind.TRANSLATION,
					passedTests = trl.passedTests, failedTests = trl.failedTests)).toInt()
				dao.deleteLink(word.id, trl.id)
				dao.insertLink(DbLink(word.id, copy, slot ?: dao.nextPosition(word.id)))
				flipRow = dao.get(copy)!!
				scope.inserted(flipRow)
			} else if (sharers.isNotEmpty()) {
				// the row name matches the word name, so the sharers link the row
				// and the word itself becomes below, no copy splits the pair
				for (sharer in sharers) {
					// read the kind before the delete removes the evidence
					val wasReference = dao.linkIsReference(sharer, trl.id) == true
					dao.deleteLink(sharer, trl.id)
					// a reference link stays one, so a chapter must not own the replacement
					if (dao.linkPosition(sharer, trlTargetId) == null)
						dao.insertLink(DbLink(sharer, trlTargetId, dao.nextPosition(sharer),
							isReference = wasReference))
				}
			}
			// a translation that already exists as a word of the subject merges into it
			val existing = scope.wordInSubject(subjectRoot, flipRow.name, ItemKind.WORD)
				?.takeIf { it.id != flipRow.id && it.id != word.id }
			val newWordId = if (existing != null) {
				mergeFlipRow(dao, existing, flipRow)
				mergeTouched.addAll(dao.parentsOf(existing.id))
				existing.id
			} else {
				dao.updateType(flipRow.id, ItemKind.WORD)
				// the word to translation link must go, kept it would cycle
				// with the translation to word link created below
				dao.deleteLink(word.id, flipRow.id)
				flipRow.id
			}
			// keep the chapter slots of the flipped word, so the new word takes the plain links
			for (parent in parents)
				if (parent !in referenceIds && dao.linkPosition(parent, newWordId) == null)
					dao.insertLink(DbLink(parent, newWordId, dao.nextPosition(parent)))
			// one name pair per subject, so the flipped word links the subject row of the name
			if (dao.linkPosition(newWordId, trlTargetId) == null)
				dao.insertLink(DbLink(newWordId, trlTargetId, dao.nextPosition(newWordId)))
			newWords.add(newWordId)
		}
		// the flipped word leaves its containers because it becomes a translation
		for (parent in parents) dao.deleteLink(parent, word.id)
		// references follow the flip before the merge deletes the flipped row,
		// so each referencing chapter still shows the new words as references
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
	 * The flip order of the gathered targets, Kahn over the name waits.
	 * - a word waits for any target whose name one of its translations carries.
	 * - else the owner of that name absorbs the translation and flips the fresh row back.
	 * - leftovers keep the gathering order, cycles left by the pair rewire merge in any order.
	 */
	private fun flipOrder(scope: FlipScope, targets: LinkedHashSet<DbItem>): List<DbItem> {
		val byId = HashMap<Int, DbItem>()
		val byName = HashMap<String, MutableList<DbItem>>()
		for (word in targets) {
			byId[word.id] = word
			// a corrupted subject can hold several target rows of one name, every
			// one of them delays the words that name it
			byName.getOrPut(word.name) { ArrayList() }.add(word)
		}
		val waitsFor = HashMap<Int, HashSet<Int>>()
		val waitedBy = HashMap<Int, MutableList<Int>>()
		for (word in targets)
			for (name in scope.translationNames(word.id))
				for (other in byName[name] ?: continue)
					if (other.id != word.id) {
						waitsFor.getOrPut(word.id) { HashSet() }.add(other.id)
						waitedBy.getOrPut(other.id) { ArrayList() }.add(word.id)
					}
		// seed the queue in gathering order, so words without waits keep the selection order
		val waitsLeft = HashMap<Int, Int>()
		val queue = ArrayDeque<DbItem>()
		for (word in targets) {
			val waits = waitsFor[word.id]?.size ?: 0
			waitsLeft[word.id] = waits
			if (waits == 0) queue.add(word)
		}
		val out = ArrayList<DbItem>(targets.size)
		val done = HashSet<Int>()
		while (queue.isNotEmpty()) {
			val word = queue.removeFirst()
			out.add(word)
			done.add(word.id)
			for (waiter in waitedBy[word.id] ?: continue)
				if (waitsLeft.merge(waiter, -1, Int::plus) == 0) queue.add(byId[waiter]!!)
		}
		for (word in targets) if (word.id !in done) out.add(word)
		return out
	}

	/**
	 * A name collision of one flip, the loser joins the survivor.
	 * - descriptions merge and the more-tested counters survive, as at every merge site.
	 * - the loser row is deleted at the end.
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
