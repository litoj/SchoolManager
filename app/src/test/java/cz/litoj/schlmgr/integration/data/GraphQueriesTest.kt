package cz.litoj.schlmgr.integration.data

import androidx.room.Room
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.db.ItemDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GraphQueriesTest {

	private lateinit var db: ItemDatabase

	@Before
	fun setUp() {
		db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ItemDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		ItemRepository.initForTest(db)
		ItemRepository.ensureRoot()
		val subject = ItemRepository.createSubject("s", null)
		val chapter = ItemRepository.createChapter(subject.id, "c", null)
		val word = ItemRepository.createWord(chapter.id, "w", null, listOf("t" to null))
		ItemRepository.reference(word.id, subject.id)
	}

	@After
	fun tearDown() {
		db.close()
		ItemRepository.resetForTest()
	}

	@Test
	fun `a snapshot matches the repository graph reads`() = runBlocking {
		val root = ItemRepository.subjects().single()
		val queries = GraphQueries(db)
		val snapshot = queries.snapshot(root.id)

		assertEquals(ItemRepository.subgraph(root.id), snapshot.items)
		assertEquals(ItemRepository.subgraphLinks(root.id), snapshot.links)
		assertEquals(ItemRepository.subgraphChildLinks(root.id), snapshot.childLinks)
		assertEquals(ItemRepository.children(root.id), queries.children(root.id))
		assertEquals(ItemRepository.childLinksOf(root.id), queries.childLinksOf(root.id))
		assertEquals(ItemRepository.childTranslations(root.id), queries.childTranslations(root.id))
		assertEquals(ItemRepository.get(root.id), queries.get(root.id))
		assertEquals(ItemRepository.pathTo(root.id), queries.pathTo(root.id))
		assertEquals(ItemRepository.linkIsReference(root.id, ItemRepository.children(root.id).first().id),
			queries.linkIsReference(root.id, ItemRepository.children(root.id).first().id))
	}
}
