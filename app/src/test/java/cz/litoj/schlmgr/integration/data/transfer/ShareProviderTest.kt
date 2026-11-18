package cz.litoj.schlmgr.integration.data.transfer

import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.transfer.FileGateway
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The manifest door of the shared folder, the file provider behind it.
 * - the provider keeps one folder per authority for the whole process, so this is the
 *   suite's only test through the real door, see [FileGateway.shareUri].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareProviderTest {

	private val ctx get() = RuntimeEnvironment.getApplication()

	@Before
	fun setUp() {
		GlobalDependencies.appContext = ctx
	}

	@Test
	fun `the manifest provider publishes the shared folder`() {
		val uri = FileGateway.shareCopy(ctx, "Kapitel.txt", "w1;t1\n")

		assertEquals("content://cz.litoj.schlmgr.fileprovider/shared/Kapitel.txt", uri.toString())
	}
}
