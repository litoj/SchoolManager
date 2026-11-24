package cz.litoj.schlmgr.integration.ui.screen.test

import cz.litoj.schlmgr.app.Settings
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.screen.test.TestSetupViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The content switch of the test setup screen, its engine write and its persistence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestSetupContentSwitchTest {

	private val testSetup get() = TestSetupState.shared
	private lateinit var settings: Settings
	private lateinit var userMessages: UserMessages

	@Before
	fun setUp() {
		settings = Settings(RuntimeEnvironment.getApplication())
		userMessages = UserMessages(RuntimeEnvironment.getApplication())
		TestEngine.applyClever(true)
		settings.set("isClever", true)
		testSetup.reset()
	}

	@After
	fun tearDown() {
		TestEngine.applyClever(true)
		settings.set("isClever", true)
		testSetup.reset()
	}

	@Test
	fun `the content switch lets a random pool through and persists`() {
		val model = TestSetupViewModel(settings = settings, userMessages = userMessages)
		assertTrue("weak words are preferred by default", testSetup.cleverTest)

		model.toggleClever()

		assertFalse("the pool rule flipped", TestEngine.isClever)
		assertFalse("the row shows the new side", testSetup.cleverTest)
		assertFalse("the choice reached the storage", settings.getBool("isClever", true))
	}

	@Test
	fun `a fresh setup keeps the last pick`() {
		TestSetupViewModel(settings = settings, userMessages = userMessages).toggleClever()

		testSetup.reset()

		assertFalse("the switch returns on the stored side", testSetup.cleverTest)
	}
}
