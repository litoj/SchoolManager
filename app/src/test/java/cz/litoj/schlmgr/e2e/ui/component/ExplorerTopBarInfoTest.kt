package cz.litoj.schlmgr.e2e.ui.component

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.component.ExplorerTopBar
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExplorerTopBarInfoTest {

	@get:Rule
	val rule = createComposeRule()

	private fun show(info: InfoBar) {
		rule.setContent {
			ExplorerTopBar(emptyList(), {}, info, false, {})
		}
	}

	private val mixed = mapOf(ItemKind.WORD to 3, ItemKind.CHAPTER to 4, ItemKind.NOTE to 1)

	@Test
	fun `the root names its subjects`() {
		show(InfoBar.Root(1))

		rule.onNodeWithText("1 subject").assertExists()
	}

	@Test
	fun `a chapter names every kind before its rate`() {
		show(InfoBar.Children(mixed, 50, ""))

		rule.onNodeWithText("3 words, 4 chapters, 1 note, Success rate: 50%").assertExists()
	}

	@Test
	fun `an empty chapter shows only the rate above its description`() {
		show(InfoBar.Children(emptyMap(), -1, "desc"))

		rule.onNodeWithText("Success rate: Untested\ndesc").assertExists()
	}

	@Test
	fun `the search strip counts results`() {
		show(InfoBar.Search(2, 5))

		rule.onNodeWithText("Results: 2", substring = true).assertExists()
	}

	@Test
	@Config(qualifiers = "cs")
	fun `Czech counts stay in the nominative`() {
		show(InfoBar.Children(mixed, 50, ""))

		rule.onNodeWithText("3 slova, 4 kapitoly, 1 poznámka, Úspěšnost: 50%").assertExists()
	}
}
