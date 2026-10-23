package cz.litoj.schlmgr.data.db

/**
 * One relation of a flip that spans the flip set and the outside.
 * - row is a set word held by containers outside the covered range.
 * - or row is a translation held by words of the set and words outside it.
 * - outside is what the relation reaches past the selection.
 * - scheduled is the flip set side, a remove drops these words from the flip.
 */
class FlipConflict(val row: DbItem, val outside: List<DbItem>, val scheduled: List<DbItem>)

/**
 * A custom version of one conflicting row.
 * - the flip copies the row with these contents, the other side keeps the row.
 * - the trim is the simplest detach, so a trailing space names the same word.
 */
class FlipOverride(val name: String, val description: String)

/**
 * - the row left the list or stayed with possibly edited contents.
 */
class FlipResolution(val rowId: Int, val removed: Boolean, val name: String, val description: String)

/**
 * The working state of the conflict loop in one flip run.
 * - flipSet is the words to flip, covered the containers the flip may change.
 * - overrides holds custom versions, seeds the read model input.
 * - seeds are the selection plus every word a keep resolution pulled in.
 * - each popup round advances it through FlipService.advance.
 */
class FlipPlan(
	val flipSet: MutableSet<Int>,
	val covered: MutableSet<Int>,
	val overrides: MutableMap<Int, FlipOverride>,
	val seeds: MutableSet<Int>,
)

class FlipAnalysis(val plan: FlipPlan, val conflicts: List<FlipConflict>)
