package cz.litoj.schlmgr.testing

import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind

/** Pure answer rules, with every synonym supplied by the caller. */
internal class AnswerGrader(
	private val reversed: Boolean,
	private val ignoredMistakes: List<String> = emptyList(),
	private val parentsOf: (Int) -> List<DbItem> = { emptyList() },
) {

	fun normalize(text: String): String {
		var normalized = text
		for (mistake in ignoredMistakes) normalized = normalized.replace(mistake, "")
		return normalized.trim()
	}

	fun matchesAnswer(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
		if (answer.isNullOrEmpty()) return false
		return if (reversed) translations.any { matchesName(it.name, answer) }
		else matchesName(word.name, answer)
	}

	fun matchesEntry(word: DbItem, translations: List<DbItem>, answer: String?): Boolean {
		if (matchesAnswer(word, translations, answer)) return true
		if (reversed || answer.isNullOrEmpty() || translations.isEmpty()) return false
		return translations.all { translation ->
			parentsOf(translation.id).any { parent ->
				parent.type == ItemKind.WORD && matchesName(parent.name, answer)
			}
		}
	}

	fun matchesAllVariants(word: DbItem, translations: List<DbItem>, answers: List<String>): Boolean {
		if (answers.isEmpty()) return false
		val supplied = answers.map { it.trim() }
		val names = if (reversed) translations.map { it.name } else listOf(word.name)
		return names.all { WordNames.coversAllVariants(it, supplied, ::normalize) }
	}

	fun creditedTranslations(translations: List<DbItem>, answer: String?): List<DbItem> =
		if (reversed) translations.filter { matchesName(it.name, answer ?: "") } else translations

	private fun matchesName(name: String, answer: String): Boolean {
		val expected = normalize(name)
		val candidate = normalize(answer)
		return candidate == expected || WordNames.parseVariants(name.trim()).any {
			normalize(it) == candidate
		}
	}
}
