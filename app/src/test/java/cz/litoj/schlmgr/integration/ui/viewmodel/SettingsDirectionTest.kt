package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.Settings
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

	private lateinit var settings: Settings

	@Before
	fun setUp() {
		settings = Settings(RuntimeEnvironment.getApplication())
		TestEngine.applyReversed(false)
		settings.set("reverseTest", false)
	}

	@After
	fun tearDown() {
		TestEngine.applyReversed(false)
		settings.set("reverseTest", false)
	}

	@Test
	fun `the direction row flips and persists`() {
		val model = SettingsViewModel(settings)
		assertFalse("the word is typed by default", TestEngine.isReversed)

		model.state.toggleDefaultReverse()

		assertTrue("the default flipped to the typed translation", TestEngine.isReversed)
		assertTrue("the default reached the storage", settings.getBool("reverseTest", false))
	}
}
