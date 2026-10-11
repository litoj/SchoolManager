package cz.litoj.schlmgr.data

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind

/**
 * Pre-order stream assembly — every file-format importer appends through here,
 * so the row shape stays identical.
 */
object StreamBuilder {

	fun addChapter(
		out: MutableList<StreamItem>,
		name: String,
		description: String,
		parent: StreamItem?,
	): StreamItem = addItem(out, ItemKind.CHAPTER, name, description, parent, 0, 0)

	fun addWord(
		out: MutableList<StreamItem>,
		name: String,
		description: String,
		parent: StreamItem?,
		passedTests: Int = 0,
		failedTests: Int = 0,
	): StreamItem = addItem(out, ItemKind.WORD, name, description, parent, passedTests, failedTests)

	fun addTranslation(
		out: MutableList<StreamItem>,
		name: String,
		description: String,
		parent: StreamItem,
		passedTests: Int = 0,
		failedTests: Int = 0,
	): StreamItem = addItem(out, ItemKind.TRANSLATION, name, description, parent, passedTests, failedTests)

	fun addItem(
		out: MutableList<StreamItem>,
		kind: ItemKind,
		name: String,
		description: String,
		parent: StreamItem?,
		passedTests: Int = 0,
		failedTests: Int = 0,
	): StreamItem {
		val ref = StreamItem(
			DbItem(name = name, description = description, type = kind,
				passedTests = passedTests, failedTests = failedTests), parent)
		out.add(ref)
		return ref
	}

	fun childrenOf(items: List<StreamItem>): Map<StreamItem, List<StreamItem>> {
		val childrenOf = HashMap<StreamItem, MutableList<StreamItem>>()
		for (item in items) item.parent?.let { childrenOf.getOrPut(it) { ArrayList() }.add(item) }
		return childrenOf
	}
}
