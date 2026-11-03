package cz.litoj.schlmgr.unit.ui.screen.subjects

import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MenuId
import cz.litoj.schlmgr.ui.screen.subjects.menuKindFor
import cz.litoj.schlmgr.ui.screen.subjects.selectAllButtonVisible
import cz.litoj.schlmgr.ui.screen.subjects.subjectsMenu
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectsMenuTest {

	private val config = ActionConfig(ActionConfig.DEFAULT_ORDER, ActionConfig.DEFAULT_CONNECTIONS)

	private fun menu(
		kind: MainExplorerViewModel.MenuKind,
		selecting: Boolean,
		singleSelected: Boolean = true,
		config: ActionConfig = this.config,
	) = subjectsMenu(kind, selecting, singleSelected, config)

	@Test
	fun `every top-level entry carries an icon`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			for (selecting in listOf(false, true)) {
				val menu = menu(kind, selecting)
				assertTrue("$kind selecting=$selecting gives every row an icon",
					menu.all { it.icon != 0 })
			}
		}
	}

	@Test
	fun `every menu kind filters by the selection flag`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val plain = menu(kind, selecting = false)
			assertTrue("$kind without a selection is not empty", plain.isNotEmpty())
			assertTrue(
				"$kind without a selection keeps only plain rows",
				plain.all { !it.selectionAction },
			)
			val selecting = menu(kind, selecting = true)
			assertTrue("$kind with a selection is not empty", selecting.isNotEmpty())
			assertTrue(
				"$kind with a selection keeps only selection rows",
				selecting.all { it.selectionAction || it.always },
			)
		}
	}

	@Test
	fun `every menu kind offers the export while selecting only`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val selecting = menu(kind, selecting = true)
			assertTrue(
				"$kind with a selection offers the export",
				selecting.any { it.children.isNotEmpty() &&
					it.children.all { child -> child.id == MenuId.EXPORT_WORD || child.id == MenuId.EXPORT_JSON } },
			)
			val plain = menu(kind, selecting = false)
			assertTrue(
				"$kind without a selection offers no export",
				plain.none { it.children.isNotEmpty() &&
					it.children.any { child -> child.id == MenuId.EXPORT_WORD || child.id == MenuId.EXPORT_JSON } },
			)
		}
	}

	@Test
	fun `the root menu offers no creation and flip with a selection`() {
		val plain = menu(MainExplorerViewModel.MenuKind.ROOT, selecting = false)
		assertTrue("the floating button owns creation, no menu row offers it",
			plain.none { it.id == MenuId.NEW_SUBJECT })

		val selecting = menu(MainExplorerViewModel.MenuKind.ROOT, selecting = true)
		assertTrue(selecting.any { it.id == MenuId.FLIP })
		assertTrue(selecting.any { it.id == MenuId.REFERENCE })
		assertEquals(3, selecting.size)
	}

	@Test
	fun `a custom order moves its overflow actions into the menu`() {
		val custom = ActionConfig(
			listOf(
				SelectionAction.TEST, SelectionAction.REFERENCE, SelectionAction.EDIT,
				SelectionAction.DELETE, SelectionAction.CUT, SelectionAction.FLIP,
				SelectionAction.EXPORT,
			),
			emptyMap(),
		)

		val selecting = menu(MainExplorerViewModel.MenuKind.CONTAINER, selecting = true, config = custom)

		assertTrue("the overflowed cut joins the menu",
			selecting.any { it.id == MenuId.CUT })
		assertFalse("a strip slot leaves the menu",
			selecting.any { it.id == MenuId.REFERENCE })
	}

	@Test
	fun `an unbound goto joins the search menu`() {
		val unbound = ActionConfig(ActionConfig.DEFAULT_ORDER, emptyMap())

		val selecting = menu(MainExplorerViewModel.MenuKind.SEARCH, selecting = true, config = unbound)

		assertTrue(selecting.any { it.id == MenuId.GOTO })
	}

	@Test
	fun `edit and goto stay disabled without a single selection`() {
		// edit sits in the overflow, where the menu must gate it on the single selection
		val custom = ActionConfig(
			listOf(
				SelectionAction.TEST, SelectionAction.CUT, SelectionAction.DELETE, SelectionAction.REFERENCE,
				SelectionAction.EDIT, SelectionAction.FLIP, SelectionAction.EXPORT,
			),
			emptyMap(),
		)
		for (single in listOf(false, true)) {
			val selecting = menu(MainExplorerViewModel.MenuKind.CONTAINER, selecting = true, singleSelected = single, config = custom)
			assertEquals("edit follows the single selection", single,
				selecting.single { it.id == MenuId.EDIT }.enabled)
		}
		val searching = menu(MainExplorerViewModel.MenuKind.SEARCH, selecting = true, singleSelected = false, config = custom)
		assertFalse("goto follows the single selection",
			searching.single { it.id == MenuId.GOTO }.enabled)
	}

	@Test
	fun `the search menu without a selection keeps only the sort row`() {
		val plain = menu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
		assertEquals(1, plain.size)
		assertTrue(plain.single().children.isNotEmpty())
	}

	@Test
	fun `the search menu's sort offers no default order`() {
		// sort-default reloads the browse order, which a search list has none of,
		// and the entry would leave the search mode on a browse list
		val sort = menu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
			.single { it.children.isNotEmpty() && !it.always }
		assertTrue(sort.children.none { it.id == MenuId.SORT_DEFAULT })
		assertTrue(sort.children.isNotEmpty())
	}

	@Test
	fun `every menu kind's sort offers the priority sort`() {
		// the test-urgency ordering stays in every kind, and only the default
		// position has no search meaning
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val sort = menu(kind, selecting = false)
				.single { submenu -> submenu.children.any { it.id == MenuId.SORT_ALPHA_AZ } }
			assertTrue("$kind offers the priority sort",
				sort.children.any { it.id == MenuId.SORT_PRIORITY })
		}
	}

	@Test
	fun `both browse menus offer one plain import row`() {
		for (kind in listOf(MainExplorerViewModel.MenuKind.ROOT, MainExplorerViewModel.MenuKind.CONTAINER)) {
			val plain = menu(kind, selecting = false)
			// a single import needs no submenu, so the row sits plain in every depth
			assertTrue("$kind offers the import row",
				plain.any { it.id == MenuId.IMPORT_FILE && it.children.isEmpty() })
			assertEquals("$kind offers exactly one import row",
				1, plain.count { it.id == MenuId.IMPORT_FILE })
		}
	}

	@Test
	fun `the menu kind follows the search and the path depth`() {
		assertEquals(MainExplorerViewModel.MenuKind.SEARCH, menuKindFor(searching = true, pathSize = 0))
		assertEquals(MainExplorerViewModel.MenuKind.SEARCH, menuKindFor(searching = true, pathSize = 2))
		assertEquals(MainExplorerViewModel.MenuKind.ROOT, menuKindFor(searching = false, pathSize = 0))
		assertEquals(MainExplorerViewModel.MenuKind.CONTAINER, menuKindFor(searching = false, pathSize = 1))
		assertEquals(MainExplorerViewModel.MenuKind.CONTAINER, menuKindFor(searching = false, pathSize = 2))
	}

	@Test
	fun `the select-all button shows wherever the search button does, and always in selection mode`() {
		// on the subjects screen the search button stays hidden, but a selection
		// still gets its select-all
		assertTrue(selectAllButtonVisible(selecting = true, searchVisible = false))
		assertTrue(selectAllButtonVisible(selecting = true, searchVisible = true))
		assertTrue(selectAllButtonVisible(selecting = false, searchVisible = true))
		assertFalse(selectAllButtonVisible(selecting = false, searchVisible = false))
	}
}
