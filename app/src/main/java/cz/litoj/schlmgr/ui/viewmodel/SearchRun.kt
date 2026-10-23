package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.ui.search.SearchEngine
import cz.litoj.schlmgr.ui.search.SearchIndex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One live search run: preload, walk and drain.
 * - a superseded run is [abort]ed first.
 */
internal class SearchRun(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {

		fun appendBatch(batch: List<ItemRow>)

		fun runFinished()
	}

	companion object {
		const val SEARCH_BATCH = 32
	}

	private var engine: SearchEngine? = null
	private var job: Job? = null

	/** The chapters the engine walked, read while the run lives. */
	fun visitedChapters(): Int = engine?.visitedChapters ?: 0

	fun start(engine: SearchEngine, path: List<DbItem>) {
		this.engine = engine
		job = scope.launch {
			// a run always has a root.
			val root = path.lastOrNull() ?: return@launch
			val index = withContext(io) {
				SearchIndex(
					ItemRepository.subgraph(root.id),
					ItemRepository.subgraphLinks(root.id),
					ItemRepository.subgraphChildLinks(root.id),
				)
			}
			// the run may have been superseded in preload.
			if (!engine.isActive) return@launch
			// the producer walks on IO while the consumer appends on main.
			val rows = Channel<ItemRow>(Channel.UNLIMITED)
			launch(io) {
				var position = 0
				engine.search(index, path) { item, hitPath ->
					rows.trySend(ItemRow.ofSearchHit(item, hitPath, ++position, index.linksOf(item.id)))
				}
				rows.close()
			}
			drainBatches(rows)
			// the run may have been superseded in drain.
			if (!engine.isActive) return@launch
			ensureActive()
			host.runFinished()
		}
	}

	internal suspend fun drainBatches(rows: Channel<ItemRow>) {
		var batch = ArrayList<ItemRow>(SEARCH_BATCH)
		for (row in rows) {
			batch.add(row)
			if (batch.size == SEARCH_BATCH) {
				host.appendBatch(batch)
				batch = ArrayList(SEARCH_BATCH)
			}
		}
		if (batch.isNotEmpty()) host.appendBatch(batch)
	}

	fun abort() {
		engine?.abort()
		engine = null
		job?.cancel()
		job = null
	}
}
