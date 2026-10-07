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

	/**
	 * One hop of a graph walk: the child ids of the one [parentId]. One parent at a
	 * time, never a bulk `IN (...)`: SQLite caps one statement's host parameters at
	 * 999, and a large subject's breadth exceeds that (see [deleteGraph]).
	 */
	@Query("SELECT child_id FROM item_link WHERE parent_id = :parentId")
	abstract suspend fun childIdsOf(parentId: Int): List<Int>

	/** Deletes every link touching the one [id] — see [deleteGraph] for the row-at-a-time rule. */
	@Query("DELETE FROM item_link WHERE parent_id = :id OR child_id = :id")
	abstract suspend fun deleteLinksTouching(id: Int)

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
	 * Every link whose child lies in the [subgraph] — the child's whole parent
	 * set, parents outside the subgraph included (a reference placed elsewhere
	 * can point at a subgraph item). One query instead of the per-row
	 * [parentsOf]/[linkIsReference] round-trips while building search-hit rows.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT l.* FROM item_link l WHERE l.child_id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphChildLinks(rootId: Int): List<DbLink>

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
	 * while one of them survives.
	 *
	 * Every statement handles one row: SQLite caps one statement's host parameters
	 * at 999, and a whole subject's breadth often exceeds that, so the former bulk
	 * `IN (...)` forms crashed on large subjects.
	 *
	 * The walk completes before anything is deleted: an item survives when any of
	 * its parents lies outside the doomed set, and such a parent can be discovered
	 * late (a diamond in the graph), so deletability can only be decided once the
	 * doomed set is final.
	 */
	@Transaction
	open suspend fun deleteGraph(rootId: Int) {
		// everything reachable from the root, each id visited once
		val doomed = HashSet<Int>()
		doomed.add(rootId)
		val stack = ArrayDeque<Int>()
		stack.addLast(rootId)
		while (stack.isNotEmpty()) {
			val parent = stack.removeLast()
			for (child in childIdsOf(parent))
				if (doomed.add(child)) stack.addLast(child)
		}
		// an item is truly deletable only when all its parents are deletable too;
		// each node's parents are read once, the deletable set grows until stable
		val dead = HashSet<Int>()
		dead.add(rootId)
		val parents = HashMap<Int, List<Int>>()
		var changed = true
		while (changed) {
			changed = false
			for (x in doomed) {
				if (x in dead) continue
				if (parents.getOrPut(x) { parentsOf(x) }.all { it in dead }) {
					dead.add(x)
					changed = true
				}
			}
		}
		for (id in dead) {
			deleteLinksTouching(id)
			deleteById(id)
		}
	}
}
