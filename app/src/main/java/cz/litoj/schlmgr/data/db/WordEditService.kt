package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * The committed word edit: in-place edits, rename joins and forks of shared words.
 * - a fork splits a shared word that was renamed and lost a translation.
 * - the continuing word takes the kept rows, the additions and the references.
 * - the original keeps the removed translations and its other chapters.
 * - stateless, the repository owns the transaction.
 */
internal object WordEditService {

	/** The rename rule behind the repository entry. */
	suspend fun rename(dao: ItemDao, id: Int, name: String): DbItem {
		val item = dao.get(id) ?: throw IllegalArgumentException("No item $id")
		// the same trim as creation, so the merge lookup and the stored name
		// see the trimmed form
		val trimmed = name.trim()
		// a blank name cannot be stored, since no export format round-trips it
		require(trimmed.isNotEmpty()) { "A name cannot be blank" }
		if (item.type == ItemKind.WORD) {
			val scope = dao.subjectRootOf(id, ROOT_ID) ?: id
			val target = dao.findWordInSubject(scope, trimmed)
			if (target != null && target.id != id) return FlipService.mergeWords(dao, id, target)
		}
		dao.updateById(id, trimmed, item.description)
		return dao.get(id)!!
	}

	/**
	 * Whether the edit forks, when a shared word is renamed and loses a translation.
	 * - the word is shared when a chapter holds it through a plain link.
	 * - the rename licenses the fork, which avoids a duplicate row.
	 */
	suspend fun forks(dao: ItemDao, edit: WordEdit): Boolean {
		val word = dao.get(edit.wordId) ?: throw IllegalArgumentException("No item ${edit.wordId}")
		val plainParents = dao.plainParentsOf(edit.wordId)
		return edit.parentId in plainParents && plainParents.any { it != edit.parentId } &&
			edit.removed.isNotEmpty() && edit.name != word.name
	}

	/** Applies a committed word edit, behind the repository entry. */
	suspend fun edit(dao: ItemDao, edit: WordEdit): DbItem {
		// a blank name cannot be stored, since no export format round-trips it
		require(edit.name.isNotBlank()) { "A word needs a name" }
		// name content counts, so empty-named additions land nothing
		if (edit.kept.isEmpty() && edit.additions.none { it.first.trim().isNotEmpty() })
			throw IllegalArgumentException("A word without a translation: '${edit.name}'")
		dao.get(edit.wordId) ?: throw IllegalArgumentException("No item ${edit.wordId}")
		val scope = dao.subjectRootOf(edit.wordId, ROOT_ID) ?: edit.wordId
		val keptRows = edit.kept.map { kept ->
			val current = dao.get(kept.id) ?: throw IllegalArgumentException("No item ${kept.id}")
			kept to current
		}
		// rows kept renames merged into, so later same-name rows join
		val joinedTargets = HashSet<Int>()
		if (forks(dao, edit)) return forkWord(dao, edit, keptRows, scope, joinedTargets)
		// in-place half, with removals first to avoid carrying them over
		for (trl in edit.removed) {
			dao.deleteLink(edit.wordId, trl)
			if (dao.parentCount(trl) == 0) GraphDelete.delete(dao, trl)
		}
		for ((kept, current) in keptRows)
			applyKeptTranslation(dao, scope, kept, current, edit.wordId, edit.wordId, null, joinedTargets)
		val merged = rename(dao, edit.wordId, edit.name)
		// a rename-join carries the deleted description over, and the editor value joins
		val desc = if (merged.id != edit.wordId) Descriptions.merge(merged.description, edit.description)
		else edit.description ?: ""
		dao.updateById(merged.id, merged.name, desc)
		WordMerge.insertWord(DaoItemStore(dao), InsertWordParams(edit.parentId, edit.name, desc,
			edit.additions.map { NewTranslation(it.first, it.second) }))
		return dao.get(merged.id)!!
	}

	/**
	 * Fork half of the edit.
	 * - the continuing word takes the original slot, the kept rows and the additions.
	 * - its references follow it.
	 * - the original keeps the removed translations, other chapters and counters.
	 */
	private suspend fun forkWord(
		dao: ItemDao, edit: WordEdit,
		keptRows: List<Pair<KeptTranslation, DbItem>>, scope: Int, joinedTargets: MutableSet<Int>,
	): DbItem {
		val slot = dao.linkPosition(edit.parentId, edit.wordId)
		val existing = dao.findWordInSubject(scope, edit.name)?.takeIf { it.id != edit.wordId }
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
		// editor order indexes a fresh list, so joined words append after the existing ones
		val base = if (existing != null)
			dao.getChildren(fork.id).count { it.type == ItemKind.TRANSLATION } else 0
		for ((index, row) in keptRows.withIndex())
			applyKeptTranslation(dao, scope, row.first, row.second, edit.wordId, fork.id, base + index, joinedTargets)
		WordMerge.addTranslations(DaoItemStore(dao), AddTranslationsParams(scope, fork.id,
			edit.additions.map { NewTranslation(it.first, it.second) }))
		// kept references follow the fork, removed ones keep the original.
		for (parent in edit.referencesToFork) {
			if (dao.linkIsReference(parent, edit.wordId) != true) continue
			dao.deleteLink(parent, edit.wordId)
			if (dao.linkPosition(parent, fork.id) == null)
				dao.insertLink(DbLink(parent, fork.id, dao.nextPosition(parent), isReference = true))
		}
		// the fork changes the word set of the edited chapter, so the containers above recompute
		CounterChain.recomputeCounterChain(dao, edit.parentId, dao::subgraphWordCounters, dao::parentsPlainFirst)
		return dao.get(fork.id)!!
	}

	/**
	 * Applies one kept row.
	 * - a rename onto an existing translation joins that row.
	 * - shared rows stay, and the edited word gets its own row.
	 * - joined descriptions merge.
	 */
	private suspend fun applyKeptTranslation(
		dao: ItemDao, scope: Int,
		kept: KeptTranslation, current: DbItem,
		oldOwnerId: Int, newOwnerId: Int, position: Int?, joinedTargets: MutableSet<Int>,
	) {
		var target = current.id
		if (kept.name == current.name) {
			// a sibling rename may have merged here, so the kept text joins the live content
			val live = dao.get(current.id)!!
			val desc = if (current.id in joinedTargets) Descriptions.merge(live.description, kept.description)
			else kept.description
			if (desc != live.description)
				dao.updateById(current.id, current.name, desc)
		} else {
			val existing = dao.findWordInSubject(scope, kept.name, ItemKind.TRANSLATION)
			if (existing != null && existing.id != current.id) {
				val desc = Descriptions.merge(
					Descriptions.merge(existing.description, current.description), kept.description)
				if (desc != existing.description) dao.updateById(existing.id, existing.name, desc)
				// merge adopts the more-tested counters.
				if (current.passedTests + current.failedTests > existing.passedTests + existing.failedTests)
					dao.updateCounters(existing.id, current.passedTests, current.failedTests)
				joinedTargets.add(existing.id)
				target = existing.id
			} else if (dao.parentCount(current.id) > 1) {
				// a replacement starts at zero counters.
				target = dao.insert(
					DbItem(name = kept.name, description = kept.description, type = ItemKind.TRANSLATION))
					.let { dao.get(it.toInt())!! }.id
			} else {
				dao.updateById(current.id, kept.name, kept.description)
			}
		}
		// the row leaves the previous word on fork, join or shared replacement.
		if (oldOwnerId != newOwnerId || target != current.id) {
			dao.deleteLink(oldOwnerId, current.id)
			if (target != current.id && dao.parentCount(current.id) == 0) GraphDelete.delete(dao, current.id)
		}
		if (dao.linkPosition(newOwnerId, target) == null) LinkOrder.linkAt(dao, newOwnerId, target, position)
	}
}
