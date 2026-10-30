package cz.litoj.schlmgr.integration.ui.activity

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.activity.ImportActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - a launch without a shared uri finishes the mover, nothing hangs blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportActivityTest {

	@Test
	fun `a launch without a shared uri finishes the activity`() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		val scenario = ActivityScenario.launch(ImportActivity::class.java)

		// the finish runs during onCreate, so the scenario never reaches a live state
		assertEquals(Lifecycle.State.DESTROYED, scenario.state)

		scenario.close()
	}
}
