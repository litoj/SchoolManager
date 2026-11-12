package cz.litoj.schlmgr.integration.ui.viewmodel

import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.testing.TestEngine
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsIgnoredMistakesTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		TestEngine.applyIgnoredMistakes("")
		AppSettings.set("testIgnoredMistakes", "")
	}

	@After
	fun tearDown() {
		TestEngine.applyIgnoredMistakes("")
		AppSettings.set("testIgnoredMistakes", "")
	}

	@Test
	fun `the newline-separated text applies and persists unchanged`() {
		val text = "。\n \n..."
		val state = SettingsState()

		state.setIgnoredMistakes(text)

		assertEquals(text, TestEngine.ignoredMistakesText)
		assertEquals(text, state.ignoredMistakesText)
		assertEquals(text, AppSettings.getString("testIgnoredMistakes", ""))
	}

	@Test
	fun `individual fields append edit and remove entries`() {
		val state = SettingsState()
		state.setIgnoredMistake(0, "。")
		state.setIgnoredMistake(1, " ")
		state.setIgnoredMistake(2, "...")
		state.setIgnoredMistake(0, "、")

		assertEquals(listOf("、", " ", "..."), state.ignoredMistakes)
		assertEquals("、\n \n...", AppSettings.getString("testIgnoredMistakes", ""))

		state.setIgnoredMistake(0, "")

		assertEquals(listOf(" ", "..."), state.ignoredMistakes)
		assertEquals(" \n...", AppSettings.getString("testIgnoredMistakes", ""))
	}
}
