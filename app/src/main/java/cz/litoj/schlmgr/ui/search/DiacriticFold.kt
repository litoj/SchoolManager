package cz.litoj.schlmgr.ui.search

import java.text.Normalizer
import java.util.regex.Pattern

/**
 * The diacritic fold of the search, which a settings toggle switches on.
 * - a mark-free text stays as it is and the case stays untouched.
 */
internal object DiacriticFold {

	private val marks = Pattern.compile("\\p{M}+")

	/** The text without its combining marks, a plain ASCII text returns unchanged. */
	fun of(text: String): String {
		if (text.none { it.code > 127 }) return text
		return marks.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
	}
}
