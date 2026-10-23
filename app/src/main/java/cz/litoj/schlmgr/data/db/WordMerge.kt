package cz.litoj.schlmgr.data.db

/**
 * Word and translation name merge rule over an [ItemStore].
 * - stateless: the caller owns the transaction.
 */
internal object WordMerge {

	/**
	 * Word-add merge rule for every item add.
	 * - the name trims first, since a spaced name is the same word.
	 * - same-named word in scope merges, new one otherwise.
	 */
	internal suspend fun insertWord(store: ItemStore, params: InsertWordParams): DbItem {
		val name = params.name.trim()
		val scope = store.subjectRootOf(params.parentId) ?: params.parentId
		val existing = store.findInSubject(scope, name, ItemKind.WORD)
		val word = if (existing != null) {
			store.mergeCounters(existing, params.passedTests, params.failedTests)
			store.mergeDescription(existing, params.description)
		} else store.insert(DbItem(
			name = name, description = params.description ?: "", type = ItemKind.WORD,
			passedTests = params.passedTests, failedTests = params.failedTests))
		if (!store.linked(params.parentId, word.id)) store.linkAtEnd(params.parentId, word.id)
		addTranslations(store, AddTranslationsParams(scope, word.id, params.translations))
		return word
	}

	/**
	 * Translation-add half of merge rule, for any store.
	 * - the name trims first, since a blank name is no translation.
	 * - same-named translation in scope merges, new one otherwise.
	 * - empty names skip.
	 * - rows return in the given order.
	 */
	internal suspend fun addTranslations(store: ItemStore, params: AddTranslationsParams): List<DbItem> {
		val word = store.row(params.wordId) ?: return emptyList()
		val rows = ArrayList<DbItem>(params.translations.size)
		for (translation in params.translations) {
			val name = translation.name.trim()
			if (name.isEmpty()) continue
			val existing = store.findInSubject(params.scope, name, ItemKind.TRANSLATION)
			val trl = if (existing != null) {
				// merge adopts the more-tested counters.
				store.mergeCounters(existing, translation.passedTests ?: word.passedTests,
					translation.failedTests ?: word.failedTests)
				store.mergeDescription(existing, translation.description)
			}
			else store.insert(DbItem(
				name = name, description = translation.description ?: "",
				type = ItemKind.TRANSLATION,
				passedTests = translation.passedTests ?: word.passedTests,
				failedTests = translation.failedTests ?: word.failedTests))
			if (!store.linked(params.wordId, trl.id)) store.linkAtEnd(params.wordId, trl.id)
			rows.add(trl)
		}
		return rows
	}
}
