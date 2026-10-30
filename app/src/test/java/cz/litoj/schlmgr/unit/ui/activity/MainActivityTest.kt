package cz.litoj.schlmgr.unit.ui.activity

import android.content.Intent
import cz.litoj.schlmgr.ui.activity.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * - the import destination clears with its read, so a rotation fires it once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

	@Test
	fun `a launch target reads once and clears its extras`() {
		val intent = Intent()
		intent.putIntegerArrayListExtra(MainActivity.EXTRA_PATH, arrayListOf(3, 7))
		intent.putExtra(MainActivity.EXTRA_SCROLL, true)

		val target = MainActivity.consumeLaunchTarget(intent)

		assertEquals(listOf(3, 7), target?.path)
		assertTrue(target?.scrollToBottom == true)
		// the recreation hands the same intent over again, the extras are gone
		assertNull(MainActivity.consumeLaunchTarget(intent))
	}

	@Test
	fun `an intent without the path extra holds no target`() {
		assertNull(MainActivity.consumeLaunchTarget(Intent()))
	}
}
