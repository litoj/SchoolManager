package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.ActionConfig
import cz.litoj.schlmgr.ui.viewmodel.ExplorerMenuKind
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.explorerMenu
import cz.litoj.schlmgr.ui.viewmodel.filterTagOf
import cz.litoj.schlmgr.ui.viewmodel.menuKindFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerMenuTest {

	private val config = ActionConfig(ActionConfig.DEFAULT_ORDER)

	private fun menu(
		kind: ExplorerMenuKind,
		selecting: Boolean,
		singleSelected: Boolean = true,
		config: ActionConfig = this.config,
		filterActive: Boolean = false,
	) = explorerMenu(kind, selecting, singleSelected, config, filterActive = filterActive)

	private fun sortRows(kind: ExplorerMenuKind) = menu(kind, selecting = false)
			.single { submenu -> submenu.children.any { it.id == MenuId.SORT_ALPHA_AZ } }

	@Test
	fun `every menu kind filters by the selection flag`() {
		for (kind in ExplorerMenuKind.entries) {
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
				selecting.all { it.selectionAction },
			)
		}
	}

	@Test
	fun `every menu kind offers the export while selecting only`() {
		for (kind in ExplorerMenuKind.entries) {
			val selecting = menu(kind, selecting = true)
			assertTrue(
				"$kind with a selection offers the export",
				selecting.any { it.id == MenuId.EXPORT },
			)
			val plain = menu(kind, selecting = false)
			assertTrue(
				"$kind without a selection offers no export",
				plain.none { it.id == MenuId.EXPORT },
			)
		}
	}

	@Test
	fun `the root menu offers no creation and flip with a selection`() {
		val plain = menu(ExplorerMenuKind.ROOT, selecting = false)
		assertTrue("the floating button owns creation, no menu row offers it",
			plain.none { it.id == MenuId.NEW_SUBJECT })

		val selecting = menu(ExplorerMenuKind.ROOT, selecting = true)
		assertTrue(selecting.any { it.id == MenuId.FLIP })
		assertTrue(selecting.any { it.id == MenuId.REFERENCE })
	}

	@Test
	fun `a custom order moves its overflow actions into the menu`() {
		val custom = ActionConfig(
			listOf(
				SelectionAction.TEST, SelectionAction.REFERENCE, SelectionAction.EDIT,
				SelectionAction.DELETE, SelectionAction.CUT, SelectionAction.FLIP,
				SelectionAction.EXPORT, SelectionAction.GOTO,
			),
		)

		val selecting = menu(ExplorerMenuKind.CONTAINER, selecting = true, config = custom)

		assertTrue("the overflowed cut joins the menu",
			selecting.any { it.id == MenuId.CUT })
		assertFalse("a strip slot leaves the menu",
			selecting.any { it.id == MenuId.REFERENCE })
	}

	@Test
	fun `edit and goto stay disabled without a single selection`() {
		// edit sits in the overflow, where the menu must gate it on the single selection
		val custom = ActionConfig(
			listOf(
				SelectionAction.TEST, SelectionAction.CUT, SelectionAction.DELETE, SelectionAction.REFERENCE,
				SelectionAction.FLIP, SelectionAction.EDIT, SelectionAction.GOTO, SelectionAction.EXPORT,
			),
		)
		for (single in listOf(false, true)) {
			val selecting = menu(ExplorerMenuKind.CONTAINER, selecting = true, singleSelected = single, config = custom)
			val edit = selecting.single { it.id == MenuId.EDIT }
			assertEquals("edit follows the single selection", single, edit.enabled)
			if (!single) assertEquals("the blocked edit states its reason",
				R.string.unavailable_single_row, edit.blockedReason)
		}
		val searching = menu(ExplorerMenuKind.SEARCH, selecting = true, singleSelected = false, config = custom)
		val goto = searching.single { it.id == MenuId.GOTO }
		assertFalse("goto follows the single selection", goto.enabled)
		assertEquals("the blocked goto states its reason",
			R.string.unavailable_single_row, goto.blockedReason)
	}

	@Test
	fun `an empty writable selection mutes the export with its reason`() {
		for (kind in ExplorerMenuKind.entries) {
			val selecting = explorerMenu(
				kind, selecting = true, singleSelected = true, config = config,
				exportBlockedReason = R.string.unavailable_export_empty,
			)
			val export = selecting.single { it.id == MenuId.EXPORT }
			assertFalse("$kind mutes the export", export.enabled)
			assertEquals("$kind states why the export cannot run",
				R.string.unavailable_export_empty, export.blockedReason)
		}
	}

	@Test
	fun `the search menu offers sorting without an extra selection action`() {
		val plain = menu(ExplorerMenuKind.SEARCH, selecting = false)
		assertTrue(plain.all { !it.selectionAction })
		assertTrue(plain.any { it.children.isNotEmpty() })
	}

	@Test
	fun `an active search filter adds its clear row`() {
		val filtered = menu(ExplorerMenuKind.SEARCH, selecting = false, filterActive = true)

		assertTrue("the clear row follows the sort row",
			filtered.any { it.id == MenuId.CLEAR_FILTER })
		assertFalse("a plain search has nothing to clear",
			menu(ExplorerMenuKind.SEARCH, selecting = false)
				.any { it.id == MenuId.CLEAR_FILTER })
	}

	@Test
	fun `every menu kind leads with select all, sort, then filter`() {
		for (kind in ExplorerMenuKind.entries) {
			val lead = menu(kind, selecting = false).take(3).map { it.label }
			assertEquals("$kind", listOf(R.string.select_all, R.string.sort, R.string.filter_tag), lead)
		}
	}

	@Test
	fun `the filter offers the tags by importance without description`() {
		val filter = menu(ExplorerMenuKind.CONTAINER, selecting = false).single { it.label == R.string.filter_tag }

		assertEquals(R.drawable.ic_filter, filter.icon)
		assertEquals(
			listOf(SearchTag.NAME, SearchTag.TYPE, SearchTag.RATE, SearchTag.TESTED),
			filter.children.map { filterTagOf(it.id) },
		)
	}

	@Test
	fun `blocked reasons mute the filter and select all`() {
		val blocked = explorerMenu(
			ExplorerMenuKind.CONTAINER, selecting = false, singleSelected = true, config,
			searchBlockedReason = R.string.unavailable_search_root,
			selectAllBlockedReason = R.string.unavailable_paste_armed,
		)

		assertEquals(R.string.unavailable_search_root, blocked.single { it.label == R.string.filter_tag }.blockedReason)
		assertEquals(R.string.unavailable_paste_armed, blocked.single { it.id == MenuId.SELECT_ALL }.blockedReason)
	}

	@Test
	fun `a non filter id has no tag`() {
		assertEquals(null, filterTagOf(MenuId.SORT_ALPHA_AZ))
	}

	@Test
	fun `the sort rows put original first and each ascending choice before descending`() {
		assertEquals(
			listOf(
				MenuId.SORT_DEFAULT, MenuId.SORT_ALPHA_AZ, MenuId.SORT_ALPHA_ZA,
				MenuId.SORT_SF_01, MenuId.SORT_SF_10, MenuId.SORT_PRIORITY,
				MenuId.SORT_LENGTH_01, MenuId.SORT_LENGTH_10, MenuId.SORT_RANDOM,
			),
			sortRows(ExplorerMenuKind.ROOT).children.map { it.id },
		)
	}

	@Test
	fun `the search menu's sort offers no default order`() {
		// sort-default reloads the browse order, which a search list has none of,
		// and the entry would leave the search mode on a browse list
		val sort = menu(ExplorerMenuKind.SEARCH, selecting = false)
			.single { it.label == R.string.sort }
		assertTrue(sort.children.none { it.id == MenuId.SORT_DEFAULT })
		assertTrue(sort.children.isNotEmpty())
	}

	@Test
	fun `every menu kind's sort offers the priority sort`() {
		// the test-urgency ordering stays in every kind, and only the default
		// position has no search meaning
		for (kind in ExplorerMenuKind.entries) {
			val sort = menu(kind, selecting = false)
				.single { submenu -> submenu.children.any { it.id == MenuId.SORT_ALPHA_AZ } }
			assertTrue("$kind offers the priority sort",
				sort.children.any { it.id == MenuId.SORT_PRIORITY })
		}
	}

	@Test
	fun `no explorer menu imports from its top bar`() {
		// the New palette centre is the import door
		for (kind in ExplorerMenuKind.entries) {
			val plain = menu(kind, selecting = false)
			assertFalse("$kind holds no import row", plain.any { it.id == MenuId.IMPORT_FILE })
			assertTrue("$kind keeps its sort parent", plain.any { it.children.isNotEmpty() })
		}
	}

	@Test
	fun `every browse menu keeps the original order row`() {
		for (kind in listOf(ExplorerMenuKind.ROOT, ExplorerMenuKind.CONTAINER))
			assertTrue("$kind offers the original order",
				sortRows(kind).children.any { it.id == MenuId.SORT_DEFAULT })
	}

	@Test
	fun `the menu kind follows the search and the path depth`() {
		assertEquals(ExplorerMenuKind.SEARCH, menuKindFor(searching = true, pathSize = 0))
		assertEquals(ExplorerMenuKind.SEARCH, menuKindFor(searching = true, pathSize = 2))
		assertEquals(ExplorerMenuKind.ROOT, menuKindFor(searching = false, pathSize = 0))
		assertEquals(ExplorerMenuKind.CONTAINER, menuKindFor(searching = false, pathSize = 1))
		assertEquals(ExplorerMenuKind.CONTAINER, menuKindFor(searching = false, pathSize = 2))
	}
}
