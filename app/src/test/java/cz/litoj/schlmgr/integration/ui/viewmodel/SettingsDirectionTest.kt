package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.viewmodel.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The test direction row flips the engine and persists the choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsDirectionTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		TestEngine.applyReversed(false)
		AppSettings.set("reverseTest", false)
	}

	@After
	fun tearDown() {
		TestEngine.applyReversed(false)
		AppSettings.set("reverseTest", false)
	}

	@Test
	fun `the direction row flips and persists`() {
		val model = SettingsViewModel()
		assertFalse("the word is typed by default", TestEngine.isReversed)

		model.state.toggleDefaultReverse()

		assertTrue("the default flipped to the typed translation", TestEngine.isReversed)
		assertTrue("the default reached the storage", AppSettings.getBool("reverseTest", false))
	}
}
