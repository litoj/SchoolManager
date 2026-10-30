package cz.litoj.schlmgr.unit.ui.popup

import androidx.compose.runtime.Composable
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.StickyImePad
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DialogsTest {

	private val log = mutableListOf<String>()

	private inner class RecordingSpec(val name: String) : DialogSpec {
		@Composable
		override fun Content() {
		}

		override fun onDismissed() {
			log.add(name)
		}
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	@Test
	fun `a resumed dialog follows the live keyboard inset`() {
		val pad = StickyImePad()
		pad.follow(120)
		assertEquals(120, pad.padFor(120))
		pad.follow(0)
		assertEquals("the back key close follows the live inset", 0, pad.padFor(0))
	}

	@Test
	fun `a paused dialog keeps the held inset over the dropped one`() {
		val pad = StickyImePad()
		pad.follow(300)
		pad.pause()
		pad.follow(0)
		assertEquals(300, pad.padFor(0))
	}

	@Test
	fun `a resume holds the inset until the keyboard is really back`() {
		val pad = StickyImePad()
		pad.follow(300)
		pad.pause()
		pad.resume()
		pad.follow(0)
		assertEquals("the dropped keyboard does not collapse the pad", 300, pad.padFor(0))
		pad.follow(290)
		assertEquals("the returned keyboard leads again", 290, pad.padFor(290))
	}

	@Test
	fun `a resume without a held inset follows the live inset right away`() {
		val pad = StickyImePad()
		pad.follow(0)
		pad.pause()
		pad.resume()
		pad.follow(0)
		assertEquals(0, pad.padFor(0))
	}

	@Test
	fun `a dismissal notifies exactly once`() {
		val spec = RecordingSpec("a")
		Dialogs.show(spec)
		Dialogs.dismiss(spec)
		Dialogs.dismiss(spec)
		assertEquals(listOf("a"), log)
	}

	@Test
	fun `dismissing an unshown dialog notifies nothing`() {
		Dialogs.dismiss(RecordingSpec("ghost"))
		assertEquals(emptyList<String>(), log)
	}

	@Test
	fun `a clear notifies top first`() {
		Dialogs.show(RecordingSpec("bottom"))
		Dialogs.show(RecordingSpec("middle"))
		Dialogs.show(RecordingSpec("top"))
		Dialogs.clear()
		assertEquals(listOf("top", "middle", "bottom"), log)
	}
}
