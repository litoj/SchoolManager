package cz.litoj.schlmgr.integration.ui.screen.test

import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.app.GlobalDependencies
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

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		TestEngine.applyClever(true)
		AppSettings.set("isClever", true)
		TestSetupState.reset()
	}

	@After
	fun tearDown() {
		TestEngine.applyClever(true)
		AppSettings.set("isClever", true)
		TestSetupState.reset()
	}

	@Test
	fun `the content switch lets a random pool through and persists`() {
		val model = TestSetupViewModel()
		assertTrue("weak words are preferred by default", TestSetupState.cleverTest)

		model.toggleClever()

		assertFalse("the pool rule flipped", TestEngine.isClever)
		assertFalse("the row shows the new side", TestSetupState.cleverTest)
		assertFalse("the choice reached the storage", AppSettings.getBool("isClever", true))
	}

	@Test
	fun `a fresh setup keeps the last pick`() {
		TestSetupViewModel().toggleClever()

		TestSetupState.reset()

		assertFalse("the switch returns on the stored side", TestSetupState.cleverTest)
	}
}
