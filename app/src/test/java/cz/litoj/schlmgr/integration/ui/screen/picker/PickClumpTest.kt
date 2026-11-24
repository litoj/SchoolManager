package cz.litoj.schlmgr.integration.ui.screen.picker

import androidx.room.Room
import cz.litoj.schlmgr.app.AppDependencies
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.test.TestSetupState
import cz.litoj.schlmgr.ui.viewmodel.ExplorerViewModel
import cz.litoj.schlmgr.ui.viewmodel.MenuId
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - the test-source picker lets a picked chapter and its rows stand for each other.
 * - the swap reads the direct parent only, so a deeper or higher pick stays as it is.
 * - the link picker and the armed paste keep the picks their gestures left.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PickClumpTest {

	private val testSetup get() = TestSetupState.shared

	private lateinit var db: ItemDatabase
	private lateinit var dependencies: AppDependencies
	private lateinit var subjectA: DbItem
	private lateinit var chapterC1: DbItem
	private lateinit var wordW1: DbItem
	private lateinit var chapterC3: DbItem

	/** A holds c1 with two words and c2 with one nested chapter, plus an empty c3 and subject B. */
	@Before
	fun setUp() {
		testSetup.reset()
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.setQueryExecutor(Runnable::run)
			.setTransactionExecutor(Runnable::run)
			.build()
		dependencies = AppDependencies(RuntimeEnvironment.getApplication(), db)
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		subjectA = ItemRepository.createSubject("A", null)
		ItemRepository.createSubject("B", null)
		chapterC1 = ItemRepository.createChapter(subjectA.id, "c1", null, 0)
		wordW1 = ItemRepository.createWord(chapterC1.id, "w1", null, listOf("t1" to null))
		ItemRepository.createWord(chapterC1.id, "w2", null, listOf("t2" to null))
		val c2 = ItemRepository.createChapter(subjectA.id, "c2", null, 1)
		val sub = ItemRepository.createChapter(c2.id, "sub", null, 0)
		ItemRepository.createWord(sub.id, "w3", null, listOf("t3" to null))
		chapterC3 = ItemRepository.createChapter(subjectA.id, "c3", null, 2)
	}

	@After
	fun tearDown() {
		testSetup.reset()
		db.close()
		ItemRepository.resetForTest()
	}

	private fun picker(reference: Boolean = false): PickerViewModel {
		val target = if (!reference) null
		else ItemRepository.subjects().first().let { ItemRepository.children(it.id).last() }
		return PickerViewModel(
			NavigationHistory(), Dispatchers.Unconfined, target,
			graphs = dependencies.graphQueries,
			transfer = dependencies.itemTransfer,
			userMessages = dependencies.userMessages,
		)
	}

	private fun explorer() = MainExplorerViewModel(
		NavigationHistory(), Dispatchers.Unconfined,
		graphs = dependencies.graphQueries,
		editor = dependencies.itemEditor,
		itemTransferService = dependencies.itemTransfer,
		flipper = dependencies.flipper,
		settings = dependencies.settings,
		userMessages = dependencies.userMessages,
	)

	/** Walks from the picker root into the subject A. */
	private fun enterSubject(model: PickerViewModel) {
		model.onItemClick(model.items().single { it.item.name == "A" })
	}

	/** The user's way into a picked chapter: a tap would take the pick back first. */
	private fun swipeInto(model: PickerViewModel, picked: String) {
		val open = mapOf("c2" to "c1", "c3" to "c2", "c1" to "c2").getValue(picked)
		model.onItemClick(model.items().single { it.item.name == open })
		model.openSibling(if (picked == "c1") -1 else 1)
	}

	private fun child(parentId: Int, name: String) = ItemRepository.children(parentId).single { it.name == name }

	private fun names(model: ExplorerViewModel<*>) = model.items().map { it.item.name }

	private fun marks(model: ExplorerViewModel<*>) = model.items().map { "${it.item.name}=${it.batched}" }

	@Test
	fun `a chapter opened by the swipe marks its whole view`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c2" }, true)

		swipeInto(model, "c2")

		assertEquals(listOf("A", "c2"), model.history.path.map { it.name })
		assertEquals(listOf("sub=true"), marks(model))
		assertEquals(listOf("sub"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `an untick inside the opened chapter keeps the rest and leaves the chapter unheld`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c2" }, true)
		swipeInto(model, "c2")

		model.setSelected(model.items().single { it.item.name == "sub" }, false)
		model.onBackRequested()

		assertTrue("the pick list is empty", model.batchRows().isEmpty())
		assertEquals(listOf("c1=false", "c2=false", "c3=false"), marks(model))
	}

	@Test
	fun `a wholly held chapter marks itself back in the parent list`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c2" }, true)
		swipeInto(model, "c2")
		assertEquals("the count follows the rows", 1, model.batchRows().size)

		model.onBackRequested()

		assertEquals(listOf("c1=false", "c2=true", "c3=false"), marks(model))
		assertEquals(listOf("c2"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `the count of picks follows the swap both ways`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		assertEquals(1, model.batchRows().size)

		swipeInto(model, "c1")

		assertEquals("both words joined the picks", 2, model.batchRows().size)
		model.onBackRequested()
		assertEquals("the chapter stands for them again", 1, model.batchRows().size)
	}

	@Test
	fun `a word picked deep marks no row of another view`() {
		val model = picker()
		enterSubject(model)
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.name == "w1" }, true)

		model.onBackRequested()
		model.onItemClick(model.items().single { it.item.name == "c2" })

		assertEquals("the sibling chapter shows no mark", listOf("sub=false"), marks(model))
		assertEquals(listOf("w1"), model.batchRows().map { it.item.name })
		model.onBackRequested()
		assertEquals("a gap in c1 keeps it unheld", listOf("c1=false", "c2=false", "c3=false"), marks(model))
	}

	@Test
	fun `an empty held chapter keeps its own pick when opened`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c3" }, true)

		swipeInto(model, "c3")

		assertTrue("the view holds no row to take over", names(model).isEmpty())
		assertEquals(listOf("c3"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `a search of the picker takes no pick over`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.openSearchField()
		model.onQuerySubmit("w1")

		assertEquals(listOf("w1=false"), marks(model))
		assertEquals(listOf("c1"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `a held chapter shows its search hit ticked and a tap drops the pick`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		model.openSearchField()
		model.onQuerySubmit("c1")

		assertEquals("the hit of the held chapter shows held", listOf("c1=true"), marks(model))

		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertTrue("the tap took the pick back", model.batchRows().isEmpty())
		assertEquals("the tap stayed in the search", listOf("A"), model.history.path.map { it.name })
	}

	@Test
	fun `a chapter of the search results opens without marking its rows`() {
		val model = picker()
		enterSubject(model)
		model.openSearchField()
		model.onQuerySubmit("c1")

		model.onItemClick(model.items().single { it.item.name == "c1" })

		assertEquals(listOf("A", "c1"), model.history.path.map { it.name })
		assertEquals(listOf("w1=false", "w2=false"), marks(model))
	}

	@Test
	fun `the link picker keeps its chapter pick and marks nothing inside`() {
		val model = picker(reference = true)
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c2" }, true)

		swipeInto(model, "c2")

		assertEquals(listOf("sub=false"), marks(model))
		assertEquals(listOf("c2"), model.batchRows().map { it.item.name })
		model.onBackRequested()
		assertEquals(listOf("c1=false", "c2=true", "c3=false"), marks(model))
	}

	@Test
	fun `the armed paste keeps the chapter pick when its chapter opens`() {
		val model = explorer()
		model.onItemClick(model.items().single { it.item.name == "A" })
		val c1 = model.items().single { it.item.name == "c1" }
		model.setSelected(c1, true)
		model.move(false)
		assertTrue("the cut armed the paste", model.pasteArmed)

		model.onItemClick(model.items().single { it.item.name == "c2" })
		model.openSibling(-1)

		assertEquals(listOf("w1=false", "w2=false"), marks(model))
		assertEquals(listOf("c1"), model.batchRows().map { it.item.name })
	}

	@Test
	fun `the explorer selection never swaps a chapter for its rows`() {
		val model = explorer()
		model.onItemClick(model.items().single { it.item.name == "A" })
		model.setSelected(model.items().single { it.item.name == "c1" }, true)

		model.onItemClick(model.items().single { it.item.name == "c2" })
		model.openSibling(-1)

		assertFalse("no row of the chapter took a mark",
			model.items().any { it.selected || it.batched })
		assertTrue("the gather stays empty outside a gather regime", model.batchRows().isEmpty())
	}

	@Test
	fun `expansion keeps one entry for an already picked child`() {
		val model = picker()
		enterSubject(model)
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.id == wordW1.id }, true)
		model.onBackRequested()
		model.setSelected(model.items().single { it.item.id == chapterC1.id }, true)

		swipeInto(model, "c1")

		val ids = model.batchRows().map { it.item.id }
		assertEquals(2, ids.size)
		assertEquals(setOf(wordW1.id, child(chapterC1.id, "w2").id), ids.toSet())
	}

	@Test
	fun `collapse preserves a referenced child picked under its source`() {
		ItemRepository.referenceAll(listOf(wordW1.id), chapterC3.id)
		val model = picker()
		enterSubject(model)
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.setSelected(model.items().single { it.item.id == wordW1.id }, true)
		model.onBackRequested()
		model.setSelected(model.items().single { it.item.id == chapterC3.id }, true)

		swipeInto(model, "c3")
		model.onBackRequested()

		assertEquals(setOf(wordW1.id, chapterC3.id), model.batchRows().map { it.item.id }.toSet())
		assertEquals(chapterC1.id, model.batchRows().first { it.item.id == wordW1.id }.parentId)
	}

	@Test
	fun `select closes the expanded subject as one hierarchy source`() {
		val model = picker()
		model.setSelected(model.items().single { it.item.name == "A" }, true)
		model.onItemClick(model.items().single { it.item.name == "B" })

		model.openSibling(-1)
		model.onSelect()

		assertEquals(listOf(listOf(subjectA.id)), testSetup.list.map { it.map(DbItem::id) })
		assertTrue(testSetup.hierarchyPicked())
	}

	@Test
	fun `original order reload leaves the expanded picks alone`() {
		val model = picker()
		enterSubject(model)
		model.setSelected(model.items().single { it.item.name == "c1" }, true)
		swipeInto(model, "c1")
		val before = model.batchRows()

		assertTrue(model.onMenuItemClick(MenuId.SORT_DEFAULT))

		assertSame(before, model.batchRows())
	}
}
