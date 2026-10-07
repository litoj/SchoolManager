package cz.litoj.schlmgr.ui.popup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The clipboard capping of [MessagePopup] ([MessagePopup.capForClipboard]). */
class MessagePopupTest {

	private val note = "[the copy was truncated]"

	@Test
	fun `a text within the limit passes through unchanged`() {
		assertEquals("", MessagePopup.capForClipboard("", note))
		assertEquals("abc", MessagePopup.capForClipboard("abc", note))
		assertEquals(
			"a".repeat(MessagePopup.CLIPBOARD_CHAR_LIMIT),
			MessagePopup.capForClipboard("a".repeat(MessagePopup.CLIPBOARD_CHAR_LIMIT), note))
	}

	@Test
	fun `a text over the limit is cut and ends with the truncation note`() {
		val text = "b".repeat(MessagePopup.CLIPBOARD_CHAR_LIMIT + 1)
		val capped = MessagePopup.capForClipboard(text, note)
		val cut = capped.removeSuffix("\n$note")
		assertEquals(MessagePopup.CLIPBOARD_CHAR_LIMIT - note.length - 1, cut.length)
		assertTrue(text.startsWith(cut))
	}

	@Test
	fun `a capped text stays within the limit including the note`() {
		val text = "c".repeat(MessagePopup.CLIPBOARD_CHAR_LIMIT * 10)
		val capped = MessagePopup.capForClipboard(text, note)
		assertTrue(capped.length <= MessagePopup.CLIPBOARD_CHAR_LIMIT)
		assertTrue(capped.startsWith("c"))
	}
}
