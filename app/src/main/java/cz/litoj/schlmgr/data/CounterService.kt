package cz.litoj.schlmgr.data

import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.CounterChain
import cz.litoj.schlmgr.data.db.ItemDatabase
import cz.litoj.schlmgr.data.db.SQL_IDS_PER_STATEMENT

interface CounterPort {
	suspend fun bumpCounters(ids: Collection<Int>, passed: Boolean)
	suspend fun bumpWordCounters(wordId: Int, passed: Boolean)
}

/** Counter writes, whose caller owns the transaction scope and dispatcher. */
class CounterService internal constructor(private val database: ItemDatabase) : CounterPort {

	override suspend fun bumpCounters(ids: Collection<Int>, passed: Boolean) {
		if (passed) recordPass(ids) else recordFail(ids)
	}

	suspend fun recordPass(ids: Collection<Int>) = record(ids, true)

	suspend fun recordFail(ids: Collection<Int>) = record(ids, false)

	private suspend fun record(ids: Collection<Int>, passed: Boolean) {
		if (ids.isEmpty()) return
		database.withTransaction {
			for (chunk in ids.chunked(SQL_IDS_PER_STATEMENT)) {
				if (passed) database.itemDao().addPassed(chunk) else database.itemDao().addFailed(chunk)
			}
		}
	}

	override suspend fun bumpWordCounters(wordId: Int, passed: Boolean) = database.withTransaction {
		val dao = database.itemDao()
		val passedDelta = if (passed) 1 else 0
		val failedDelta = if (passed) 0 else 1
		dao.addCounters(wordId, passedDelta, failedDelta)
		val seen = HashSet<Int>()
		seen.add(wordId)
		for (parent in dao.plainParentsOf(wordId))
			CounterChain.walkAbove(parent, dao::plainParentsOf) { id ->
				if (seen.add(id)) dao.addCounters(id, passedDelta, failedDelta)
			}
	}
}
