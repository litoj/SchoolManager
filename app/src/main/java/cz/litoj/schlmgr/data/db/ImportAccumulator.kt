package cz.litoj.schlmgr.data.db
import cz.litoj.schlmgr.data.StreamItem

/**
 * Mutable save state for one import run.
 * - saved row ids plus name indexes for reference phase.
 * - one run owns one instance, phases mutate in order.
 */
internal class ImportAccumulator {

	val saved = HashMap<StreamItem, Int>()

	val rowsByParent = HashMap<Int, HashMap<String, Int>>()

	val chaptersByParent = HashMap<Int, HashMap<String, Int>>()

	val topLevel = ArrayList<DbItem>()

	var words = 0
	var chapters = 0

	/**
	 * Indexes one saved row for the reference phase — the first row of a name
	 * wins, and a chapter also lands in the chapter-only index the reference
	 * paths walk.
	 */
	fun indexSavedRow(parent: Int, name: String, id: Int, type: ItemKind) {
		val names = rowsByParent.getOrPut(parent) { HashMap() }
		if (name in names) return
		names[name] = id
		if (type == ItemKind.CHAPTER) chaptersByParent.getOrPut(parent) { HashMap() }[name] = id
	}

	/**
	 * Re-keys every row id onto the real ids the import's bulk flush
	 * assigned — the phases before it worked with placeholder ids.
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
