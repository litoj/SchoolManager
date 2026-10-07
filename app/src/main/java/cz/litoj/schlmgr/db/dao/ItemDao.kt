package cz.litoj.schlmgr.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.DbLink
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository

/**
 * Passed/failed aggregate of a set of rows (the chapter success-rate counters).
 */
data class Sf(val passed: Int, val failed: Int)

@Dao
abstract class ItemDao {

	@Insert
	abstract suspend fun insert(data: DbItem): Long

	@Insert
	abstract suspend fun insertLink(link: DbLink)

	/**
	 * Inserts the single invisible root row ([ItemRepository.ROOT_ID]). A no-op when it
	 * already exists. Uses a raw insert because `@Insert` auto-generates and can never
	 * write the reserved id `0`.
	 */
	@Query(
		"INSERT OR IGNORE INTO item(id, name, description, type, passed_tests, failed_tests) " +
			"VALUES (:id, '', '', 'root', 0, 0)"
	)
	abstract suspend fun insertRoot(id: Int)

	/** All children of the given item, in link order. */
	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id = :parentId ORDER BY l.position, l.child_id"
	)
	abstract suspend fun getChildren(parentId: Int): List<DbItem>

	@Query("SELECT * FROM item WHERE id = :id")
	abstract suspend fun get(id: Int): DbItem?

	@Query("SELECT * FROM item WHERE name = :name AND type = :kind")
	abstract suspend fun getByName(name: String, kind: ItemKind): List<DbItem>

	@Query("SELECT COUNT(*) FROM item")
	abstract suspend fun count(): Int

	/** One position past the current end of [parentId]'s child list. */
	@Query("SELECT COALESCE(MAX(position), -1) + 1 FROM item_link WHERE parent_id = :parentId")
	abstract suspend fun nextPosition(parentId: Int): Int

	@Query("SELECT position FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun linkPosition(parentId: Int, childId: Int): Int?

	@Query("UPDATE item_link SET position = :position WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun setLinkPosition(parentId: Int, childId: Int, position: Int)

	/** Shifts the positions in the inclusive `[from, to]` range by [delta]. */
	@Query(
		"UPDATE item_link SET position = position + :delta " +
			"WHERE parent_id = :parentId AND position BETWEEN :from AND :to"
	)
	abstract suspend fun shiftPositions(parentId: Int, delta: Int, from: Int, to: Int)

	@Query("SELECT COUNT(*) FROM item_link WHERE child_id = :childId")
	abstract suspend fun parentCount(childId: Int): Int

	/** Whether the (parent, child) link is a reference link — `null` when no such link exists. */
	@Query("SELECT is_reference FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun linkIsReference(parentId: Int, childId: Int): Boolean?

	@Query("SELECT parent_id FROM item_link WHERE child_id = :childId")
	abstract suspend fun parentsOf(childId: Int): List<Int>

	/** One BFS hop: the child ids of all given parents. */
	@Query("SELECT child_id FROM item_link WHERE parent_id IN (:parentIds)")
	abstract suspend fun childIdsOf(parentIds: Collection<Int>): List<Int>

	@Query("DELETE FROM item_link WHERE parent_id IN (:ids) OR child_id IN (:ids)")
	abstract suspend fun deleteLinksTouching(ids: Set<Int>)

	@Query("DELETE FROM item WHERE id IN (:ids)")
	abstract suspend fun deleteItems(ids: Set<Int>)

	@Query("DELETE FROM item WHERE id = :id")
	abstract suspend fun deleteById(id: Int)

	@Query("DELETE FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun deleteLink(parentId: Int, childId: Int)

	@Query("UPDATE item SET name = :name, description = :description WHERE id = :id")
	abstract suspend fun updateById(id: Int, name: String, description: String?)

	@Query("UPDATE item SET passed_tests = :passed, failed_tests = :failed WHERE id = :id")
	abstract suspend fun updateSf(id: Int, passed: Int, failed: Int)

	@Query("UPDATE item SET type = :type WHERE id = :id")
	abstract suspend fun updateType(id: Int, type: ItemKind)

	@Query("UPDATE item SET passed_tests = passed_tests + 1 WHERE id IN (:ids)")
	abstract suspend fun addPassed(ids: Collection<Int>)

	@Query("UPDATE item SET failed_tests = failed_tests + 1 WHERE id IN (:ids)")
	abstract suspend fun addFailed(ids: Collection<Int>)

	/**
	 * The subject that contains [itemId]: the ancestor whose own parent is the root.
	 * That ancestor is the word/translation name-merge scope, so the merge never
	 * widens to the root.
	 */
	@Query(
		"WITH RECURSIVE anc(id) AS (SELECT :itemId " +
			"UNION SELECT l.parent_id FROM item_link l INNER JOIN anc a ON l.child_id = a.id) " +
			"SELECT id FROM anc WHERE id IN (SELECT child_id FROM item_link WHERE parent_id = :rootId) LIMIT 1"
	)
	abstract suspend fun subjectRootOf(itemId: Int, rootId: Int): Int?

	/**
	 * All rows reachable from [rootId] (the subject's subgraph), each once.
	 * This is the subject scope used for word/translation name merges.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT DISTINCT i.* FROM item i WHERE i.id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraph(rootId: Int): List<DbItem>

	/**
	 * All links between the rows of [subgraph] — every link whose parent lies in
	 * the subgraph (children of a reached row are reached too), in no particular
	 * order. Together with [subgraph] this is the whole content of a container,
	 * preloaded for the in-memory search run.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT l.* FROM item_link l WHERE l.parent_id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphLinks(rootId: Int): List<DbLink>

	/**
	 * The first [kind] row with the given [name] inside [rootId]'s subgraph —
	 * the database form of the engine's per-subject name registry.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT i.* FROM item i WHERE i.id IN (SELECT id FROM des) " +
			"AND i.name = :name AND i.type = :kind LIMIT 1"
	)
	abstract suspend fun findInSubgraph(rootId: Int, name: String, kind: ItemKind): DbItem?

	/** Passed/failed summed over the distinct word rows in [rootId]'s subgraph. */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT COALESCE(SUM(passed_tests), 0) AS passed, COALESCE(SUM(failed_tests), 0) AS failed " +
			"FROM item WHERE type = 'word' AND id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphWordSf(rootId: Int): Sf

	/**
	 * Deletes the graph reachable from [rootId], **preserving shared items**: an
	 * item whose every parent lies outside the doomed set is kept (together with
	 * its subtree), so a word/translation living in multiple parents never dies
	 * while one of them survives. Links are removed first, items second.
	 */
	@Transaction
	open suspend fun deleteGraph(rootId: Int) {
		val doomed = HashSet<Int>()
		doomed.add(rootId)
		var frontier = listOf(rootId)
		while (frontier.isNotEmpty()) {
			doomed.addAll(frontier)
			frontier = childIdsOf(frontier).filter { it !in doomed }
		}
		// an item is truly deletable only when all its parents are deletable too
		val dead = HashSet<Int>()
		dead.add(rootId)
		var changed = true
		while (changed) {
			changed = false
			for (x in doomed) {
				if (x !in dead && parentsOf(x).all { it in dead }) {
					dead.add(x)
					changed = true
				}
			}
		}
		if (dead.isEmpty()) return
		deleteLinksTouching(dead)
		deleteItems(dead)
	}

	@Query("SELECT parent_id FROM item_link WHERE child_id IN (:ids)")
	abstract suspend fun parentsOf(ids: Collection<Int>): List<Int>
}
