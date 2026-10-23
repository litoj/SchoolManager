package cz.litoj.schlmgr.app

/**
 * A user notice by meaning, and the shell picks every string at display time.
 */
sealed interface UiNotice {

	/** The armed paste was refused because the target sits inside the pasted content. */
	data object PasteInsideItself : UiNotice

	/** The armed paste was refused because no container is open at the subjects root. */
	data object PasteNoTarget : UiNotice

	/**
	 * The armed paste was refused. The rows belong to another subject, so a whole
	 * subject merges instead.
	 */
	data object PasteOtherSubject : UiNotice

	/** The armed paste skipped rows already present in the target. */
	data object PasteAlreadyPresent : UiNotice

	/** The description of the note reached the clipboard. */
	data object NoteCopied : UiNotice

	/** A search pattern failed to parse, and [detail] is the report of the regex compiler. */
	data class BadPattern(val detail: String) : UiNotice

	/** A flip-conflict row was left without a name, but the flip needs one. */
	data object FlipBlankName : UiNotice

	/**
	 * Test-source pick refused, since sources must come from one subject.
	 * - [subject] is the already-picked one.
	 * - null when the refused pick was first.
	 */
	data class OneSubjectOnly(val subject: String?) : UiNotice
}
