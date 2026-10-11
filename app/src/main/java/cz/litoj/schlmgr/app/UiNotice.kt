package cz.litoj.schlmgr.app

/**
 * A user notice by meaning — the shell picks every string at display time.
 */
sealed interface UiNotice {

	/** The armed paste was refused — the target sits inside the pasted content. */
	data object PasteInsideItself : UiNotice

	/** The armed paste was refused — no container is open (the subjects root). */
	data object PasteNoTarget : UiNotice

	/**
	 * The armed paste was refused. The rows belong to another subject — a
	 * whole subject merges instead.
	 */
	data object PasteOtherSubject : UiNotice

	/** The armed paste skipped rows already present in the target. */
	data object PasteAlreadyPresent : UiNotice

	/** The note's description reached the clipboard. */
	data object NoteCopied : UiNotice

	/** A search pattern failed to parse — [detail] is the regex compiler's report. */
	data class BadPattern(val detail: String) : UiNotice

	/** Flipping the selected words meets existing words — [lines] holds one line per conflict. */
	data class FlipConflicts(val lines: List<String>) : UiNotice

	/**
	 * Test-source pick refused — sources must come from one subject.
	 * - [subject] is the already-picked one, null when the refused pick
	 *   was first.
	 */
	data class OneSubjectOnly(val subject: String?) : UiNotice
}
