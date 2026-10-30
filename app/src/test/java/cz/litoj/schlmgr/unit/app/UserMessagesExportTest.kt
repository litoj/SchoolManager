package cz.litoj.schlmgr.unit.app

import android.net.Uri
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UserMessages
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UserMessagesExportTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@Test
	fun `an export result offers the saved document for sharing`() = runBlocking {
		val uri = Uri.parse("content://schlmgr.test/out/1")
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeout(1_000) { GlobalDependencies.messages.first() }
		}

		UserMessages.onExportSuccess(uri, "words.txt", 2, 1, "text/plain")

		val share = message.await() as GlobalDependencies.UiMessage.ShareFile
		assertEquals(uri, share.uri)
		assertEquals("text/plain", share.mime)
		assertTrue(share.text.contains("words.txt"))
	}
}
