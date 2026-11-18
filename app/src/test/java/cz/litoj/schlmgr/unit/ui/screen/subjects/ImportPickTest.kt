package cz.litoj.schlmgr.unit.ui.screen.subjects

import android.app.Activity
import android.content.Intent
import android.net.Uri
import cz.litoj.schlmgr.ui.screen.subjects.OpenEditableDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The pick of a file the app may later offer to an editor.
 * - the pick itself has to ask for write, or no grant can ever carry it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportPickTest {

	private val types = arrayOf("text/plain", "application/json")

	private fun pick() = OpenEditableDocument()
		.createIntent(RuntimeEnvironment.getApplication(), types)

	@Test
	fun `the import pick asks to keep the document open for an editor`() {
		val intent = pick()

		assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
		assertTrue("the pick opens documents only", intent.hasCategory(Intent.CATEGORY_OPENABLE))
		assertEquals(types.toList(), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList())
		assertTrue("the app reads the file",
			intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
		assertTrue("an outside editor has to reach the same file",
			intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
		assertTrue("the grant has to outlive the picker",
			intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
	}

	@Test
	fun `only an accepted pick hands a document`() {
		val picked = Uri.parse("content://schlmgr.test/list.txt")
		val answer = Intent().apply { data = picked }

		assertNull("a cancel keeps the old file away",
			OpenEditableDocument().parseResult(Activity.RESULT_CANCELED, answer))
		assertEquals(picked, OpenEditableDocument().parseResult(Activity.RESULT_OK, answer))
	}
}
