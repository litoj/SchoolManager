package cz.litoj.schlmgr.e2e.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.MenuHost
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the menu hangs from its host's box by construction, since MenuHost is the only door to it.
 * - a popup reports its own window here, so the distance to the button is not assertable.
 * - these pins cover the rest of the host: the tap opens it, a pick reports and closes it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MenuAnchorTest {

	@get:Rule
	val rule = createComposeRule()

	private val entries = listOf(MenuEntry(1, R.string.sort))

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	private fun sortLabel(): String =
		RuntimeEnvironment.getApplication().getString(R.string.sort)

	@Test
	fun `a tap on a host opens its menu while its neighbour stays closed`() {
		rule.setContent {
			Row(Modifier.width(600.dp).height(60.dp)) {
				Spacer(Modifier.weight(1f))
				for (tag in listOf("left", "right")) {
					MenuHost(entries, {}, Modifier.width(60.dp).fillMaxHeight()) { open ->
						Box(Modifier.fillMaxSize().testTag(tag).clickable { open() })
					}
				}
			}
		}

		rule.onNodeWithTag("right").performClick()

		// one menu only, so the neighbour did not open a second one
		assertEquals(1, rule.onAllNodesWithText(sortLabel()).fetchSemanticsNodes().size)
	}

	@Test
	fun `a picked row reports its id and closes the menu`() {
		var picked = 0
		rule.setContent {
			Row(Modifier.height(60.dp)) {
				MenuHost(entries, { picked = it }, Modifier.width(60.dp).fillMaxHeight()) { open ->
					Box(Modifier.fillMaxSize().testTag("button").clickable { open() })
				}
			}
		}

		rule.onNodeWithTag("button").performClick()
		rule.onNodeWithText(sortLabel()).performClick()

		rule.runOnIdle {
			assertEquals(1, picked)
		}
		rule.onNodeWithText(sortLabel()).assertDoesNotExist()
	}

	@Test
	fun `every row icon takes one size whatever its drawable declares`() {
		val rows = listOf(
			MenuEntry(1, R.string.select_all, icon = R.drawable.ic_check_all),
			MenuEntry(2, R.string.sort, icon = R.drawable.ic_sort_black),
		)
		rule.setContent {
			Row(Modifier.height(60.dp)) {
				MenuHost(rows, {}, Modifier.width(60.dp).fillMaxHeight()) { open ->
					Box(Modifier.fillMaxSize().testTag("button").clickable { open() })
				}
			}
		}

		rule.onNodeWithTag("button").performClick()

		// the label follows its icon, so one icon size lines every label up
		val selectAll = RuntimeEnvironment.getApplication().getString(R.string.select_all)
		assertEquals(
			rule.onNodeWithText(sortLabel(), useUnmergedTree = true).getUnclippedBoundsInRoot().left,
			rule.onNodeWithText(selectAll, useUnmergedTree = true).getUnclippedBoundsInRoot().left,
		)
	}
}
