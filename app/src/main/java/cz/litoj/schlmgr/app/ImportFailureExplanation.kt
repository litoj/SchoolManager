package cz.litoj.schlmgr.app

import android.content.Context
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.wordlist.WordListParseException
import cz.litoj.schlmgr.data.wordlist.WordListPlaceholder
import cz.litoj.schlmgr.data.wordlist.WordListProblem
import cz.litoj.schlmgr.data.wordlist.WordListRepair
import cz.litoj.schlmgr.data.wordlist.WordListRepairKind

/** Builds the localized source context, diagnosis and confirmed fixes for the popup. */
internal object ImportFailureExplanation {
	data class Content(
		val location: String,
		val pointer: String,
		val source: String,
		val guidance: String,
	) {
		val text: String get() = "$location\n\n$pointer\n$source\n\n$guidance"
	}

	/** One sentence per confirmed fix, the most likely first. */
	private fun fixesOf(error: WordListParseException, ctx: Context): String {
		if (error.repairs.isEmpty()) return ctx.getString(R.string.import_hint_no_single_fix)
		return error.repairs.mapIndexed { index, repair ->
			val fix = ctx.getString(fixResourceOf(repair.kind), formTextOf(repair, ctx))
			if (index == 0) fix else ctx.getString(R.string.import_fix_another, fix)
		}.joinToString("\n")
	}

	private fun fixResourceOf(kind: WordListRepairKind): Int = when (kind) {
		WordListRepairKind.SEPARATOR_SWAP -> R.string.import_fix_separator_swap
		WordListRepairKind.TRANSLATION_TEXT -> R.string.import_fix_translation_text
		WordListRepairKind.TRANSLATION_ORDER -> R.string.import_fix_translation_order
		WordListRepairKind.NOTE_TEXT -> R.string.import_fix_note_text
		WordListRepairKind.CHAPTER_NAME -> R.string.import_fix_chapter_name
		WordListRepairKind.CHAPTER_NAMED_BLOCK -> R.string.import_fix_chapter_named_block
		WordListRepairKind.CHAPTER_OPEN -> R.string.import_fix_chapter_open
		WordListRepairKind.CHAPTER_BLOCK -> R.string.import_fix_chapter_block
		WordListRepairKind.CHAPTER_CLOSE -> R.string.import_fix_chapter_close
		WordListRepairKind.DESCRIPTION_CLOSE -> R.string.import_fix_description_close
		WordListRepairKind.VARIANT_TEXT -> R.string.import_fix_variant_text
		WordListRepairKind.VARIANT_DIVIDER -> R.string.import_fix_variant_divider
		WordListRepairKind.DESCRIBED_TEXT -> R.string.import_fix_described_text
		WordListRepairKind.ESCAPED_SEPARATOR -> R.string.import_fix_escaped_separator
		WordListRepairKind.DOUBLED_BACKSLASH -> R.string.import_fix_doubled_backslash
	}

	private fun formTextOf(repair: WordListRepair, ctx: android.content.Context): String {
		if (WordListRepair.MISSING_TEXT !in repair.text) return repair.text
		val placeholder = when (repair.placeholder) {
			WordListPlaceholder.CHAPTER -> R.string.import_placeholder_chapter
			WordListPlaceholder.TRANSLATION -> R.string.import_placeholder_translation
			WordListPlaceholder.NOTE -> R.string.import_placeholder_note
			WordListPlaceholder.VARIANT -> R.string.import_placeholder_variant
			else -> R.string.import_placeholder_text
		}
		return repair.text.replace(
			WordListRepair.MISSING_TEXT.toString(),
			"<${ctx.getString(placeholder)}>",
		)
	}

	fun of(error: WordListParseException): Content {
		val ctx = GlobalDependencies.appContext
		val hint = when (error.problem) {
			WordListProblem.INVALID_SEPARATOR -> ctx.getString(R.string.import_hint_invalid_separator)
			WordListProblem.INCOMPLETE_ENTRY -> ctx.getString(R.string.import_hint_incomplete_entry)
			WordListProblem.SYMBOL_ONLY -> ctx.getString(R.string.import_hint_symbol_only)
			WordListProblem.MISSING_CHAPTER_OPEN -> ctx.getString(R.string.import_hint_missing_chapter_open)
			WordListProblem.MISSING_TRANSLATION -> ctx.getString(R.string.import_hint_missing_translation)
			WordListProblem.TRANSLATION_BEFORE_DESCRIPTION -> ctx.getString(R.string.import_hint_translation_before_description)
			WordListProblem.EXTRA_SEPARATOR -> ctx.getString(
				if (error.sourceLine.substring(error.problemIndex + 1).isBlank())
					R.string.import_hint_trailing_separator else R.string.import_hint_extra_separator
			)
			WordListProblem.MISSING_NOTE_TEXT -> ctx.getString(R.string.import_hint_missing_note_text)
			WordListProblem.NOTE_DIVIDER -> ctx.getString(R.string.import_hint_note_divider)
			WordListProblem.NOTE_SECOND_SEPARATOR -> ctx.getString(
				if (error.sourceLine.substring(error.problemIndex + 1).isBlank())
					R.string.import_hint_trailing_note_separator else R.string.import_hint_note_second_separator
			)
			WordListProblem.EMPTY_VARIANT -> ctx.getString(R.string.import_hint_empty_variant)
			WordListProblem.MISSING_CHAPTER_NAME -> ctx.getString(R.string.import_hint_missing_chapter_name)
			WordListProblem.CHAPTER_DIVIDER -> ctx.getString(R.string.import_hint_chapter_divider)
			WordListProblem.DESCRIPTION_WITHOUT_TEXT -> ctx.getString(R.string.import_hint_description_without_text)
			WordListProblem.DESCRIPTION_END -> ctx.getString(R.string.import_hint_description_end)
			WordListProblem.DESCRIPTION_CLOSING -> ctx.getString(R.string.import_hint_description_closing)
			WordListProblem.CHAPTER_END -> ctx.getString(R.string.import_hint_chapter_end)
			WordListProblem.CHAPTER_BRACES -> ctx.getString(R.string.import_hint_chapter_braces)
			WordListProblem.INCOMPLETE_ESCAPE -> ctx.getString(R.string.import_hint_incomplete_escape)
		}
		val pointer = buildString {
			error.sourceLine.take(error.problemIndex).forEach { append(if (it == '\t') '\t' else ' ') }
			append('↓')
		}
		return Content(
			ctx.getString(R.string.import_error_location, error.containerName, error.lineNumber),
			pointer,
			error.sourceLine,
			guidanceOf(error, hint, ctx),
		)
	}

	/** A fix is the whole message, the diagnosis is left for the case no fix exists. */
	private fun guidanceOf(error: WordListParseException, hint: String, ctx: Context): String =
		if (error.repairs.isEmpty()) "$hint\n\n${ctx.getString(R.string.import_hint_no_single_fix)}"
		else fixesOf(error, ctx)
}
