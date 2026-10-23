package cz.litoj.schlmgr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.ShiftRange

data class Counters(val passed: Int, val failed: Int)

data class TypeCount(val kind: ItemKind, val amount: Int)

@Dao
abstract class ItemDao {

	@Insert
	abstract suspend fun insert(data: DbItem): Long

	/**
	 * The returned ids follow the order of the list.
	 */
	@Insert
	abstract suspend fun insertAll(items: List<DbItem>): List<Long>

	@Insert
	abstract suspend fun insertLink(link: DbLink)

	@Insert
	abstract suspend fun insertAllLinks(links: List<DbLink>)

	/**
	 * Inserts the root row, or nothing when it is present.
	 * This is a raw insert, since the insert annotation auto-generates and can never write id 0.
	 */
	@Query(
		"INSERT OR IGNORE INTO item(id, name, description, type, passed_tests, failed_tests) " +
			"VALUES (:id, '', '', 'root', 0, 0)"
	)
	abstract suspend fun insertRoot(id: Int)

	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.child_id = i.id " +
			"WHERE l.parent_id = :parentId ORDER BY l.position, l.child_id"
	)
	abstract suspend fun getChildren(parentId: Int): List<DbItem>

	@Query("SELECT * FROM item WHERE id = :id")
	abstract suspend fun get(id: Int): DbItem?

	@Query("SELECT * FROM item WHERE name = :name AND type = :kind")
	abstract suspend fun getByName(name: String, kind: ItemKind): List<DbItem>

	/**
	 * The first name and kind match that reaches [rootId] over its parent chain.
	 * - it finds the merge targets inside one subject.
	 * - plain links lead the walk, so a reference cannot pull a row from its home subject.
	 * - only the first parent chain counts, so a second path never widens the merge.
	 * - the first match wins, so repeated merges land on one target.
	 */
	suspend fun findWordInSubject(rootId: Int, name: String, kind: ItemKind = ItemKind.WORD): DbItem? {
		for (match in getByName(name, kind)) {
			var cursor = match.id
			// a link cycle cannot reach the root, so the walk stops on the first repeat
			val seen = hashSetOf(match.id)
			while (true) {
				val parent = anyParentOf(cursor)?.id ?: break
				if (parent == rootId) return match
				if (parent == ROOT_ID || !seen.add(parent)) break
				cursor = parent
			}
		}
		return null
	}

	@Query("SELECT COALESCE(MAX(position), -1) + 1 FROM item_link WHERE parent_id = :parentId")
	abstract suspend fun nextPosition(parentId: Int): Int

	@Query("SELECT position FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun linkPosition(parentId: Int, childId: Int): Int?

	@Query("UPDATE item_link SET position = :position WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun setLinkPosition(parentId: Int, childId: Int, position: Int)

	@Query(
		"UPDATE item_link SET position = position + :delta " +
			"WHERE parent_id = :parentId AND position BETWEEN :from AND :to"
	)
	abstract suspend fun shiftPositions(parentId: Int, delta: Int, from: Int, to: Int)

	suspend fun shift(range: ShiftRange) =
		shiftPositions(range.parentId, range.delta, range.from, range.to)

	@Query("SELECT COUNT(*) FROM item_link WHERE child_id = :childId")
	abstract suspend fun parentCount(childId: Int): Int

	@Query("SELECT is_reference FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun linkIsReference(parentId: Int, childId: Int): Boolean?

	@Query("SELECT parent_id FROM item_link WHERE child_id = :childId")
	abstract suspend fun parentsOf(childId: Int): List<Int>

	/** The parents, plain links first, since the plain chain is the primary one. */
	@Query("SELECT parent_id FROM item_link WHERE child_id = :childId ORDER BY is_reference")
	abstract suspend fun parentsPlainFirst(childId: Int): List<Int>

	/**
	 * The parents linked through plain, non-reference links, the occurrences that share the row.
	 * - plain links form the primary derivation chain.
	 * - only plain parents carry counters and child order.
	 * - reference links only expose content without owning it.
	 */
	@Query("SELECT parent_id FROM item_link WHERE child_id = :childId AND is_reference = 0")
	abstract suspend fun plainParentsOf(childId: Int): List<Int>

	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.parent_id = i.id " +
			"WHERE l.child_id = :childId AND l.is_reference = 1"
	)
	abstract suspend fun referenceParentsOf(childId: Int): List<DbItem>

	/**
	 * The rows that link to [childId] in link order, the words of a translation.
	 * Reference parents are included, since a shown-through parent is a home too.
	 */
	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.parent_id = i.id " +
			"WHERE l.child_id = :childId ORDER BY l.position"
	)
	abstract suspend fun parentRowsOf(childId: Int): List<DbItem>

	/**
	 * One hop of the browsing path walk: the first parent row of [childId].
	 * - the root is a valid answer.
	 * - no ordering exists among equal candidates, so a multi-parent path stays arbitrary.
	 */
	@Query(
		"SELECT i.* FROM item i INNER JOIN item_link l ON l.parent_id = i.id " +
			"WHERE l.child_id = :childId ORDER BY l.is_reference LIMIT 1"
	)
	abstract suspend fun anyParentOf(childId: Int): DbItem?

	/**
	 * - one parent at a time, never a bulk query, since host parameters cap at 999
	 */
	@Query("SELECT child_id FROM item_link WHERE parent_id = :parentId")
	abstract suspend fun childIdsOf(parentId: Int): List<Int>

	/**
	 * Every row the flip read model needs in one read, with no growing parameter list.
	 * - every row below the seeds, their whole subtrees.
	 * - every ancestor above them, the subject chains.
	 * - every word or translation named like a reached row, the merge candidates.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT id FROM item WHERE id IN (:seeds) " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id), " +
			"anc(id) AS (SELECT l.parent_id FROM item_link l WHERE l.child_id IN (SELECT id FROM des) " +
			"UNION SELECT l.parent_id FROM item_link l INNER JOIN anc a ON l.child_id = a.id) " +
			"SELECT i.* FROM item i WHERE i.id IN (SELECT id FROM des) " +
			"OR i.id IN (SELECT id FROM anc) " +
			"OR (i.type IN ('word', 'translation') " +
			"AND i.name IN (SELECT s.name FROM item s WHERE s.id IN (SELECT id FROM des)))"
	)
	abstract suspend fun flipScopeRows(seeds: Collection<Int>): List<DbItem>

	/**
	 * Every link into the reached rows, one read for all their parent sets and maps.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT id FROM item WHERE id IN (:seeds) " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id), " +
			"anc(id) AS (SELECT l.parent_id FROM item_link l WHERE l.child_id IN (SELECT id FROM des) " +
			"UNION SELECT l.parent_id FROM item_link l INNER JOIN anc a ON l.child_id = a.id) " +
			"SELECT l.* FROM item_link l WHERE l.child_id IN (SELECT id FROM des UNION SELECT id FROM anc)"
	)
	abstract suspend fun flipScopeLinks(seeds: Collection<Int>): List<DbLink>

	/** Every link holding [childId], one read for the parent slot and kind. */
	@Query("SELECT * FROM item_link WHERE child_id = :childId")
	abstract suspend fun linksInto(childId: Int): List<DbLink>

	@Query("SELECT * FROM item_link WHERE parent_id = :parentId")
	abstract suspend fun linksOf(parentId: Int): List<DbLink>

	/** Deletes every link touching the one [id], the row-at-a-time rule of the graph deletion. */
	@Query("DELETE FROM item_link WHERE parent_id = :id OR child_id = :id")
	abstract suspend fun deleteLinksTouching(id: Int)

	@Query("DELETE FROM item WHERE id = :id")
	abstract suspend fun deleteById(id: Int)

	@Query("DELETE FROM item_link WHERE parent_id = :parentId AND child_id = :childId")
	abstract suspend fun deleteLink(parentId: Int, childId: Int)

	@Query("UPDATE item SET name = :name, description = :description WHERE id = :id")
	abstract suspend fun updateById(id: Int, name: String, description: String)

	@Query("UPDATE item SET passed_tests = :passed, failed_tests = :failed WHERE id = :id")
	abstract suspend fun updateCounters(id: Int, passed: Int, failed: Int)

	/** Adds to the stored counters of a row, and negative values deduct. */
	@Query("UPDATE item SET passed_tests = passed_tests + :passed, failed_tests = failed_tests + :failed WHERE id = :id")
	abstract suspend fun addCounters(id: Int, passed: Int, failed: Int)

	@Query("UPDATE item SET type = :type WHERE id = :id")
	abstract suspend fun updateType(id: Int, type: ItemKind)

	@Query("UPDATE item SET passed_tests = passed_tests + 1 WHERE id IN (:ids)")
	abstract suspend fun addPassed(ids: Collection<Int>)

	@Query("UPDATE item SET failed_tests = failed_tests + 1 WHERE id IN (:ids)")
	abstract suspend fun addFailed(ids: Collection<Int>)

	/**
	 * The subject containing [itemId] over plain links only. Plain links
	 * never cross subjects, so a home-holding row keeps its own subject.
	 */
	@Query(
		"WITH RECURSIVE anc(id) AS (SELECT :itemId " +
			"UNION SELECT l.parent_id FROM item_link l INNER JOIN anc a ON l.child_id = a.id " +
			"WHERE l.is_reference = 0) " +
			"SELECT id FROM anc WHERE id IN (SELECT child_id FROM item_link WHERE parent_id = :rootId) LIMIT 1"
	)
	abstract suspend fun plainSubjectRootOf(itemId: Int, rootId: Int): Int?

	/**
	 * The any-link variant, the fallback for a row with no plain home.
	 * Several subjects can show such a row, so the pick is arbitrary.
	 */
	@Query(
		"WITH RECURSIVE anc(id) AS (SELECT :itemId " +
			"UNION SELECT l.parent_id FROM item_link l INNER JOIN anc a ON l.child_id = a.id) " +
			"SELECT id FROM anc WHERE id IN (SELECT child_id FROM item_link WHERE parent_id = :rootId) LIMIT 1"
	)
	abstract suspend fun anySubjectRootOf(itemId: Int, rootId: Int): Int?

	/**
	 * The subject that scopes the word and translation name merge, so it never widens to the root.
	 * - plain links come first, so a reference never moves a home-holding row.
	 * - a row without a plain home takes any link that shows it.
	 */
	suspend fun subjectRootOf(itemId: Int, rootId: Int): Int? =
		plainSubjectRootOf(itemId, rootId) ?: anySubjectRootOf(itemId, rootId)

	/**
	 * All rows reachable from [rootId], the subgraph of the subject, each once.
	 * This is the subject scope for word and translation name merges.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT DISTINCT i.* FROM item i WHERE i.id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraph(rootId: Int): List<DbItem>

	/**
	 * All links between the rows of [subgraph].
	 * - every link whose parent lies in the subgraph, since reached rows bring their children.
	 * - the order is arbitrary.
	 * - with [subgraph], the whole preloaded container content.
	 * - its users are the search run, the export walk and the subject merge.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT l.* FROM item_link l WHERE l.parent_id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphLinks(rootId: Int): List<DbLink>

	/**
	 * Every link whose child lies in the [subgraph], the whole parent set of the child.
	 * - outside parents are included.
	 * - the search-hit build needs no per-row [parentsOf] or [linkIsReference] lookup.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT l.* FROM item_link l WHERE l.child_id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphChildLinks(rootId: Int): List<DbLink>

	/**
	 * A container success/fail derived from the word rows in its subgraph, each word once.
	 * - plain links only.
	 * - referenced content shows in a container but never counts toward its rate.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :rootId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id " +
			"WHERE l.is_reference = 0) " +
			"SELECT COALESCE(SUM(passed_tests), 0) AS passed, COALESCE(SUM(failed_tests), 0) AS failed " +
			"FROM item WHERE type = 'word' AND id IN (SELECT id FROM des)"
	)
	abstract suspend fun subgraphWordCounters(rootId: Int): Counters

	/**
	 * Plain links entering the plain subtree of [childId] from outside it.
	 * - this is the sharing that a stats delta cannot express.
	 * - the subtree words and chapters stay reachable elsewhere, so their sums must not move.
	 * - call this after the boundary link is gone.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT :childId " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id " +
			"WHERE l.is_reference = 0) " +
			"SELECT COUNT(*) FROM item_link l INNER JOIN item i ON i.id = l.child_id " +
			"WHERE l.is_reference = 0 AND i.type IN ('word', 'chapter') " +
			"AND l.child_id IN (SELECT id FROM des) AND l.parent_id NOT IN (SELECT id FROM des)"
	)
	abstract suspend fun plainLinksIntoSubtree(childId: Int): Int

	/**
	 * The parent sets of the direct children of [parentId], the browse reference facts.
	 * - no subtree walk, since a browse list shows the children only.
	 * - the root is a parent like any other.
	 */
	@Query(
		"SELECT l.* FROM item_link l WHERE l.child_id IN " +
			"(SELECT child_id FROM item_link WHERE parent_id = :parentId)"
	)
	abstract suspend fun childLinksOf(parentId: Int): List<DbLink>

	/**
	 * The word and chapter counts of the union of the [rootIds] subgraphs, the roots included.
	 * - one aggregate, so a row under several roots counts once.
	 * - this replaces a whole subgraph load per root.
	 */
	@Query(
		"WITH RECURSIVE des(id) AS (SELECT id FROM item WHERE id IN (:rootIds) " +
			"UNION SELECT l.child_id FROM item_link l INNER JOIN des d ON l.parent_id = d.id) " +
			"SELECT i.type AS kind, COUNT(*) AS amount FROM item i " +
			"WHERE i.id IN (SELECT id FROM des) AND i.type IN ('word', 'chapter') GROUP BY i.type"
	)
	abstract suspend fun subgraphTypeCounts(rootIds: Collection<Int>): List<TypeCount>
}
