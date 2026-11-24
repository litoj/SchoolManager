package cz.litoj.schlmgr.unit.app

import android.net.Uri
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.app.UserMessages.UiMessage
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

	private lateinit var userMessages: UserMessages

	@Before
	fun setUp() {
		userMessages = UserMessages(RuntimeEnvironment.getApplication())
	}

	@Test
	fun `an export result offers the saved document for sharing`() = runBlocking {
		val uri = Uri.parse("content://schlmgr.test/out/1")
		val message = async(start = CoroutineStart.UNDISPATCHED) {
			withTimeout(1_000) { userMessages.messages.first() }
		}

		userMessages.onExportSuccess(uri, "words.txt", 2, 1, "text/plain")

		val share = message.await() as UiMessage.ShareFile
		assertEquals(uri, share.uri)
		assertEquals("text/plain", share.mime)
		assertTrue(share.text.contains("words.txt"))
	}
}
