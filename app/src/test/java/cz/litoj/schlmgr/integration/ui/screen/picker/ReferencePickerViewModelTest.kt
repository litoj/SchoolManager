package cz.litoj.schlmgr.integration.ui.screen.picker

import androidx.room.Room
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.screen.picker.PickerViewModel
import cz.litoj.schlmgr.ui.viewmodel.NavigationHistory
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
import kotlinx.coroutines.Dispatchers

/** Reference picks share the picker but links the picked rows into the captured chapter. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReferencePickerViewModelTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries().build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `a whole pick links into the picker's initial chapter without duplicating links`() {
		val subject = ItemRepository.createSubject("A", null)
		val target = ItemRepository.createChapter(subject.id, "target", null, 0)
		val source = ItemRepository.createChapter(subject.id, "source", null, 1)
		val first = ItemRepository.createWord(source.id, "one", null, listOf("1" to null))
		val second = ItemRepository.createWord(source.id, "two", null, listOf("2" to null))
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined, target)
		model.onItemClick(model.items().single { it.item.type == ItemKind.CHAPTER && it.item.name == "A" })
		model.onItemClick(model.items().single { it.item.name == "source" })
		model.setSelected(model.items().single { it.item.id == first.id }, true)
		model.setSelected(model.items().single { it.item.id == second.id }, true)
		model.onSelect()

		assertTrue("a committed pick reports a refresh", model.didReference)
		assertTrue(ItemRepository.linkIsReference(target.id, first.id))
		assertTrue(ItemRepository.linkIsReference(target.id, second.id))

		// the same pick cannot append a second representation of the already-linked word
		assertTrue(ItemRepository.referenceAll(listOf(first.id), target.id))
		assertEquals(setOf(first.id, second.id),
			ItemRepository.children(target.id).map { it.id }.toSet())
	}

	@Test
	fun `one pick links rows of two chapters`() {
		val subject = ItemRepository.createSubject("A", null)
		val target = ItemRepository.createChapter(subject.id, "target", null, 0)
		val c1 = ItemRepository.createChapter(subject.id, "c1", null, 1)
		val c2 = ItemRepository.createChapter(subject.id, "c2", null, 2)
		val first = ItemRepository.createWord(c1.id, "one", null, listOf("1" to null))
		val second = ItemRepository.createWord(c2.id, "two", null, listOf("2" to null))
		val model = PickerViewModel(NavigationHistory(), Dispatchers.Unconfined, target)
		model.onItemClick(model.items().single { it.item.type == ItemKind.CHAPTER && it.item.name == "A" })
		model.onItemClick(model.items().single { it.item.name == "c1" })
		model.onItemLongClick(model.items().single { it.item.id == first.id })
		model.onBackRequested()
		model.onItemClick(model.items().single { it.item.name == "c2" })
		model.toggleSelect(model.items().single { it.item.id == second.id })
		model.onSelect()

		assertEquals(setOf(first.id, second.id), ItemRepository.children(target.id).map { it.id }.toSet())
	}

	@Test
	fun `a cycle refuses the whole pick and leaves no earlier links behind`() {
		val subject = ItemRepository.createSubject("A", null)
		val target = ItemRepository.createChapter(subject.id, "target", null, 0)
		val other = ItemRepository.createChapter(subject.id, "other", null, 1)

		val accepted = ItemRepository.referenceAll(listOf(other.id, target.id), target.id)

		assertFalse(accepted)
		assertFalse("the refused batch rolled back its first link",
			ItemRepository.children(target.id).any { it.id == other.id })
	}
}
