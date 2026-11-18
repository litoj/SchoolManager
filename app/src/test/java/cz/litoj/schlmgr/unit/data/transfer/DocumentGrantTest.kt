package cz.litoj.schlmgr.unit.data.transfer

import android.content.Context
import android.content.UriPermission
import android.net.Uri
import cz.litoj.schlmgr.data.transfer.FileGateway
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The document grant that lets an outside editor reach a picked file.
 * - a held document carries write, an editor of no write saves nothing.
 * - a stored import gives the grant back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentGrantTest {

	private val context: Context get() = RuntimeEnvironment.getApplication()

	private val file = Uri.parse("content://schlmgr.test/list.txt")

	private fun held(): List<UriPermission> =
		context.contentResolver.persistedUriPermissions.filter { it.uri == file }

	@Test
	fun `a picked document is held for an editor with write access`() {
		FileGateway.holdForEdit(context, file)

		val grant = held().single()
		assertTrue("the editor reads the file", grant.isReadPermission)
		assertTrue("the editor saves into the same file", grant.isWritePermission)
	}

	@Test
	fun `a stored import hands the grant back`() {
		FileGateway.holdForEdit(context, file)

		FileGateway.releaseEdit(context, file)

		assertTrue("nothing is held for a file that landed", held().isEmpty())
	}

	@Test
	fun `a document never held is left alone`() {
		FileGateway.releaseEdit(context, file)

		assertTrue("no grant appears out of nothing", held().isEmpty())
	}
}
