package cz.litoj.schlmgr.testing

/** One answer aligned to its nearest target with display marks and edit distance. */
data class AnswerComparison(
	val target: String,
	val distance: Int,
	val parts: List<AnswerPart>,
) {
	companion object {

		/** Equal distances keep target order, so variant selection stays deterministic. */
		@JvmStatic
		fun closest(answer: String, targets: List<String>): AnswerComparison {
			var best: AnswerComparison? = null
			for (target in targets) {
				val candidate = compare(answer, target)
				if (best == null || candidate.distance < best.distance) best = candidate
			}
			return best ?: compare(answer, "")
		}

		/**
		 * Levenshtein backtracking prefers matches, substitutions and deletions in that order.
		 * Substitutions emit the typed character before its missing replacement.
		 */
		@JvmStatic
		fun compare(answer: String, target: String): AnswerComparison {
			val costs = Array(answer.length + 1) { IntArray(target.length + 1) }
			for (i in 0..answer.length) costs[i][0] = i
			for (j in 1..target.length) costs[0][j] = j
			for (i in 1..answer.length) for (j in 1..target.length) {
				val substitution = costs[i - 1][j - 1] + if (answer[i - 1] == target[j - 1]) 0 else 1
				costs[i][j] = minOf(costs[i - 1][j] + 1, costs[i][j - 1] + 1, substitution)
			}

			val reversed = ArrayList<AnswerPart>(answer.length + target.length)
			var i = answer.length
			var j = target.length
			while (i > 0 || j > 0) when {
				i > 0 && j > 0 && answer[i - 1] == target[j - 1]
					&& costs[i][j] == costs[i - 1][j - 1] -> {
					reversed.add(AnswerPart(answer[i - 1].toString(), AnswerMark.CORRECT))
					i--
					j--
				}
				i > 0 && j > 0 && costs[i][j] == costs[i - 1][j - 1] + 1 -> {
					reversed.add(AnswerPart(target[j - 1].toString(), AnswerMark.MISSING))
					reversed.add(AnswerPart(answer[i - 1].toString(), AnswerMark.EXTRA))
					i--
					j--
				}
				i > 0 && costs[i][j] == costs[i - 1][j] + 1 -> {
					reversed.add(AnswerPart(answer[i - 1].toString(), AnswerMark.EXTRA))
					i--
				}
				else -> {
					reversed.add(AnswerPart(target[j - 1].toString(), AnswerMark.MISSING))
					j--
				}
			}
			return AnswerComparison(
				target,
				costs[answer.length][target.length],
				merge(reversed.asReversed()),
			)
		}

		private fun merge(characters: List<AnswerPart>): List<AnswerPart> {
			val parts = ArrayList<AnswerPart>()
			for (part in characters) {
				val previous = parts.lastOrNull()
				if (previous?.mark == part.mark) {
					parts[parts.lastIndex] = previous.copy(text = previous.text + part.text)
				} else parts.add(part)
			}
			return parts
		}
	}
}

data class AnswerPart(val text: String, val mark: AnswerMark)

enum class AnswerMark { CORRECT, MISSING, EXTRA }
