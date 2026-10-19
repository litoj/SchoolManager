package cz.litoj.schlmgr.integration.ui.screen.subjects

import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MenuId
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import cz.litoj.schlmgr.ui.viewmodel.TranslationValue
import cz.litoj.schlmgr.ui.viewmodel.toUi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The subjects screen's shell-state and back-dispatch logic on a real
 * in-memory database.
 * - back clears the selection before it pops a path level. The double-press
 *   exit window works off the injected clock, an armed paste cancels cleanly.
 * - the paste rules ride on the same harness — move within the subject,
 *   refuse another subject with a notice, merge a cut subject on open.
 * - the search-result behaviours — the search-mode ladder, a word hit
 *   flipping in place, the go-to-location action, refresh in place.
 * - the destructive and shaping flows — delete through a request, flip of
 *   the selected words, the sorts, the creator's commits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainExplorerViewModelTest {

	private lateinit var db: ItemDatabase
	private var now = 1_000_000L

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun vm(): MainExplorerViewModel {
		// the repository reads run inline (an unconfined dispatcher), so the
		// construction-time load lands before the assertions
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		// the exit window reads the injected clock — deterministic, no sleeps
		model.clock = { now }
		return model
	}

	/** Opens the subject and returns the view model sitting inside it. */
	private fun vmIn(subject: String): MainExplorerViewModel {
		// the model loads the current content on construction, so callers seed first
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == subject && it.item.type == ItemKind.CHAPTER })
		return model
	}

	private fun seed(): Pair<Int, Int> {
		// subject A holds chapter c1 — subject B is the paste target
		val a = ItemRepository.createSubject("A", null)
		val b = ItemRepository.createSubject("B", null)
		ItemRepository.createChapter(a.id, "c1", null, 0)
		return a.id to b.id
	}

	/**
	 * A one-word search-result list — the word hit lives in A/c1, and the model
	 * shows it as a search list (no engine run, so the behaviours under test
	 * start from a shown result list).
	 */
	private fun searchedWord(): Pair<MainExplorerViewModel, ItemRow> {
		val (aId, _) = seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		val word = ItemRepository.createWord(c1.item.id, "w1", null, listOf("t1" to null))
		val row = ItemRow.ofSearchHit(word, listOf(ItemRepository.get(aId)!!, c1.item), 1, emptyList())
		model.load(listOf(row), isSearch = true)
		return model to row
	}

	@Test
	fun `back clears the selection before it pops a path level`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		model.setSelected(c1, true)
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		// the selection is gone, the path is not — one press does one thing
		assertFalse(model.items().any { it.selected })

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertTrue("the back press left the subject", model.history.path.isEmpty())
	}

	@Test
	fun `a long-press on a browse row starts the selection`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		// the list is reorderable, and the press still selects — selection entry
		// must not depend on the drag mode the same press arms
		model.onItemLongClick(c1)
		assertTrue(c1.selected)
		assertTrue(model.listState.value.selectionActive)
		model.onItemLongClick(c1)
		assertFalse(c1.selected)
	}

	@Test
	fun `new actions refuse while searching`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")

		assertFalse(model.onMenuItemClick(MenuId.NEW_WORD))
		assertFalse(model.onMenuItemClick(MenuId.NEW_CHAPTER))
		assertFalse(model.onMenuItemClick(MenuId.NEW_NOTE))
		assertFalse(model.onMenuItemClick(MenuId.NEW_SUBJECT))
	}

	@Test
	fun `a search starts with an empty result count, not a child count`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("nothing-matches-this")

		assertTrue("no rows matched", model.items().isEmpty())
		val info = model.state.value.info
		assertTrue("the strip is the search count from the start", info is InfoBar.Search)
		assertEquals(0, (info as InfoBar.Search).count)
	}

	@Test
	fun `doPaste in search moves the hit with its row`() {
		val (model, row) = searchedWord()
		model.setSelected(row, true)
		model.move(false)
		model.doPaste()

		assertFalse(model.pasteArmed)
		assertTrue("the results stay a search list",
			model.listState.value.variant == ExplorerRowVariant.Search)
		assertEquals(listOf(row), model.items())
		val home = row.path!![0]
		assertEquals("the hit followed its row out of the chapter",
			home.id, row.parentId)
		assertEquals("the hit chain is the open container plus the row",
			listOf(home.id, row.item.id), row.path!!.map { it.id })
	}

	@Test
	fun `a breadcrumb click from shown results lands on the clicked crumb`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		model.onItemClick(c1)
		model.openSearchField()
		model.onQuerySubmit("w1")

		// back out of the results' hit, then click the subject crumb —
		// the stacked search hop must not swallow the level
		model.onBackRequested()

		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)
		// the click lands on the subject crumb, not the hit's chapter
		model.breadcrumbClick(0)

		// the subject itself (crumb 0), one level up from the hit
		assertEquals(listOf("A"), model.history.path.map { it.name })
		assertEquals(SearchUi.OFF, model.state.value.searchUi)
	}

	@Test
	fun `a swipe with nowhere to go stays put`() {
		seed()
		val rootModel = vm()
		rootModel.openSibling(1)
		assertTrue("the subjects root has no siblings", rootModel.history.path.isEmpty())

		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.openSibling(1)
		assertTrue("a search has no siblings", model.history.path.map { it.name } == listOf("A"))
	}

	@Test
	fun `a swipe onto a bare root word stays put`() {
		seed()
		// a bare word under the root (a mis-anchored import) — opening it
		// would freeze the list on the stale pane, its read is a leaf's null
		val bare = ItemRepository.legacyInsert("bare", null, ItemKind.WORD, 0, 0)
		val trl = ItemRepository.legacyInsert("tr", null, ItemKind.TRANSLATION, 0, 0)
		ItemRepository.legacyLinkAtEnd(ROOT_ID, bare)
		ItemRepository.legacyLinkAtEnd(bare, trl)
		val model = vm()

		// B is the last subject — its next sibling is the bare word
		model.onItemClick(model.items().single { it.item.name == "B" })
		model.openSibling(1)

		assertEquals("the swipe refused the non-chapter sibling",
			listOf("B"), model.history.path.map { it.name })
	}

	@Test
	fun `a swipe cancels the selection and switches the chapter`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 1)
		val c1 = ItemRepository.children(aId).first { it.name == "c1" }
		ItemRepository.createWord(c1.id, "w", null, listOf("t" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single(), true)

		model.openSibling(1)

		assertEquals("the swipe switched to the sibling chapter",
			listOf("A", "c2"), model.history.path.map { it.name })
		assertEquals("the selection left with the swipe", 0, model.selectionCount())
		assertEquals("the switched chapter shows its own content", 0, model.items().size)
	}

	@Test
	fun `a swipe keeps an armed move alive across the switch`() {
		val (aId, _) = seed()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val c1 = ItemRepository.children(aId).first { it.name == "c1" }
		ItemRepository.createWord(c1.id, "w", null, listOf("t" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single(), true)
		model.move(false)

		model.openSibling(1)

		assertEquals(listOf("A", "c2"), model.history.path.map { it.name })
		assertTrue("the armed move survived the switch", model.pasteArmed)

		// the paste lands on the switched chapter
		model.doPaste()

		assertFalse(model.pasteArmed)
		assertEquals(listOf("w"), ItemRepository.children(c2.id).map { it.name })
	}

	@Test
	fun `back clears a search selection before it leaves the results`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")

		model.setSelected(model.items().single(), true)

		// from the deepest layer out — the selection leaves, then the search
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals("the selection left, the results stayed", SearchUi.TYPING, model.state.value.searchUi)
		assertFalse(model.items().any { it.selected })

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals("the field closed onto its results", SearchUi.RESULTS, model.state.value.searchUi)

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.OFF, model.state.value.searchUi)
	}

	@Test
	fun `the root exit needs a second press inside the window`() {
		val model = vm()

		assertEquals(ExplorerViewModel.BackOutcome.EXIT_HINT, model.onBackRequested())
		now += 2_000
		assertEquals("still inside the window", ExplorerViewModel.BackOutcome.EXIT, model.onBackRequested())

		now += 4_000
		assertEquals("the window closed", ExplorerViewModel.BackOutcome.EXIT_HINT, model.onBackRequested())
	}

	@Test
	fun `an armed paste arms and cancels cleanly`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		model.setSelected(c1, true)
		model.move(false)

		assertTrue(model.pasteArmed)
		assertFalse("arming the paste cleared the selection", model.items().any { it.selected })

		model.cancelPaste()
		assertFalse(model.pasteArmed)
	}

	@Test
	fun `splitting an existing translation replaces it`() {
		val (aId, _) = seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		val word = ItemRepository.createWord(c1.item.id, "w", null, listOf("abc" to null))
		val trl = ItemRepository.translations(word.id).single()
		val row = ItemRow(word, c1.item.id, 1, false)

		// the editor turns the existing "abc" row into a two-part split
		val result = CreatorResult(
			"w", "",
			listOf(TranslationValue("xyz\\def", "", trl)), emptyList())
		assertTrue(model.commitWordEdit(row, result))

		// the split replaced the row — not two new ones beside the original
		assertEquals(listOf("xyz", "def"),
			ItemRepository.translations(word.id).map { it.name })
	}

	@Test
	fun `an armed paste keeps the list frozen through a browse load`() {
		seed()
		val model = vmIn("A")
		assertTrue(model.state.value.reorderable)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)
		assertFalse("the paste freezes the reorder", model.state.value.reorderable)

		// a browse publish (a navigation landing while the paste is armed)
		// must not undo the freeze — the drag would persist an order the
		// paste is about to change
		model.load(ArrayList(), isSearch = false)
		assertFalse("the freeze survives the publish", model.state.value.reorderable)

		model.cancelPaste()
		assertTrue(model.state.value.reorderable)
	}

	@Test
	fun `an armed paste blocks the drag-select over the results`() {
		val (aId, _) = seed()
		val model = vmIn("A")
		val word = ItemRepository.createWord(aId, "w", null, listOf("t" to null))
		val row = ItemRow.ofSearchHit(word, listOf(ItemRepository.get(aId)!!), 1, emptyList())
		model.load(listOf(row), isSearch = true)
		model.setSelected(model.items().single { it.item.id == word.id }, true)
		model.move(false)
		assertTrue(model.pasteArmed)

		// the range drag over the still-shown results selects nothing
		model.selectRange(0, 0)
		assertFalse("no row selected under the paste strip",
			model.items().any { it.selected })
	}

	@Test
	fun `doPaste moves the selected row within its subject`() {
		val (aId, _) = seed()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)

		// the paste target is the open chapter, the way the user browses to it
		model.onItemClick(model.items().single { it.item.name == "c2" })
		model.doPaste()

		assertFalse(model.pasteArmed)
		assertEquals(listOf("c1"), ItemRepository.children(c2.id).map { it.name })
	}

	@Test
	fun `doPaste refuses a move into another subject and reports it`() {
		val (aId, bId) = seed()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)

		// walk out of A and open B, the way the user browses to the target
		model.onBackRequested()
		model.onItemClick(model.items().single { it.item.id == bId })
		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			model.doPaste()
			withTimeout(1_000) {
				assertEquals(
					GlobalDependencies.UiMessage.Notice(UiNotice.PasteOtherSubject), notice.await())
			}
		}

		assertFalse(model.pasteArmed)
		assertEquals("the row stayed in its subject",
			listOf("c1"), ItemRepository.children(aId).map { it.name })
		assertTrue(ItemRepository.children(bId).isEmpty())
	}

	@Test
	fun `doPaste merges a cut subject into the open subject`() {
		val (aId, bId) = seed()
		val model = vm()
		model.setSelected(model.items().single { it.item.id == bId }, true)
		model.move(false)
		model.onItemClick(model.items().single { it.item.id == aId })
		model.doPaste()

		assertFalse(model.pasteArmed)
		assertEquals(listOf("A"), ItemRepository.subjects().map { it.name })
		// the moved subject became a chapter of A, next to A's own
		assertEquals(listOf("c1", "B"), ItemRepository.children(aId).map { it.name })
		assertEquals("the open list re-read with the merged chapter",
			listOf("c1", "B"), model.items().map { it.item.name })
	}

	@Test
	fun `a paste at the subjects root reports that there is no target`() {
		seed()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)
		model.onBackRequested()
		assertTrue("the walk reached the subjects root", model.history.path.isEmpty())

		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			model.doPaste()
			withTimeout(1_000) {
				assertEquals(GlobalDependencies.UiMessage.Notice(UiNotice.PasteNoTarget), notice.await())
			}
		}
		assertFalse("the refused paste disarmed anyway", model.pasteArmed)
	}

	@Test
	fun `a word found through its translation shows as the hit`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val model = vmIn("A")
		model.openSearchField()
		// the query matches the translation, not the word — the WORD
		// is the hit, carrying its own row and path
		model.onQuerySubmit("t1")

		val hit = model.items().single()
		assertEquals("the hit row is the word", ItemKind.WORD, hit.item.type)
		assertEquals("w1", hit.item.name)

		model.onSearchHitClick(hit.toUi())

		// the click flipped it to its translations — the search never left
		assertTrue("the hit flipped to its translations", hit.flipped)
		assertEquals(listOf("t1"), hit.translationLines.map { it.name })
		assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
		assertEquals(SearchUi.TYPING, model.state.value.searchUi)
		assertEquals(listOf(hit), model.items())

		// the second click toggles back to the word's name
		model.onSearchHitClick(hit.toUi())
		assertFalse(hit.flipped)
	}

	@Test
	fun `go to location opens the selected hit's directory`() {
		val (model, row) = searchedWord()
		model.setSelected(row, true)

		model.onGoToAction()

		assertEquals("the open left the search", SearchUi.OFF, model.state.value.searchUi)
		assertEquals(listOf("A", "c1"), model.state.value.breadcrumbs)
		assertTrue("the hit's container shows it", model.items().any { it.item.id == row.item.id })
	}

	@Test
	fun `go to location with several homes shows the source picker`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// the word's second home — the go-to picker's second source
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("w1")
		model.setSelected(model.items().single(), true)

		model.onGoToAction()

		val request = runBlocking { withTimeout(100) { model.requests.first() } }
		assertTrue("several homes pick the source", request is MainExplorerViewModel.Request.PickSource)
		val paths = (request as MainExplorerViewModel.Request.PickSource).paths
		assertEquals(listOf(listOf("A", "c1"), listOf("A", "c2")),
			paths.map { chain -> chain.map { it.name } })
	}

	@Test
	fun `onReferenceClick with a single source opens the source container`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// the word's second home — the reference jump's target
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })

		model.onReferenceClick(model.items().single { it.item.name == "w1" }.toUi())

		assertEquals("the jump landed on the word's other home",
			listOf("A", "c2"), model.history.path.map { it.name })
		assertEquals("the landing is the browse mode", SearchUi.OFF, model.state.value.searchUi)
		assertTrue("the other container shows the word",
			model.items().any { it.item.id == word.id })
	}

	@Test
	fun `refreshSearchRows re-reads the shown rows in place`() {
		val (model, row) = searchedWord()
		ItemRepository.rename(row.item.id, "renamed")

		model.refreshSearchRows()

		assertEquals("renamed", model.items().single().item.name)
		assertTrue("the refresh left the search list",
			model.listState.value.variant == ExplorerRowVariant.Search)
	}

	@Test
	fun `a submitted search lands its hits in the list`() {
		val (aId, _) = seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		ItemRepository.createWord(c1.item.id, "das Haus", null, listOf("house" to null))

		model.openSearchField()
		model.onQuerySubmit("Haus")

		assertEquals("the run landed under the open field", SearchUi.TYPING, model.state.value.searchUi)
		assertEquals(listOf("das Haus"), model.items().map { it.item.name })
		assertTrue("the strip shows the result count",
			(model.state.value.info as InfoBar.Search).count == 1)
	}

	@Test
	fun `the search field stays closed at the root`() {
		seed()
		val model = vm()

		model.openSearchField()

		assertEquals("the run has no container to walk — the field never opened",
			SearchUi.OFF, model.state.value.searchUi)
	}

	@Test
	fun `the search field opens over an active selection`() {
		seed()
		val model = vmIn("A")
		model.onItemLongClick(model.items().single { it.item.name == "c1" })

		model.openSearchField()

		assertEquals(SearchUi.TYPING, model.state.value.searchUi)
		assertTrue("the selection stays as it was", model.listState.value.selectionActive)
	}

	@Test
	fun `restored results come back unselected`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.setSelected(model.items().single(), true)

		// the hit's chapter opens (the search suspends), then back restores the results
		model.onSearchHitClick(model.items().single { it.item.name == "c1" }.toUi())
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)

		assertFalse("the selection belonged to the left list — none carries over",
			model.items().any { it.selected })
	}

	// ------------------------------------------------------------ delete and flip

	@Test
	fun `onDeleteAction asks the view to confirm the selected rows`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 1)
		ItemRepository.createChapter(aId, "c3", null, 2)
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		val c2 = model.items().single { it.item.name == "c2" }

		// nothing is selected yet — a request here would surface as the
		// first flow value below and fail the doomed assertion
		model.onDeleteAction()

		model.setSelected(c1, true)
		model.setSelected(c2, true)
		model.onDeleteAction()
		val request = runBlocking { withTimeout(100) { model.requests.first() } }

		// the rows travel to the dialog, not only their ids — the view
		// reports the very objects back in performDelete. c3 stayed
		// unselected — only the selected pair is doomed, not the container
		assertEquals(listOf(c1, c2), (request as MainExplorerViewModel.Request.ConfirmDelete).doomed)
	}

	@Test
	fun `performDelete removes the selected rows and renumbers the rest`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 1)
		ItemRepository.createChapter(aId, "c3", null, 2)
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		val c3 = model.items().single { it.item.name == "c3" }

		model.performDelete(listOf(c1, c3))

		assertEquals(listOf("c2"), model.items().map { it.item.name })
		assertEquals("the renumbering closed the gap", listOf(1), model.items().map { it.position })
		assertEquals(listOf("c2"), ItemRepository.children(aId).map { it.name })
	}

	@Test
	fun `performDelete under a progress title removes the rows too`() {
		val (aId, bId) = seed()
		val model = vm()
		val a = model.items().single { it.item.id == aId }

		// a whole subject's graph is the blocking case the bar exists for
		model.performDelete(listOf(a), "deleting")

		assertEquals(listOf("B"), model.items().map { it.item.name })
		assertEquals(listOf(bId), ItemRepository.subjects().map { it.id })
	}

	@Test
	fun `a delete cascade refreshes a search list past its selection`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "cw", null, listOf("t" to null))
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c")
		assertEquals(listOf("c1", "cw"), model.items().map { it.item.name })

		model.performDelete(listOf(model.items().single { it.item.name == "c1" }))

		// the cascade took the hit under the chapter too — the refresh drops
		// every dead row, not only the selected one
		assertTrue("the cascade emptied the results", model.items().isEmpty())
		assertTrue("the results stay a search list",
			model.listState.value.variant == ExplorerRowVariant.Search)
		assertEquals("the strip followed the dropped rows",
			0, (model.state.value.info as InfoBar.Search).count)
	}

	@Test
	fun `flipSelected swaps the selected word with its translation`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.name == "w1" }, true)

		model.flipSelected()

		// the translation took the word's place in the chapter
		assertEquals(listOf("t1"), model.items().map { it.item.name })
		assertFalse("the flip consumed the selection", model.items().any { it.selected })
		assertEquals("the old word translates the new one",
			listOf("w1"), ItemRepository.translations(model.items().single().item.id).map { it.name })
	}

	@Test
	fun `delete in search clears every location inside the searched region`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// the word's second home — inside the searched subject
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("w1")
		model.setSelected(model.items().single(), true)

		model.performDelete(model.items().filter(ItemRow::selected))

		assertTrue("the subject-level search cleared both homes",
			ItemRepository.children(c1.id).none { it.id == word.id } &&
				ItemRepository.children(c2.id).none { it.id == word.id })
		assertTrue("the hit left the results", model.items().isEmpty())
	}

	@Test
	fun `a deeper search deletes only the occurrences inside its chapter`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(c1.id, "c2", null, 0)
		val c3 = ItemRepository.createChapter(aId, "c3", null, 1)
		val word = ItemRepository.createWord(c2.id, "w1", null, listOf("t1" to null))
		// the word's other home — outside the searched chapter, inside the subject
		ItemRepository.legacyLinkAtEnd(c3.id, word.id)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.openSearchField()
		model.onQuerySubmit("w1")
		model.setSelected(model.items().single(), true)

		model.performDelete(model.items().filter(ItemRow::selected))

		assertTrue("the inner occurrence is gone",
			ItemRepository.children(c2.id).none { it.id == word.id })
		assertTrue("the outside occurrence survives",
			ItemRepository.children(c3.id).any { it.id == word.id })
		assertTrue("the hit stays in the results", model.items().isNotEmpty())
	}

	@Test
	fun `a word shared outside the selection blocks the flip with a notice`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// a plain second home — flipping w1 would change it there too
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.name == "w1" }, true)

		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			model.flipSelected()
			withTimeout(1_000) {
				assertEquals(
					GlobalDependencies.UiMessage.Notice(UiNotice.FlipConflicts(listOf("w1 — c2"))),
					notice.await())
			}
		}

		// the refused flip left the list and the selection for a retry
		assertEquals(listOf("w1"), model.items().map { it.item.name })
		assertTrue(model.items().single().selected)
	}

	@Test
	fun `a selected chapter flips the words below it`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.flipSelected()

		// the chapter row stays in the parent — opening it shows the flipped
		// words in place
		assertEquals(listOf("c1"), model.items().map { it.item.name })
		model.onItemClick(model.items().single())
		assertEquals(listOf("t1", "t2"), model.items().map { it.item.name })
	}

	// ------------------------------------------------------------ sorting

	@Test
	fun `an alphabetical sort reorders the shown rows renumbered`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "beta", null, 1)
		ItemRepository.createChapter(aId, "alpha", null, 2)
		val model = vmIn("A")

		assertTrue(model.onMenuItemClick(MenuId.SORT_ALPHA_AZ))

		assertEquals(listOf("alpha", "beta", "c1"), model.items().map { it.item.name })
		assertEquals("the renumbering follows the new order", listOf(1, 2, 3),
			model.items().map { it.position })
		// the sort reorders the live list only — the stored order waits for
		// a drag or SORT_DEFAULT
		assertEquals(listOf("c1", "beta", "alpha"), ItemRepository.children(aId).map { it.name })
	}

	@Test
	fun `a priority sort orders the rows most urgent first`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		// stored order differs from the urgency order, so the pin cannot pass
		// on an untouched list
		val wPass = ItemRepository.createWord(c1.id, "wPass", null, listOf("t" to null))
		val wFail = ItemRepository.createWord(c1.id, "wFail", null, listOf("t" to null))
		val wNew = ItemRepository.createWord(c1.id, "wNew", null, listOf("t" to null))
		// an untested word needs a test most, a failed one follows, a passed one least
		ItemRepository.bumpCounters(listOf(wFail.id), false)
		ItemRepository.bumpCounters(listOf(wPass.id), true)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertTrue(model.onMenuItemClick(MenuId.SORT_PRIORITY))

		assertEquals(listOf("wNew", "wFail", "wPass"), model.items().map { it.item.name })
		assertEquals("the renumbering follows the new order", listOf(1, 2, 3),
			model.items().map { it.position })
		// the sort reorders the live list only
		assertEquals(listOf("wPass", "wFail", "wNew"), ItemRepository.children(c1.id).map { it.name })
	}

	@Test
	fun `the default sort reloads the stored order`() {
		val (aId, _) = seed()
		// the stored order (c1, zeta, alpha) is neither the alphabetical
		// order (alpha, c1, zeta) nor its reverse — a flip of the sorted
		// list would not reproduce it
		ItemRepository.createChapter(aId, "zeta", null, 1)
		ItemRepository.createChapter(aId, "alpha", null, 2)
		val model = vmIn("A")
		model.onMenuItemClick(MenuId.SORT_ALPHA_AZ)

		assertTrue(model.onMenuItemClick(MenuId.SORT_DEFAULT))

		assertEquals("the stored order came back",
			listOf("c1", "zeta", "alpha"), model.items().map { it.item.name })
		assertEquals(listOf(1, 2, 3), model.items().map { it.position })
	}

	@Test
	fun `a sort reorders the shown search rows renumbered`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "zw", null, listOf("t" to null))
		ItemRepository.createWord(c1.id, "aw", null, listOf("t" to null))
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("w")
		assertEquals(listOf("zw", "aw"), model.items().map { it.item.name })

		assertTrue(model.onMenuItemClick(MenuId.SORT_ALPHA_AZ))

		assertEquals(listOf("aw", "zw"), model.items().map { it.item.name })
		assertEquals(listOf(1, 2), model.items().map { it.position })
		assertTrue("the sorted results stay a search list",
			model.listState.value.variant == ExplorerRowVariant.Search)
	}

	// ------------------------------------------------------------ creation

	@Test
	fun `commitNewItem appends a new subject`() {
		seed()
		val model = vm()

		assertTrue(model.commitNewItem(RowKind.SUBJECT,
			CreatorResult("S", "", emptyList(), emptyList())))

		assertEquals(listOf("A", "B", "S"), model.items().map { it.item.name })
		assertEquals(listOf("A", "B", "S"), ItemRepository.subjects().map { it.name })
	}

	@Test
	fun `commitNewItem appends a new chapter and renumbers`() {
		val (aId, _) = seed()
		val model = vmIn("A")

		assertTrue(model.commitNewItem(RowKind.CHAPTER,
			CreatorResult("c0", "", emptyList(), emptyList())))

		assertEquals(listOf("c1", "c0"), model.items().map { it.item.name })
		assertEquals(listOf(1, 2), model.items().map { it.position })
		assertEquals("the append landed in the database too",
			listOf("c1", "c0"), ItemRepository.children(aId).map { it.name })
	}

	@Test
	fun `commitNewItem lands a new note like a chapter`() {
		val (aId, _) = seed()
		val model = vmIn("A")

		assertTrue(model.commitNewItem(RowKind.NOTE,
			CreatorResult("n1", "note text", emptyList(), emptyList())))

		assertEquals(listOf("c1", "n1"), model.items().map { it.item.name })
		val note = model.items().single { it.item.name == "n1" }
		assertEquals(ItemKind.NOTE, note.item.type)
		assertEquals("note text", note.item.description)
	}

	@Test
	fun `commitNewItem splits a multi-name word into sequential rows`() {
		val (aId, _) = seed()
		val model = vmIn("A")

		assertTrue(model.commitNewItem(RowKind.WORD, CreatorResult(
			"one\\two", "", listOf(TranslationValue("t", "", null)), emptyList())))

		assertEquals(listOf("c1", "one", "two"), model.items().map { it.item.name })
		assertEquals(listOf(1, 2, 3), model.items().map { it.position })
		assertEquals("each name links at the end in order",
			listOf("c1", "one", "two"), ItemRepository.children(aId).map { it.name })
	}

	@Test
	fun `onEditAction on a non-word row requests the item editor`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 1)
		val model = vmIn("A")
		val c2 = model.items().single { it.item.name == "c2" }
		// c1 stays unselected ahead of it — the request must carry the
		// selected row, not simply the first one in the list
		model.setSelected(c2, true)

		model.onEditAction()
		val request = runBlocking { withTimeout(100) { model.requests.first() } }

		assertEquals(c2, (request as MainExplorerViewModel.Request.EditItem).row)
	}

	// ------------------------------------------------------------ export targets

	@Test
	fun `an export without a selection carries the open container and names the file after it`() {
		seed()
		val model = vmIn("A")

		assertEquals(listOf("A"), model.exportContainers().map { it.name })

		model.onMenuItemClick(MenuId.EXPORT_WORD)
		val request = runBlocking { withTimeout(100) { model.requests.first() } }
		assertEquals("A.txt", (request as MainExplorerViewModel.Request.PickWordExport).fileName)
	}

	@Test
	fun `an export without a selection at the root carries every subject and the generic name`() {
		seed()
		// a bare word under the root (a mis-anchored import) has no wrapper
		// of its own in either format — the export skips it
		val bare = ItemRepository.legacyInsert("bare", null, ItemKind.WORD, 0, 0)
		val trl = ItemRepository.legacyInsert("tr", null, ItemKind.TRANSLATION, 0, 0)
		ItemRepository.legacyLinkAtEnd(ROOT_ID, bare)
		ItemRepository.legacyLinkAtEnd(bare, trl)
		val model = vm()

		assertEquals(listOf("A", "B"), model.exportContainers().map { it.name })

		model.onMenuItemClick(MenuId.EXPORT_WORD)
		val request = runBlocking { withTimeout(100) { model.requests.first() } }
		assertEquals("subjects.txt", (request as MainExplorerViewModel.Request.PickWordExport).fileName)
	}

	@Test
	fun `an export with a selection carries the selected chapters only`() {
		seed()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		assertEquals(listOf("c1"), model.exportContainers().map { it.name })

		model.onMenuItemClick(MenuId.EXPORT_WORD)
		val request = runBlocking { withTimeout(100) { model.requests.first() } }
		assertEquals("c1.txt", (request as MainExplorerViewModel.Request.PickWordExport).fileName)
	}
}
