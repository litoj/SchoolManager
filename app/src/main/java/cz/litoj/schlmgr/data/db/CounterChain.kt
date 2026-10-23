package cz.litoj.schlmgr.data.db

import cz.litoj.schlmgr.data.db.dao.Counters
import cz.litoj.schlmgr.data.db.dao.ItemDao

/**
 * Counter derivation of containers, from a start row upward.
 * - stateless: the caller owns the open transaction.
 */
internal object CounterChain {

	/**
	 * Recomputes the stored success/fail of a container from the words in its subgraph.
	 * The fresh counters are returned as well.
	 */
	internal suspend fun recomputeCounters(dao: ItemDao, containerId: Int): Counters {
		val counts = dao.subgraphWordCounters(containerId)
		dao.updateCounters(containerId, counts.passed, counts.failed)
		return counts
	}

	/**
	 * Recomputes the counters of [start] and of every container above it.
	 * - it walks all parents, each container once, and shared containers carry sums.
	 * - never the root, since its sum spans the whole database and shows nowhere.
	 * - callers supply the sums and the parents.
	 */
	internal suspend fun recomputeCounterChain(
		dao: ItemDao,
		start: Int,
		countersOf: suspend (Int) -> Counters,
		parentsOf: suspend (Int) -> List<Int>,
	) = walkAbove(start, parentsOf) { id ->
		val counts = countersOf(id)
		dao.updateCounters(id, counts.passed, counts.failed)
	}

	/**
	 * Adds a counter delta to every container above [start], and negative values deduct.
	 * The walk matches [recomputeCounterChain].
	 */
	internal suspend fun shiftCounterChain(
		dao: ItemDao,
		start: Int,
		passed: Int,
		failed: Int,
		parentsOf: suspend (Int) -> List<Int>,
	) = walkAbove(start, parentsOf) { dao.addCounters(it, passed, failed) }

	/** Visits start and every container above it, each once, never the root. */
	internal suspend fun walkAbove(
		start: Int,
		parentsOf: suspend (Int) -> List<Int>,
		visit: suspend (Int) -> Unit,
	) {
		val seen = HashSet<Int>()
		seen.add(start)
		val queue = ArrayDeque<Int>()
		queue.add(start)
		while (queue.isNotEmpty()) {
			val id = queue.removeFirst()
			if (id != ROOT_ID) visit(id)
			for (parent in parentsOf(id)) {
				if (parent == ROOT_ID) continue
				if (seen.add(parent)) queue.add(parent)
			}
		}
	}
}
