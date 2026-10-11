package cz.litoj.schlmgr.unit.ui.popup

import androidx.compose.runtime.Composable
import cz.litoj.schlmgr.ui.popup.DialogSpec
import cz.litoj.schlmgr.ui.popup.Dialogs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the dialog stack removals ([Dialogs.dismiss]/[Dialogs.clear]):
 * a dismissal notifies exactly once even when requested twice, and a clear
 * notifies top first.
 */
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
