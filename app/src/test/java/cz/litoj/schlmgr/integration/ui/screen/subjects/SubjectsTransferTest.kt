package cz.litoj.schlmgr.integration.ui.screen.subjects

import android.net.Uri
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.transfer.ExportDestination
import cz.litoj.schlmgr.data.transfer.ExportFormat
import cz.litoj.schlmgr.ui.popup.Dialogs
import cz.litoj.schlmgr.ui.popup.ExportPopup
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel
import cz.litoj.schlmgr.ui.screen.subjects.SubjectsTransfer
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class SubjectsTransferTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@After
	fun tearDown() {
		Dialogs.clear()
	}

	private class Host : SubjectsTransfer.Host {
		private val rows = listOf(
			ItemRow(DbItem(name = "A", description = "", type = ItemKind.CHAPTER), null, 1, false),
		)
		val requests = mutableListOf<MainExplorerViewModel.Request>()

		init {
			rows.forEach { it.select(true) }
		}

		override val ctx get() = GlobalDependencies.appContext
		override val path = emptyList<DbItem>()
		override val searching = false
		override fun items() = rows
		override fun emitRequest(request: MainExplorerViewModel.Request) { requests += request }
		override fun postToUi(work: Runnable) = work.run()
		override fun clearSelection() = rows.forEach { it.select(false) }
		override fun reloadContent() {}
		override fun refreshInfo() {}
		override fun setContent(container: DbItem?) {}
		override fun requestScrollToBottom() {}
	}

	private data class Started(
		val destination: ExportDestination,
		val format: ExportFormat,
		val rows: List<DbItem>,
	)

	private val started = mutableListOf<Started>()

	private fun transfer(host: Host): SubjectsTransfer =
		SubjectsTransfer(CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined, host) {
			_, _, destination, format, selected, _ ->
			started += Started(destination, format, selected())
		}

	private fun sheetAfterSelectionClears(transfer: SubjectsTransfer, host: Host): ExportPopup {
		transfer.openExportSheet()
		host.clearSelection()
		return Dialogs.stack.value.filterIsInstance<ExportPopup>().single()
	}

	@Test
	fun `share exports the rows selected before the sheet opened`() {
		val host = Host()
		val transfer = transfer(host)

		sheetAfterSelectionClears(transfer, host).pickShare()

		assertEquals(1, started.size)
		assertEquals(listOf("A"), started.single().rows.map { it.name })
		assertEquals(ExportFormat.WORD, started.single().format)
		assertEquals(ExportDestination.Share("A.txt"), started.single().destination)
	}

	@Test
	fun `save uses the same rows after the document pick`() {
		val host = Host()
		val transfer = transfer(host)

		sheetAfterSelectionClears(transfer, host).pickSave()
		assertEquals(
			listOf(MainExplorerViewModel.Request.PickWordExport("A.txt")), host.requests)

		val uri = Uri.parse("content://schlmgr.test/export")
		transfer.onWordTargetPicked(uri)

		assertEquals(1, started.size)
		assertEquals(listOf("A"), started.single().rows.map { it.name })
		assertEquals(ExportDestination.Document(uri), started.single().destination)
	}
}
