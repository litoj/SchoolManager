package cz.litoj.schlmgr.data

import androidx.room.withTransaction
import cz.litoj.schlmgr.data.db.FlipAnalysis
import cz.litoj.schlmgr.data.db.FlipResolution
import cz.litoj.schlmgr.data.db.FlipService
import cz.litoj.schlmgr.data.db.ItemDatabase

/** Database entry points of one flip run. */
class FlipRunner internal constructor(private val database: ItemDatabase) {

	suspend fun flip(ids: List<Int>) = database.withTransaction {
		FlipService.flipIds(database.itemDao(), ids)
	}

	suspend fun start(actingParents: Collection<Int>, ids: List<Int>): FlipAnalysis =
		FlipService.start(database.itemDao(), actingParents, ids)

	suspend fun advance(analysis: FlipAnalysis, resolutions: List<FlipResolution>): FlipAnalysis =
		FlipService.advance(database.itemDao(), analysis, resolutions)

	suspend fun commit(analysis: FlipAnalysis) {
		require(analysis.conflicts.isEmpty()) { "The flip plan still holds conflicts" }
		database.withTransaction { FlipService.execute(database.itemDao(), analysis.plan) }
	}
}
