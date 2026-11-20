package cz.litoj.schlmgr.integration.ui.screen.subjects

import android.os.Looper
import androidx.room.Room
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.support.setRowOptions
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.CriterionValue
import cz.litoj.schlmgr.ui.search.FilterOperator
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.InfoBar
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.SearchUi
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import cz.litoj.schlmgr.ui.viewmodel.TranslationValue
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainExplorerViewModelTest {

	private lateinit var db: ItemDatabase
	private var now = 1_000_000L

	/** The search pins run from the word side. */
	private val searchDefFlipWas = ItemRow.searchDefFlip

	/** The join cases read a flipped browse row, so the browse side is pinned too. */
	private val defFlipWas = ItemRow.defFlip

	@Before
	fun setUp() {
		setRowOptions(searchDefFlip = false, defFlip = true)
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		freshDb()
	}

	@After
	fun tearDown() {
		Dialogs.clear()
		db.close()
		ItemRepository.resetForTest()
		setRowOptions(searchDefFlip = searchDefFlipWas, defFlip = defFlipWas)
	}

	/** An empty root, so every case of a parameterized run starts from the same rows. */
	private fun freshDb() {
		if (::db.isInitialized) db.close()
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	private fun vm(): MainExplorerViewModel {
		// the repository reads run inline on an unconfined dispatcher, so the
		// construction-time load lands before the assertions
		val model = MainExplorerViewModel(NavigationHistory(), Dispatchers.Unconfined)
		// a root back press would read the wall clock through the exit window
		model.clock = { now }
		return model
	}

	private fun vmIn(subject: String): MainExplorerViewModel {
		// the model loads the current content on construction, so callers seed first
		val model = vm()
		model.onItemClick(model.items().single { it.item.name == subject && it.item.type == ItemKind.CHAPTER })
		return model
	}

	/** Opens one folder of set A, the list the paste rows are cut from. */
	private fun vmInSet(folder: String): MainExplorerViewModel {
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == folder })
		return model
	}

	/** Runs the io work queued so far, then the screen posts it made. */
	private fun settle(io: CoroutineDispatcher) {
		runBlocking { withContext(io) { } }
		shadowOf(Looper.getMainLooper()).idle()
	}

	private fun seed(): Pair<Int, Int> {
		val a = ItemRepository.createSubject("A", null)
		val b = ItemRepository.createSubject("B", null)
		ItemRepository.createChapter(a.id, "c1", null, 0)
		return a.id to b.id
	}

	/** A filter no seeded row answers to. */
	private val notesOnly = Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.NOTE))

	/** Two folders, where one word sits in both and one only in the first. */
	private fun linkedWordsSetup(): Pair<Int, Int> {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val shared = ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		ItemRepository.reference(shared.id, c2.id)
		return c1.id to c2.id
	}

	/** Builds a shown search list without an engine run, so the behaviours start there. */
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
	fun `a loaded info strip reads fresh counters instead of the historical row`() {
		seed()
		val model = vmIn("A")
		val chapter = model.items().single { it.item.name == "c1" }
		model.onItemClick(chapter)
		val word = ItemRepository.createWord(chapter.item.id, "word", null, listOf("translation" to null))
		ItemRepository.bumpWordCounters(word.id, true)
		assertEquals(0, model.history.path.last().passedTests)

		model.onBackRequested()

		val info = model.state.value.info as InfoBar.Children
		assertEquals(100, info.ratio)
	}

	@Test
	fun `back clears the selection before it pops a path level`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		model.setSelected(c1, true)
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		// one press does one thing: the selection is gone, the path is not
		assertFalse(model.items().any { it.selected })

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertTrue("the back press left the subject", model.history.path.isEmpty())
	}

	/** One drag: the words of the list, the rows it picks, its drop slot, the order it leaves. */
	private class Drag(val words: Int, val picked: List<Int>, val from: Int, val to: Int, val shown: List<String>)

	@Test
	fun `a reorder moves every selected row into its slot`() {
		val drags = mapOf(
			"a run of two at the top" to Drag(3, listOf(0, 1), 0, 1, listOf("w3", "w1", "w2")),
			"every other row of five" to Drag(5, listOf(0, 2, 4), 4, 2, listOf("w2", "w4", "w1", "w3", "w5")),
		)

		for ((case, drag) in drags) {
			freshDb()
			val (c1, _) = linkedWordsSetup()
			repeat(drag.words - 2) { i ->
				ItemRepository.createWord(c1, "w${i + 3}", null, listOf("t${i + 3}" to null))
			}
			val model = vmInSet("c1")
			val rows = model.items()
			assertEquals("$case starts in its shown order",
				List(drag.words) { "w${it + 1}" }, rows.map { it.item.name })
			drag.picked.forEach { model.setSelected(rows[it], true) }

			model.moveRow(drag.from, drag.to)

			assertEquals("$case gathers its rows without a gap",
				drag.shown, model.items().map { it.item.name })
		}
	}

	@Test
	fun `a custom parent sort survives opening a child and returning`() {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "alpha", null)
		ItemRepository.createChapter(a.id, "zulu", null)
		ItemRepository.createChapter(a.id, "mid", null)
		val model = vmIn("A")
		model.onMenuItemClick(MenuId.SORT_ALPHA_ZA)
		assertEquals(listOf("zulu", "mid", "alpha"), model.items().map { it.item.name })

		model.onItemClick(model.items().single { it.item.name == "zulu" })
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())

		assertEquals(listOf("zulu", "mid", "alpha"), model.items().map { it.item.name })
	}

	@Test
	fun `siblings advance and retreat in the custom parent order`() {
		val a = ItemRepository.createSubject("A", null)
		ItemRepository.createChapter(a.id, "alpha", null)
		ItemRepository.createChapter(a.id, "zulu", null)
		ItemRepository.createChapter(a.id, "mid", null)
		val model = vmIn("A")
		model.onMenuItemClick(MenuId.SORT_ALPHA_ZA)
		model.onItemClick(model.items().single { it.item.name == "zulu" })

		model.openSibling(1)
		assertEquals("mid", model.history.path.last().name)
		model.openSibling(-1)
		assertEquals("zulu", model.history.path.last().name)
	}

	/** One return to the stored order: the sort it comes back from, the rows the view stores. */
	private class Stored(val awayFrom: Int, val stored: List<String>)

	@Test
	fun `the original order reloads the stored order`() {
		val returns = mapOf(
			"a descending sort before it" to Stored(MenuId.SORT_ALPHA_ZA, listOf("alpha", "zulu", "mid")),
			"an ascending sort before it" to Stored(MenuId.SORT_ALPHA_AZ, listOf("alpha", "zulu", "mid")),
		)

		for ((case, back) in returns) {
			freshDb()
			val a = ItemRepository.createSubject("A", null)
			for (name in back.stored) ItemRepository.createChapter(a.id, name, null)
			val model = vmIn("A")
			assertTrue(model.onMenuItemClick(back.awayFrom))
			assertNotEquals("$case hides the stored order", back.stored, model.items().map { it.item.name })

			assertTrue(model.onMenuItemClick(MenuId.SORT_DEFAULT))

			assertEquals("$case brings the stored order back",
				back.stored, model.items().map { it.item.name })
			assertEquals("$case renumbers the rows it brings back",
				listOf(1, 2, 3), model.items().map { it.position })
		}
	}

	@Test
	fun `a long-press starts the selection and a second press keeps it`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		// the list is reorderable, and the press still selects, so selection entry
		// must not depend on the drag mode the same press arms
		model.onItemLongClick(c1)
		assertTrue(c1.selected)
		assertTrue(model.listState.value.selectionActive)
		// a selected row keeps its mark, since the same press may move it
		model.onItemLongClick(c1)
		assertTrue(c1.selected)
	}

	@Test
	fun `the clear filter entry leaves the typed text searching`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.applyFilter(notesOnly)
		assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)

		model.onMenuItemClick(MenuId.CLEAR_FILTER)

		assertEquals("the text keeps its results", listOf("c1"), model.items().map { it.item.name })
		assertEquals("the strip names no filter", emptyList<Criterion>(), (model.state.value.info as InfoBar.Search).criteria)
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
	fun `the import door asks for a file`() {
		seed()
		val model = vmIn("A")

		// the New centre, the More row and the picker all land on this one door
		assertTrue(model.onMenuItemClick(MenuId.IMPORT_FILE))
		val request = runBlocking { withTimeout(100) { model.requests.first() } }

		assertEquals(MainExplorerViewModel.Request.PickImportFile, request)
	}

	/** One submitted query: its text, the rows it lands, the count its strip shows. */
	private class Run(val query: String, val shown: List<String>, val count: Int)

	@Test
	fun `a search run counts its own hits, not the children of the list`() {
		val runs = mapOf(
			"a query that misses" to Run("nothing-matches-this", emptyList(), 0),
			"a query that hits" to Run("Haus", listOf("das Haus"), 1),
		)

		for ((case, run) in runs) {
			freshDb()
			seed()
			val model = vmIn("A")
			val c1 = model.items().single { it.item.name == "c1" }
			ItemRepository.createWord(c1.item.id, "das Haus", null, listOf("house" to null))
			model.openSearchField()

			model.onQuerySubmit(run.query)

			assertEquals("$case landed its rows under the open field",
				run.shown, model.items().map { it.item.name })
			assertEquals("$case keeps the field open", SearchUi.TYPING, model.state.value.searchUi)
			val info = model.state.value.info
			assertTrue("$case shows the search strip", info is InfoBar.Search)
			assertEquals("$case counts its own hits", run.count, (info as InfoBar.Search).count)
		}
	}

	@Test
	fun `a filter replaces the results of the typed text`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")
		assertEquals(1, model.items().size)

		model.applyFilter(notesOnly)

		assertTrue("the old results do not stay", model.items().isEmpty())
		val info = model.state.value.info as InfoBar.Search
		assertEquals("the strip names the filter", listOf(notesOnly), info.criteria)
		model.refreshInfo()
		assertEquals("a count refresh keeps the filter",
			listOf(notesOnly), (model.state.value.info as InfoBar.Search).criteria)
	}

	@Test
	fun `a filter runs without any typed text`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()

		model.applyFilter(Criterion(SearchTag.TYPE, FilterOperator.IS, CriterionValue.Kind(ItemKind.CHAPTER)))

		assertEquals(listOf("c1"), model.items().map { it.item.name })
	}

	@Test
	fun `dropping the last filter leaves the typed text`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")
		model.applyFilter(notesOnly)
		assertTrue(model.items().isEmpty())

		model.dropFilter(SearchTag.TYPE)

		assertEquals(listOf("c1"), model.items().map { it.item.name })
		assertEquals(emptyList<Criterion>(), (model.state.value.info as InfoBar.Search).criteria)
	}

	@Test
	fun `dropping the filter of a textless search ends it`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.applyFilter(notesOnly)

		model.dropFilter(SearchTag.TYPE)

		assertTrue("the browse list is back", model.items().isNotEmpty())
		assertEquals(ExplorerRowVariant.Browse, model.listState.value.variant)
		assertEquals(SearchUi.OFF, model.state.value.searchUi)
	}

	@Test
	fun `the root strip counts the subjects`() {
		seed()

		assertEquals(InfoBar.Root(2), vm().state.value.info)
	}

	@Test
	fun `the strip counts the open rows per kind and a kind without rows stays absent`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 0)
		ItemRepository.createWord(aId, "w1", null, listOf("t1" to null))
		val model = vmIn("A")

		val kinds = (model.state.value.info as InfoBar.Children).kinds
		assertEquals(mapOf(ItemKind.CHAPTER to 2, ItemKind.WORD to 1), kinds)
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

	/** Where a search would start: its list, whether a move is armed, the block it reads. */
	private class Place(val atRoot: Boolean, val armed: Boolean, val blocked: Int)

	@Test
	fun `only the subjects root blocks a search`() {
		val places = mapOf(
			"the subjects root" to Place(true, false, R.string.unavailable_search_root),
			"an open subject list" to Place(false, false, 0),
			"a list with an armed move" to Place(false, true, 0),
		)

		for ((case, place) in places) {
			freshDb()
			seed()
			val model = if (place.atRoot) vm() else vmIn("A")
			if (place.armed) {
				model.setSelected(model.items().single(), true)
				model.move(false)
				assertTrue("$case arms the cut", model.pasteArmed)
			}

			assertEquals("$case reads its block", place.blocked, model.searchBlockedReason)
			model.openSearchField()
			assertEquals("$case opens its field on its block alone",
				place.blocked == 0, model.state.value.searchUi != SearchUi.OFF)
			if (place.blocked != 0) continue
			model.onQuerySubmit("c1")
			assertEquals("$case keeps the search available",
				listOf("c1"), model.items().map { it.item.name })
		}
	}

	@Test
	fun `a breadcrumb click from shown results lands on the clicked crumb`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }
		model.onItemClick(c1)
		model.openSearchField()
		model.onQuerySubmit("w1")

		// back out of the hit in the results, then click the subject crumb,
		// because the stacked search hop must not swallow the level
		model.onBackRequested()

		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)
		model.breadcrumbClick(0)

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
		// a bare word under the root, a mis-anchored import, would freeze the list
		// on the stale pane when opened, because its read is the null of a leaf
		val bare = ItemRepository.legacyInsert("bare", null, ItemKind.WORD, 0, 0)
		val trl = ItemRepository.legacyInsert("tr", null, ItemKind.TRANSLATION, 0, 0)
		ItemRepository.legacyLinkAtEnd(ROOT_ID, bare)
		ItemRepository.legacyLinkAtEnd(bare, trl)
		val model = vm()

		// the subject B is the last one, so its next sibling is the bare word
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

		model.doPaste()

		assertFalse(model.pasteArmed)
		assertEquals(listOf("w"), ItemRepository.children(c2.id).map { it.name })
	}

	@Test
	fun `every landing on a container asks for its own anchor`() {
		seed()
		val model = vmIn("A")
		val c1 = model.items().single { it.item.name == "c1" }

		model.onItemClick(c1)

		assertEquals("a fresh container lands at the top", 0, model.listState.value.anchor?.index)
		assertEquals(0, model.listState.value.anchor?.offset)
		assertEquals("the state carries the opened container", c1.item.id, model.listState.value.containerId)
		val first = model.listState.value.anchor

		model.onBackRequested()
		model.onItemClick(c1)

		assertNotEquals("a repeated landing is a new anchor request", first, model.listState.value.anchor)
	}

	@Test
	fun `the drawer press walks the back ladder straight to the root`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w", null, listOf("t" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single(), true)
		assertEquals(listOf("A", "c1"), model.history.path.map { it.name })

		model.goToRoot()

		assertEquals("one press clears the selection too", 0, model.selectionCount())
		assertTrue("the path drops back to the root", model.history.path.isEmpty())
		assertTrue("the breadcrumbs clear", model.state.value.breadcrumbs.isEmpty())
		// the root list loads, with both subjects
		assertEquals(listOf("A", "B"), model.items().map { it.item.name })
	}

	@Test
	fun `the drawer press keeps an armed paste alive`() {
		seed()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)

		model.goToRoot()

		assertTrue("the armed paste rode along", model.pasteArmed)
		assertTrue(model.history.path.isEmpty())
		// the root opens no container, so the paste has no target there
		assertEquals(R.string.fail_paste_target, model.pasteBlockedReason)
	}

	@Test
	fun `a row the open list already holds blocks the paste, a reference too`() {
		for (reference in listOf(false, true)) {
			freshDb()
			seed()
			val kind = if (reference) "reference" else "cut"
			val model = vmIn("A")
			model.setSelected(model.items().single { it.item.name == "c1" }, true)
			model.move(reference)

			assertEquals("the open list already holds the $kind row",
				R.string.fail_paste_present, model.pasteBlockedReason)

			model.onItemClick(model.items().single { it.item.name == "c1" })

			assertEquals("the $kind row is its own content",
				R.string.fail_paste_inside, model.pasteBlockedReason)

			model.onBackRequested()

			assertEquals("the walk back out holds the $kind row again",
				R.string.fail_paste_present, model.pasteBlockedReason)
		}
	}

	@Test
	fun `a paste to a folder of the open set runs the button`() {
		val (aId, _) = seed()
		ItemRepository.createChapter(aId, "c2", null, 1)
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)

		model.onItemClick(model.items().single { it.item.name == "c2" })

		assertEquals("the sibling folder takes the row", 0, model.pasteBlockedReason)
	}

	@Test
	fun `a batch the target only partly holds runs its button and names what stayed`() {
		val (_, c2Id) = linkedWordsSetup()
		val model = vmInSet("c1")
		model.setSelected(model.items().single { it.item.name == "w1" }, true)
		model.setSelected(model.items().single { it.item.name == "w2" }, true)
		model.move(false)
		assertEquals("the open list holds both rows",
			R.string.fail_paste_present, model.pasteBlockedReason)

		model.onBackRequested()
		model.onItemClick(model.items().single { it.item.name == "c2" })

		assertEquals("one row still has to move", 0, model.pasteBlockedReason)

		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			model.doPaste()
			withTimeout(1_000) {
				assertEquals(
					GlobalDependencies.UiMessage.Notice(UiNotice.PasteAlreadyPresent), notice.await())
			}
		}

		assertEquals("the new row moved and the linked one stayed",
			listOf("w1", "w2"), ItemRepository.children(c2Id).map { it.name }.sorted())
	}

	@Test
	fun `the block read answers on its own thread`() {
		val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		try {
			seed()
			val model = MainExplorerViewModel(NavigationHistory(), io)
			model.clock = { now }
			settle(io)
			model.onItemClick(model.items().single { it.item.name == "A" })
			settle(io)
			model.setSelected(model.items().single { it.item.name == "c1" }, true)
			model.move(false)
			settle(io)
			assertEquals("the open list already holds the cut row",
				R.string.fail_paste_present, model.pasteBlockedReason)

			model.onItemClick(model.items().single { it.item.name == "c1" })
			settle(io)
			assertEquals(R.string.fail_paste_inside, model.pasteBlockedReason)

			model.goToRoot()
			assertEquals("the root answers without a read",
				R.string.fail_paste_target, model.pasteBlockedReason)
			// the walk of the left container is cancelled, so it adds no answer
			settle(io)
			assertEquals(R.string.fail_paste_target, model.pasteBlockedReason)
		} finally {
			io.close()
		}
	}

	@Test
	fun `a cut row stays in its set and an armed reference crosses on purpose`() {
		val crossings = mapOf(
			"a cut" to (false to R.string.fail_paste_other_subject),
			"a reference" to (true to 0),
		)

		for ((case, crossing) in crossings) {
			val (reference, reason) = crossing
			freshDb()
			val (_, bId) = seed()
			val model = vmIn("A")
			model.setSelected(model.items().single { it.item.name == "c1" }, true)
			model.move(reference)
			model.onBackRequested()
			model.onItemClick(model.items().single { it.item.id == bId })

			assertEquals("$case reads its own answer across sets",
				reason, model.pasteBlockedReason)
		}
	}

	@Test
	fun `a cut subject stays enabled across sets`() {
		val (aId, bId) = seed()
		val model = vm()
		model.setSelected(model.items().single { it.item.id == bId }, true)
		model.move(false)
		model.onItemClick(model.items().single { it.item.id == aId })

		assertEquals("the cut subject merges across sets", 0, model.pasteBlockedReason)
	}

	@Test
	fun `a reload of the open container keeps the list in place`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onScrollPosition(3, 12)
		model.setSelected(model.items().single { it.item.name == "w1" }, true)

		model.flipSelected()

		// the reload re-anchors at the remembered place, which is the current one
		assertEquals(3, model.listState.value.anchor?.index)
		assertEquals(12, model.listState.value.anchor?.offset)
	}

	@Test
	fun `going up forgets the child state`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onScrollPosition(1, 40)
		model.flipWord(model.items().single { it.item.name == "w1" })

		model.onBackRequested()
		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertEquals("the left child starts at the top", 0, model.listState.value.anchor?.index)
		assertEquals(0, model.listState.value.anchor?.offset)
		assertTrue("the left child keeps its default side",
			model.items().single { it.item.name == "w1" }.flipped)
	}

	@Test
	fun `every parent keeps its own scroll on the way back`() {
		for (depth in listOf(1, 2)) {
			freshDb()
			var parent = ItemRepository.createSubject("A", null)
			repeat(depth) {
				// every level holds the chapter it walks into beside one to scroll past
				val down = ItemRepository.createChapter(parent.id, "down", null, 0)
				ItemRepository.createChapter(parent.id, "aside", null, 1)
				parent = down
			}
			val model = vmIn("A")
			// a level marks its own place, so a restored one cannot pass for another
			repeat(depth) { level ->
				model.onScrollPosition(level + 1, (level + 1) * 10)
				model.onItemClick(model.items().single { it.item.name == "down" })
			}

			for (level in depth downTo 1) {
				model.onBackRequested()
				assertEquals("the walk out of $depth levels restores this one",
					level, model.listState.value.anchor?.index)
				assertEquals(level * 10, model.listState.value.anchor?.offset)
			}
		}
	}

	@Test
	fun `a sibling round trip restores the flip sides`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		ItemRepository.createWord(c2.id, "w2", null, listOf("t2" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onScrollPosition(0, 40)
		model.flipWord(model.items().single { it.item.name == "w1" })

		model.openSibling(1)
		model.openSibling(-1)

		assertEquals("the sibling kept its place", 0, model.listState.value.anchor?.index)
		assertEquals(40, model.listState.value.anchor?.offset)
		assertTrue("the flip side survived the sibling round trip",
			!model.items().single { it.item.name == "w1" }.flipped)
	}

	@Test
	fun `an item edit updates its row without a reload`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createNote(c1.id, "note1", "old")
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.flipWord(model.items().single { it.item.name == "w1" })
		val step = model.listState.value.contentStep
		val note = model.items().single { it.item.name == "note1" }

		assertTrue(model.commitItemEdit(note, CreatorResult("note2", "new", emptyList(), emptyList())))

		assertEquals("the edit rebuilt no list", step, model.listState.value.contentStep)
		assertEquals("note2", model.items().single { it.item.name == "note2" }.item.name)
		assertTrue("the flip side of the other row survived",
			!model.items().single { it.item.name == "w1" }.flipped)
	}

	@Test
	fun `a word edit updates its row in place and refreshes the shared rows`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "dog", null, listOf("pes" to null))
		ItemRepository.createWord(c1.id, "hound", null, listOf("pes" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		val dog = model.items().single { it.item.name == "dog" }
		val hound = model.items().single { it.item.name == "hound" }
		model.flipWord(dog)
		val step = model.listState.value.contentStep
		// both words show one shared translation row
		val pes = ItemRepository.translations(dog.item.id).single()

		val result = CreatorResult("dog", "",
			listOf(TranslationValue("pes", "psí pes", pes)), emptyList())
		assertTrue(model.commitWordEdit(dog, result))

		assertEquals("the edit rebuilt no list", step, model.listState.value.contentStep)
		assertTrue("the flip side of the row survived", !dog.flipped)
		assertEquals("dog", dog.toShow)
		assertEquals("the shared row re-rendered its changed description",
			listOf("psí pes"), hound.translationLines.map { it.description })
	}

	@Test
	fun `a search round trip restores the view state`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onScrollPosition(2, 60)
		model.flipWord(model.items().single { it.item.name == "w1" })

		model.openSearchField()
		model.onQuerySubmit("w1")
		model.endSearch()

		assertTrue("the flip side survived the search round trip",
			!model.items().single { it.item.name == "w1" }.flipped)
		assertEquals(2, model.listState.value.anchor?.index)
		assertEquals(60, model.listState.value.anchor?.offset)
	}

	@Test
	fun `back clears a search selection before it leaves the results`() {
		seed()
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("c1")

		model.setSelected(model.items().single(), true)

		// from the deepest layer out: the selection leaves, then the search
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals("the selection left, the results stayed", SearchUi.TYPING, model.state.value.searchUi)
		assertFalse(model.items().any { it.selected })

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals("the field closed onto its results", SearchUi.RESULTS, model.state.value.searchUi)

		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.OFF, model.state.value.searchUi)
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
	fun `an armed paste keeps the list frozen through a browse load`() {
		seed()
		val model = vmIn("A")
		assertTrue(model.state.value.reorderable)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)
		assertFalse("the paste freezes the reorder", model.state.value.reorderable)

		// a navigation landing while the paste is armed must not undo the freeze,
		// because the drag would persist an order the paste is about to change
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
	fun `doPaste refuses a move into the armed rows content and reports it`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.move(false)
		model.onItemClick(model.items().single { it.item.name == "c1" })

		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			model.doPaste()
			withTimeout(1_000) {
				assertEquals(
					GlobalDependencies.UiMessage.Notice(UiNotice.PasteInsideItself), notice.await())
			}
		}

		assertFalse(model.pasteArmed)
		assertEquals("the chapter stayed in its set", listOf("c1"), ItemRepository.children(aId).map { it.name })
		assertTrue("nothing landed inside the armed chapter", ItemRepository.children(c1.id).isEmpty())
	}

	@Test
	fun `a word found through its translation shows as the hit`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val model = vmIn("A")
		model.openSearchField()
		// the query matches the translation, not the word, but the WORD is the hit,
		// carrying its own row and path
		model.onQuerySubmit("t1")

		val hit = model.items().single()
		assertEquals("the hit row is the word", ItemKind.WORD, hit.item.type)
		assertEquals("w1", hit.item.name)

		model.onItemClick(hit)

		assertTrue("the hit flipped to its translations", hit.flipped)
		assertEquals(listOf("t1"), hit.translationLines.map { it.name })
		assertEquals(ExplorerRowVariant.Search, model.listState.value.variant)
		assertEquals(SearchUi.TYPING, model.state.value.searchUi)
		assertEquals(listOf(hit), model.items())

		model.onItemClick(hit)
		assertFalse(hit.flipped)
	}

	/** One tap run: the searched words, the rows it taps, whether a double closes, the rows left flipped. */
	private class Taps(val words: Int, val tapped: List<String>, val doubles: Boolean, val flipped: List<String>)

	@Test
	fun `the double flips every word against the majority, a plain tap only its own`() {
		val runs = mapOf(
			"a majority of one" to Taps(3, listOf("w1"), true, listOf("w1", "w2", "w3")),
			"a tie of two" to Taps(2, listOf("w1"), true, emptyList()),
			"two taps apart" to Taps(3, listOf("w1", "w2"), false, listOf("w1", "w2")),
		)

		for ((case, run) in runs) {
			freshDb()
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			repeat(run.words) { i ->
				ItemRepository.createWord(c1.id, "w${i + 1}", null, listOf("xx${i + 1}" to null))
			}
			val model = vmIn("A")
			model.openSearchField()
			model.onQuerySubmit("xx")
			assertEquals("$case finds its words", run.words, model.items().size)
			// the tap of a row flips that row, the double is the list's own flip
			run.tapped.forEach { name ->
				model.onItemClick(model.items().single { it.item.name == name })
			}
			if (run.doubles) model.onItemDoubleTap()

			for (name in List(run.words) { "w${it + 1}" })
				assertEquals("$case leaves $name on ${if (name in run.flipped) "its translations" else "its name"}",
					name in run.flipped, model.items().single { it.item.name == name }.flipped)
		}
	}

	@Test
	fun `a settings toggle makes the search ignore diacritics`() {
		// the run owns no fold of its own, it only reads the stored setting per run
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "sídlo", null, listOf("byt" to null))
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("sidlo")
		assertTrue("the run made before the toggle keeps the stored setting",
			model.items().none { it.item.name == "sídlo" })
		model.endSearch()

		SettingsState(searchDiacritics = false).toggleSearchDiacritics()
		model.openSearchField()
		model.onQuerySubmit("sidlo")

		assertEquals("the next run reads what the toggle wrote",
			listOf("sídlo"), model.items().map { it.item.name })
	}

	@Test
	fun `preparing a test finishes the selection`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onItemLongClick(model.items().single { it.item.name == "w1" })
		model.setSelected(model.items().single { it.item.name == "w2" }, true)

		model.prepareTest()

		assertEquals("the test hop finished the selection", 0, model.selectionCount())
	}

	@Test
	fun `a refused test preparation keeps the selection`() {
		seed()
		val model = vm()
		model.onItemLongClick(model.items().single { it.item.name == "A" })
		model.setSelected(model.items().single { it.item.name == "B" }, true)

		model.prepareTest()

		assertEquals("the refusal kept the selection for another try", 2, model.selectionCount())
	}

	@Test
	fun `go to location lands on the selected hit and pulses it once`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		val hit = ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("w2")
		model.setSelected(model.items().single(), true)

		model.onGoToAction()

		assertEquals("the open left the search", SearchUi.OFF, model.state.value.searchUi)
		assertEquals(listOf("A", "c1"), model.state.value.breadcrumbs)
		assertTrue("the hit's container shows it", model.items().any { it.item.id == hit.id })
		assertEquals("the landing shows the hit", 1, model.listState.value.anchor?.index)
		assertEquals("the landed hit pulses", 1, model.listState.value.pulse?.index)
	}

	@Test
	fun `go to location with several homes shows the source picker`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// the second home of the word, the second source of the go-to picker
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
		// the second home of the word, the target of the reference jump
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })

		model.onReferenceClick(model.items().indexOfFirst { it.item.name == "w1" })

		assertEquals("the jump landed on the word's other home",
			listOf("A", "c2"), model.history.path.map { it.name })
		assertEquals("the landing is the browse mode", SearchUi.OFF, model.state.value.searchUi)
		assertTrue("the other container shows the word",
			model.items().any { it.item.id == word.id })
		assertNull("a reference jump does not pulse", model.listState.value.pulse)
	}

	@Test
	fun `a rename join keeps the edited row in the list it came from`() {
		for (searching in listOf(false, true)) {
			freshDb()
			val list = if (searching) "search" else "browse"
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
			val target = ItemRepository.createWord(c2.id, "plain", null, listOf("t" to null))
			val hit = ItemRepository.createWord(c1.id, "[hit", null, listOf("h" to null))
			val model = vmIn("A")
			if (searching) {
				model.openSearchField()
				model.onQuerySubmit("[")
			} else {
				model.onItemClick(model.items().single { it.item.id == c1.id })
			}
			assertEquals("the $list list starts on the edited row",
				listOf("[hit"), model.items().map { it.item.name })
			val row = model.items().single()
			val translation = ItemRepository.translations(hit.id).single()

			assertTrue(model.commitWordEdit(row, CreatorResult(
				"plain", "", listOf(TranslationValue("h", "", translation)), emptyList())))

			assertEquals("the $list list keeps the joined row in its place",
				listOf(target.id), model.items().map { it.item.id })
			assertEquals(listOf("plain"), model.items().map { it.item.name })
			assertEquals("the edit leaves its $list list a $list list", searching,
				model.listState.value.variant == ExplorerRowVariant.Search)
			if (searching) assertEquals("the query stays for the next run", "[", model.query.value)
		}
	}

	@Test
	fun `a rename join onto a shown sibling keeps the survivor row`() {
		for (searching in listOf(false, true)) {
			freshDb()
			val list = if (searching) "search" else "browse"
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			val survivor = ItemRepository.createWord(c1.id, "beta", null, listOf("t" to null))
			val edited = ItemRepository.createWord(c1.id, "meta", null, listOf("e" to null))
			val after = ItemRepository.createWord(c1.id, "other", null, listOf("o" to null))
			val model = vmIn("A")
			if (searching) {
				model.openSearchField()
				model.onQuerySubmit("eta")
			} else {
				model.onItemClick(model.items().single { it.item.id == c1.id })
			}
			val row = model.items().single { it.item.id == edited.id }
			val trl = ItemRepository.translations(edited.id).single()

			assertTrue(model.commitWordEdit(row, CreatorResult(
				"beta", "", listOf(TranslationValue("e", "", trl)), emptyList())))

			// the query matches the two joined words only
			val expected = if (searching) listOf(survivor.id) else listOf(survivor.id, after.id)
			assertEquals("the $list list shows the joined word once",
				expected, model.items().map { it.item.id })
			assertEquals("the $list list renumbers behind the row that left",
				expected.indices.map { it + 1 }, model.items().map { it.position })
			assertEquals("the survivor keeps its name",
				listOf("beta") + if (searching) emptyList() else listOf("other"),
				model.items().map { it.item.name })
		}
	}

	@Test
	fun `a rename join onto a shown sibling re-renders the survivor row`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val survivor = ItemRepository.createWord(c1.id, "beta", null, listOf("t" to null))
		val edited = ItemRepository.createWord(c1.id, "meta", null, listOf("e" to null))
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.id == c1.id })
		val row = model.items().single { it.item.id == edited.id }
		val trl = ItemRepository.translations(edited.id).single()

		assertTrue(model.commitWordEdit(row, CreatorResult(
			"beta", "", listOf(TranslationValue("e", "", trl)), emptyList())))

		assertEquals("the survivor row shows the merged word",
			listOf("e", "t"), model.items().single { it.item.id == survivor.id }
				.translationLines.map { it.name }.sorted())
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
	fun `a search result shows only the path below the search root`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val inner = ItemRepository.createChapter(c1.id, "inner", null, 0)
		ItemRepository.createWord(inner.id, "nest", null, listOf("hnízdo" to null))
		val model = vmIn("A")
		model.openSearchField()
		model.onQuerySubmit("nest")

		val hit = model.listState.value.items.single()
		assertEquals(listOf("c1", "inner"), hit.trackPath?.map { it.name })
		assertEquals("the full path still serves navigation",
			listOf("A", "c1", "inner"), model.items().single().path?.map { it.name })
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

		model.onItemClick(model.items().single { it.item.name == "c1" })
		assertEquals(ExplorerViewModel.BackOutcome.HANDLED, model.onBackRequested())
		assertEquals(SearchUi.RESULTS, model.state.value.searchUi)

		assertFalse("the selection belonged to the left list, none carries over",
			model.items().any { it.selected })
	}

	@Test
	fun `every delete door asks the view with the selected rows themselves`() {
		for (fromStrip in listOf(false, true)) {
			freshDb()
			val door = if (fromStrip) "strip" else "action"
			val (aId, _) = seed()
			ItemRepository.createChapter(aId, "c2", null, 1)
			ItemRepository.createChapter(aId, "c3", null, 2)
			val model = vmIn("A")
			val c1 = model.items().single { it.item.name == "c1" }
			val c2 = model.items().single { it.item.name == "c2" }

			// nothing is selected yet, so a request here would surface as the first
			// flow value below and fail the doomed assertion
			if (fromStrip) model.onSelectionAction(SelectionAction.DELETE) else model.onDeleteAction()

			model.setSelected(c1, true)
			model.setSelected(c2, true)
			if (fromStrip) model.onSelectionAction(SelectionAction.DELETE) else model.onDeleteAction()
			val request = runBlocking { withTimeout(100) { model.requests.first() } }

			// the rows travel to the dialog, not only their ids
			assertEquals("the $door door carries the rows and not their ids",
				listOf(c1, c2), (request as MainExplorerViewModel.Request.ConfirmDelete).doomed)
		}
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

		// the graph of a whole subject is the blocking case the bar exists for
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

		// the cascade took the hit under the chapter too, so the refresh drops
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

		assertEquals(listOf("t1"), model.items().map { it.item.name })
		assertFalse("the flip consumed the selection", model.items().any { it.selected })
		assertEquals("the old word translates the new one",
			listOf("w1"), ItemRepository.translations(model.items().single().item.id).map { it.name })
	}

	@Test
	fun `a delete in search clears the homes inside the searched region only`() {
		for (deep in listOf(false, true)) {
			freshDb()
			val region = if (deep) "chapter" else "subject"
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			val c2 = ItemRepository.createChapter(c1.id, "c2", null, 0)
			val c3 = ItemRepository.createChapter(aId, "c3", null, 1)
			val word = ItemRepository.createWord(c2.id, "w1", null, listOf("t1" to null))
			// the second home of the word, inside the subject and outside the chapter
			ItemRepository.legacyLinkAtEnd(c3.id, word.id)
			val model = vmIn("A")
			if (deep) model.onItemClick(model.items().single { it.item.name == "c1" })
			model.openSearchField()
			model.onQuerySubmit("w1")
			assertEquals("the $region search runs on one hit", 1, model.items().size)
			model.setSelected(model.items().single(), true)

			model.performDelete(model.items().filter(ItemRow::selected))

			assertTrue("the $region search took the home of its own region",
				ItemRepository.children(c2.id).none { it.id == word.id })
			assertEquals("the $region search answers for the home beyond its chapter", deep,
				ItemRepository.children(c3.id).any { it.id == word.id })
			assertEquals("the $region search leaves its results", deep, model.items().isNotEmpty())
		}
	}

	@Test
	fun `a word shared outside the selection asks through the conflict popup`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val c2 = ItemRepository.createChapter(aId, "c2", null, 1)
		val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
		// a plain second home, so flipping w1 would change it there too
		ItemRepository.legacyLinkAtEnd(c2.id, word.id)
		val model = vmIn("A")
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.name == "w1" }, true)

		model.flipSelected()

		// the conflict round asks before anything changes
		val request = runBlocking { withTimeout(100) { model.requests.first() } }
		assertTrue("the shared word opened the popup", request is MainExplorerViewModel.Request.ResolveFlipConflicts)
		val analysis = (request as MainExplorerViewModel.Request.ResolveFlipConflicts).analysis
		val conflict = analysis.conflicts.single()
		assertEquals("w1", conflict.row.name)
		assertEquals(listOf("c2"), conflict.outside.map { it.name })

		// a refused popup leaves the pending flip for a retry
		assertEquals(listOf("w1"), model.items().map { it.item.name })
		assertTrue(model.items().single().selected)

		// a blank name refuses the commit, so the popup stays open for a retry
		runBlocking {
			val notice = async(start = CoroutineStart.UNDISPATCHED) {
				GlobalDependencies.messages.first()
			}
			assertFalse(model.commitFlipResolutions(analysis,
				listOf(FlipResolution(conflict.row.id, false, " ", ""))))
			withTimeout(1_000) {
				assertEquals(GlobalDependencies.UiMessage.Notice(UiNotice.FlipBlankName), notice.await())
			}
		}

		// a keep adds the outside chapter, so the flip runs through it
		assertTrue(model.commitFlipResolutions(analysis,
			listOf(FlipResolution(conflict.row.id, false, conflict.row.name, conflict.row.description))))
		assertEquals("the flip ran after the resolution", listOf("t1"), model.items().map { it.item.name })
	}

	/** One sort: its menu row, the order it puts on screen, the list kind it runs over. */
	private class ShownSort(val menu: Int, val shown: List<String>, val searching: Boolean)

	@Test
	fun `a sort reorders the shown rows renumbered and leaves the stored order`() {
		val sorts = mapOf(
			"the alphabetical sort" to ShownSort(MenuId.SORT_ALPHA_AZ, listOf("ant", "bear", "cat"), false),
			"the priority sort" to ShownSort(MenuId.SORT_PRIORITY, listOf("bear", "cat", "ant"), false),
			"a sort of the search rows" to ShownSort(MenuId.SORT_ALPHA_AZ, listOf("ant", "bear", "cat"), true),
		)

		for ((case, sort) in sorts) {
			freshDb()
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			// the stored order is neither the alphabetical nor the urgent one, so no
			// sort of this list can pass untouched
			// a flipped word shows its translation, so both sides sort the same way here
			val failed = ItemRepository.createWord(c1.id, "cat", null, listOf("t-cat" to null))
			val passed = ItemRepository.createWord(c1.id, "ant", null, listOf("t-ant" to null))
			ItemRepository.createWord(c1.id, "bear", null, listOf("t-bear" to null))
			// an untested word needs a test most, a failed one follows, a passed one least
			ItemRepository.bumpCounters(listOf(failed.id), false)
			ItemRepository.bumpCounters(listOf(passed.id), true)
			val stored = ItemRepository.children(c1.id).map { it.name }
			val model = vmIn("A")
			model.onItemClick(model.items().single { it.item.name == "c1" })
			if (sort.searching) {
				model.openSearchField()
				model.onQuerySubmit("t-")
				assertEquals("$case sorts the rows its run found", 3, model.items().size)
			}

			assertTrue("$case runs", model.onMenuItemClick(sort.menu))

			assertEquals("$case reorders its rows", sort.shown, model.items().map { it.item.name })
			assertEquals("$case renumbers them",
				sort.shown.indices.map { it + 1 }, model.items().map { it.position })
			assertEquals("$case reorders the live list only",
				stored, ItemRepository.children(c1.id).map { it.name })
			assertEquals("$case keeps its list kind", sort.searching,
				model.listState.value.variant == ExplorerRowVariant.Search)
		}
	}

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
	fun `commitNewItem opens the new chapter`() {
		val (aId, _) = seed()
		val model = vmIn("A")

		assertTrue(model.commitNewItem(RowKind.CHAPTER,
			CreatorResult("c0", "", emptyList(), emptyList())))

		assertEquals(listOf("A", "c0"), model.history.path.map { it.name })
		val c0 = ItemRepository.children(aId).single { it.name == "c0" }
		assertEquals(c0.id, model.listState.value.containerId)
		assertTrue("the open chapter shows its empty content", model.items().isEmpty())
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
		// c1 stays unselected ahead of it, so the request must carry the selected
		// row, not simply the first one in the list
		model.setSelected(c2, true)

		model.onEditAction()
		val request = runBlocking { withTimeout(100) { model.requests.first() } }

		assertEquals(c2, (request as MainExplorerViewModel.Request.EditItem).row)
	}

	@Test
	fun `an export carries the selected rows a format can write`() {
		for (translation in listOf(false, true)) {
			freshDb()
			val (aId, _) = seed()
			val c1 = ItemRepository.children(aId).single()
			val word = ItemRepository.createWord(c1.id, "w1", null, listOf("t1" to null))
			ItemRepository.createWord(c1.id, "w2", null, listOf("t2" to null))
			val model = vmIn("A")
			val picked: List<ItemRow>
			if (translation) {
				// a search run is the only list that shows a translation row
				val line = ItemRepository.translations(word.id).single()
				val hit = ItemRow.ofSearchHit(
					line, listOf(ItemRepository.get(aId)!!, c1), 1, emptyList())
				model.load(listOf(hit), isSearch = true)
				picked = listOf(hit)
			} else {
				model.onItemClick(model.items().single { it.item.name == "c1" })
				picked = model.items()
			}
			picked.forEach { model.setSelected(it, true) }

			val written = if (translation) emptyList<String>() else listOf("w1", "w2")
			assertEquals(if (translation) "a translation row has no line of any format"
				else "every selected word carries its own lines",
				written, model.exportRows().map { it.name })
		}
	}

	@Test
	fun `an export of a selected chapter carries that chapter`() {
		seed()
		val model = vmIn("A")
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		assertEquals(listOf("c1"), model.exportRows().map { it.name })
	}

	@Test
	fun `an import target opens the path and scrolls to the bottom`() {
		val (aId, _) = seed()
		val c1 = ItemRepository.children(aId).single()
		val model = vm()

		model.openImportTarget(listOf(aId, c1.id), scrollToBottom = true)

		assertEquals(listOf("A", "c1"), model.history.path.map { it.name })
		assertEquals(1, model.listState.value.scrollToBottom)
	}
}
