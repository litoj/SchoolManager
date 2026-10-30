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

class SubjectsMenuTest {

	@Test
	fun `every top-level entry carries an icon`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			for (selecting in listOf(false, true)) {
				val menu = subjectsMenu(kind, selecting)
				assertTrue("$kind selecting=$selecting gives every row an icon",
					menu.all { it.icon != 0 })
			}
		}
	}

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
	fun `every menu kind offers the export while selecting only`() {
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val selecting = subjectsMenu(kind, selecting = true)
			assertTrue(
				"$kind with a selection offers the export",
				selecting.any { it.children.isNotEmpty() &&
					it.children.all { child -> child.id == MenuId.EXPORT_WORD || child.id == MenuId.EXPORT_JSON } },
			)
			val plain = subjectsMenu(kind, selecting = false)
			assertTrue(
				"$kind without a selection offers no export",
				plain.none { it.children.isNotEmpty() &&
					it.children.any { child -> child.id == MenuId.EXPORT_WORD || child.id == MenuId.EXPORT_JSON } },
			)
		}
	}

	@Test
	fun `the root menu offers no creation and flip with a selection`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = false)
		assertTrue("the floating button owns creation, no menu row offers it",
			plain.none { it.id == MenuId.NEW_SUBJECT })

		val selecting = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = true)
		assertTrue(selecting.any { it.id == MenuId.FLIP })
		assertTrue(selecting.any { it.id == MenuId.REFERENCE })
		assertEquals(3, selecting.size)
	}

	@Test
	fun `the search menu without a selection keeps only the sort row`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
		assertEquals(1, plain.size)
		assertTrue(plain.single().children.isNotEmpty())
	}

	@Test
	fun `the search menu's sort offers no default order`() {
		// sort-default reloads the browse order, which a search list has none of,
		// and the entry would leave the search mode on a browse list
		val sort = subjectsMenu(MainExplorerViewModel.MenuKind.SEARCH, selecting = false)
			.single { it.children.isNotEmpty() && !it.always }
		assertTrue(sort.children.none { it.id == MenuId.SORT_DEFAULT })
		assertTrue(sort.children.isNotEmpty())
	}

	@Test
	fun `every menu kind's sort offers the priority sort`() {
		// the test-urgency ordering stays in every kind, and only the default
		// position has no search meaning
		for (kind in MainExplorerViewModel.MenuKind.entries) {
			val sort = subjectsMenu(kind, selecting = false)
				.single { submenu -> submenu.children.any { it.id == MenuId.SORT_ALPHA_AZ } }
			assertTrue("$kind offers the priority sort",
				sort.children.any { it.id == MenuId.SORT_PRIORITY })
		}
	}

	@Test
	fun `the root menu groups both imports under one submenu`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = false)
		// no import leaf sits at the top level of the root, because both hide in the submenu
		assertTrue(plain.none { it.id == MenuId.IMPORT_DIR || it.id == MenuId.IMPORT_FILE })
		val imports = plain.single { submenu -> submenu.children.any { it.id == MenuId.IMPORT_DIR } }
		assertEquals(listOf(MenuId.IMPORT_DIR, MenuId.IMPORT_FILE), imports.children.map { it.id })
		// the submenu is not a selection action, so a selection drops it with the rest
		assertTrue(subjectsMenu(MainExplorerViewModel.MenuKind.ROOT, selecting = true)
			.none { it.children == imports.children })
	}

	@Test
	fun `the container menu offers the plain file import only`() {
		val plain = subjectsMenu(MainExplorerViewModel.MenuKind.CONTAINER, selecting = false)
		// the only import a container offers is the file import, as a plain row,
		// so no submenu tells it apart
		assertEquals(1, plain.count { it.id == MenuId.IMPORT_FILE || it.id == MenuId.IMPORT_DIR })
		assertTrue(plain.any { it.id == MenuId.IMPORT_FILE && it.children.isEmpty() })
		assertTrue("a container offers no folder import", plain.none { it.id == MenuId.IMPORT_DIR })
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
