package cz.litoj.schlmgr.integration.data.transfer

import android.net.Uri
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.FileWorker
import cz.litoj.schlmgr.data.transfer.ImportKind
import cz.litoj.schlmgr.data.transfer.ImportTarget
import cz.litoj.schlmgr.data.transfer.ImporterFactory
import cz.litoj.schlmgr.data.transfer.JsonTreeImport
import cz.litoj.schlmgr.data.transfer.WordAsSubjectImport
import cz.litoj.schlmgr.data.transfer.WordIntoContainerImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The picked-file dispatch of [ImporterFactory] — the file extension picks
 * the format, the backlog position the landing place. [ImporterFactory.create]
 * builds the worker the dispatch picked, from context and uri alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImporterFactoryTest {

	private val container = DbItem(name = "Lektion 1", description = "", type = ItemKind.CHAPTER)

	/** The worker [ImporterFactory.create] builds for the pick. */
	private fun workerOf(fileName: String, target: ImportTarget): FileWorker =
		ImporterFactory.create(
			RuntimeEnvironment.getApplication(), Runnable::run,
			Uri.parse("content://schlmgr.test/pick"), fileName, target,
			{ null }, { true }, {}, {}, {})

	@Test
	fun `a json or hjson name is a tree import whatever is open`() {
		for (name in listOf("tree.json", "tree.hjson", "TREE.HJSON", "backup.Json"))
			for (target in listOf(ImportTarget.Root, ImportTarget.Container(container)))
				assertEquals(name, ImportKind.JSON_TREE, ImporterFactory.decide(name, target))
	}

	@Test
	fun `any other name is a word list - a new subject at the root, the open container's content otherwise`() {
		assertEquals(ImportKind.WORD_AS_SUBJECT, ImporterFactory.decide("list.txt", ImportTarget.Root))
		assertEquals(
			ImportKind.WORD_INTO_CONTAINER,
			ImporterFactory.decide("list.txt", ImportTarget.Container(container)))
		// no suffix at all — the extension, not its presence, decides
		assertEquals(ImportKind.WORD_AS_SUBJECT, ImporterFactory.decide("bare", ImportTarget.Root))
	}

	@Test
	fun `no open container is the root target, an open one the container target`() {
		assertEquals(ImportTarget.Root, ImporterFactory.targetFor(null))
		assertEquals(ImportTarget.Container(container), ImporterFactory.targetFor(container))
	}

	@Test
	fun `create builds the worker the dispatch picked`() {
		assertTrue(workerOf("tree.hjson", ImportTarget.Root) is JsonTreeImport)
		assertTrue(workerOf("tree.json", ImportTarget.Container(container)) is JsonTreeImport)
		assertTrue(workerOf("list.txt", ImportTarget.Root) is WordAsSubjectImport)
		assertTrue(workerOf("list.txt", ImportTarget.Container(container)) is WordIntoContainerImport)
	}
}
