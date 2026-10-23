package cz.litoj.schlmgr.data.db
import cz.litoj.schlmgr.data.StreamItem

/**
 * Mutable save state for one import run, and the phases mutate it in order.
 */
internal class ImportAccumulator {

	val saved = HashMap<StreamItem, Int>()

	val rowsByParent = HashMap<Int, HashMap<String, Int>>()

	val chaptersByParent = HashMap<Int, HashMap<String, Int>>()

	val topLevel = ArrayList<DbItem>()

	private val wordRows = HashSet<Int>()
	private val chapterRows = HashSet<Int>()

	/**
	 * The distinct rows the stream words and chapters became.
	 * - every entry of a shared word merges into one row.
	 * - the counts then match the row counts of the export of the same content.
	 */
	val words: Int get() = wordRows.size
	val chapters: Int get() = chapterRows.size

	fun countRow(id: Int, type: ItemKind) {
		if (type == ItemKind.WORD) wordRows.add(id)
		else if (type == ItemKind.CHAPTER) chapterRows.add(id)
	}

	/**
	 * - the first row of a name wins.
	 * - a chapter also lands in the chapter-only index that the reference paths walk.
	 */
	fun indexSavedRow(parent: Int, name: String, id: Int, type: ItemKind) {
		val names = rowsByParent.getOrPut(parent) { HashMap() }
		if (name in names) return
		names[name] = id
		if (type == ItemKind.CHAPTER) chaptersByParent.getOrPut(parent) { HashMap() }[name] = id
	}

	/**
	 * Re-keys every row id onto the real ids that the bulk flush of the import assigned.
	 * The phases before it worked with placeholder ids.
	 */
	fun remapIds(remap: Map<Int, Int>) {
		if (remap.isEmpty()) return
		for (entry in saved.entries) entry.setValue(remap[entry.value] ?: entry.value)
		remapIndex(rowsByParent, remap)
		remapIndex(chaptersByParent, remap)
		for (index in topLevel.indices) {
			val real = remap[topLevel[index].id] ?: continue
			topLevel[index] = topLevel[index].copy(id = real)
		}
	}

	private fun remapIndex(index: HashMap<Int, HashMap<String, Int>>, remap: Map<Int, Int>) {
		for ((key, names) in index.toList()) {
			for (entry in names.entries) entry.setValue(remap[entry.value] ?: entry.value)
			val real = remap[key]
			if (real != null) {
				index.getOrPut(real) { HashMap() }.putAll(names)
				index.remove(key)
			}
		}
	}
}
