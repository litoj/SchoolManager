package cz.litoj.schlmgr.unit.ui.screen.subjects

import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MenuId
import cz.litoj.schlmgr.ui.screen.subjects.menuKindFor
import cz.litoj.schlmgr.ui.screen.subjects.selectAllButtonVisible
import cz.litoj.schlmgr.ui.screen.subjects.subjectsMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the subjects more-options menu ([subjectsMenu]): a selection
 * keeps only the rows that act on it (flip, reference) plus the always-present
 * export rows, while no selection keeps every other row — the PopupMenu's
 * visibility rule with the export available in both forms.
 */
class SubjectsMenuTest {

	@Test
	fun `every menu kind filters by the selection flag`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val plain = subjectsMenu(kind, selecting = false)
			assertTrue("$kind without a selection is not empty", plain.isNotEmpty())
			assertTrue(
				"$kind without a selection keeps only plain rows",
				plain.all { !it.selectionAction },
			)
			val selecting = subjectsMenu(kind, selecting = true)
			assertTrue("$kind with a selection is not empty", selecting.isNotEmpty())
			assertTrue(
				"$kind with a selection keeps only selection rows",
				selecting.all { it.selectionAction || it.always },
			)
		}
	}

	@Test
	fun `every menu kind offers the export in both forms`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			for (selecting in listOf(false, true)) {
				val menu = subjectsMenu(kind, selecting)
				assertTrue(
					"$kind selecting=$selecting offers the export",
					menu.any { it.children.isNotEmpty() && it.always &&
						it.children.all { child -> child.id == MenuId.EXPORT_WORD || child.id == MenuId.EXPORT_JSON } },
				)
			}
		}
	}

	@Test
	fun `the root menu offers creation without a selection and flip with one`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = false)
		assertTrue(plain.any { it.id == MenuId.NEW_SUBJECT })

		val selecting = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = true)
		assertTrue(selecting.any { it.id == MenuId.FLIP })
		assertTrue(selecting.any { it.id == MenuId.REFERENCE })
		assertEquals(3, selecting.size)
	}

	@Test
	fun `the search menu keeps the sort and export rows without a selection`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
		assertEquals(2, plain.size)
		assertTrue(plain.any { it.children.isNotEmpty() && !it.always })
		assertTrue(plain.any { it.always })
	}

	@Test
	fun `the search menu's sort offers no default order`() {
		// sort-default reloads the browse order — a search list has none,
		// and the entry would leave the search mode on a browse list
		val sort = subjectsMenu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
			.single { it.children.isNotEmpty() && !it.always }
		assertTrue(sort.children.none { it.id == MenuId.SORT_DEFAULT })
		assertTrue(sort.children.isNotEmpty())
	}

	@Test
	fun `the search menu go-to acts on one selected row`() {
		val single = subjectsMenu(
			MainExplorerViewModel.MenuKind.SEARCH, selecting = true, singleSelected = true)
		assertTrue(single.single { it.id == MenuId.GO_TO }.enabled)

		val several = subjectsMenu(MainExplorerViewModel.MenuKind.SEARCH, selecting = true)
		assertFalse("several selected rows mute the go-to", several.single { it.id == MenuId.GO_TO }.enabled)

		for (kind in listOf(
			MainExplorerViewModel.MenuKind.ROOT,
			MainExplorerViewModel.MenuKind.CONTAINER,
		)) {
			assertTrue("$kind offers no go-to", subjectsMenu(kind, selecting = true).none { it.id == MenuId.GO_TO })
		}
	}

	@Test
	fun `the root menu groups both imports under one submenu`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = false)
		// no import leaf sits at the root's top level — both hide in the submenu
		assertTrue(plain.none { it.id == MenuId.IMPORT_DIR || it.id == MenuId.IMPORT_FILE })
		val imports = plain.single { submenu -> submenu.children.any { it.id == MenuId.IMPORT_DIR } }
		assertEquals(listOf(MenuId.IMPORT_DIR, MenuId.IMPORT_FILE), imports.children.map { it.id })
		// the submenu is not a selection action — a selection drops it with the rest
		assertTrue(subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = true)
			.none { it.children == imports.children })
	}

	@Test
	fun `the container menus offer the plain file import only`() {
		for (kind in listOf(
			MainExplorerViewModel.MenuKind.CONTAINER,
		)) {
			val plain = subjectsMenu(kind, selecting = false)
			// the only import a container offers is the file import, as a plain
			// row — no submenu needed to tell it apart
			assertEquals(1, plain.count { it.id == MenuId.IMPORT_FILE || it.id == MenuId.IMPORT_DIR })
			assertTrue(plain.any { it.id == MenuId.IMPORT_FILE && it.children.isEmpty() })
			assertTrue("$kind offers no folder import", plain.none { it.id == MenuId.IMPORT_DIR })
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
		// the subjects screen — the search button stays hidden there, but a
		// selection still gets its select-all
		assertTrue(selectAllButtonVisible(selecting = true, searchVisible = false))
		assertTrue(selectAllButtonVisible(selecting = true, searchVisible = true))
		assertTrue(selectAllButtonVisible(selecting = false, searchVisible = true))
		// the plain subjects screen without a selection shows neither
		assertFalse(selectAllButtonVisible(selecting = false, searchVisible = false))
	}
}
