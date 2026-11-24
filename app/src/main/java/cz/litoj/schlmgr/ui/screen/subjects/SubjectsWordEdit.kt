package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.KeptTranslation
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.ItemEditor
import cz.litoj.schlmgr.data.GraphQueries
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.ui.screen.subjects.MainExplorerViewModel.Request
import cz.litoj.schlmgr.ui.viewmodel.CreatorResult
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.TranslationRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The word editing and creation of the subjects screen.
 * - it covers the popup commits and the New actions.
 */
internal class SubjectsWordEdit(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
	private val editor: ItemEditor? = null,
	private val graphs: GraphQueries? = null,
) {

	interface Host : SubjectsHost {
		fun selectionCount(): Int

		/** Republishes the rows in place, without a reload. */
		fun notifyChanged()

		fun addItem(item: ItemRow)

		fun addItem(index: Int, item: ItemRow)

		/** Drops rows that the edit removed from the container. */
		fun removeItems(items: Collection<ItemRow>)

		/** Descends into the container, like tapping it: extends the path and loads its content. */
		fun openContainer(container: DbItem)
	}

	fun onEditAction() {
		if (host.selectionCount() != 1) return
		val row = host.items().firstOrNull(ItemRow::selected) ?: return
		val item = row.item
		if (item.type == ItemKind.WORD) {
			scope.launch(io) {
				val rows = ArrayList<TranslationRow>()
				for (trl in checkNotNull(graphs).translations(item.id)) {
					rows.add(TranslationRow(trl.name, trl.description, trl))
				}
				host.postToUi { host.emitRequest(Request.EditWord(row, rows)) }
			}
		} else {
			host.emitRequest(Request.EditItem(row))
		}
	}

	/**
	 * A false verdict keeps the popup open.
	 * - a fork emits [Request.ForkReferences].
	 */
	fun commitWordEdit(row: ItemRow, result: CreatorResult): Boolean {
		val edit = wordEditOf(result, row) ?: return false
		val rowId = row.item.id
		scope.launch(io) {
			val editor = checkNotNull(editor)
			val references = if (editor.editForks(edit)) editor.referencesOf(rowId) else emptyList()
			if (references.isEmpty()) commitWordEditNow(row, edit, rowId)
			else host.postToUi { host.emitRequest(Request.ForkReferences(row, edit, references)) }
		}
		return true
	}

	/** Commits the fork, where only the kept references follow. */
	fun commitForkReferences(row: ItemRow, edit: WordEdit, kept: List<Int>) {
		val rowId = row.item.id
		scope.launch(io) {
			commitWordEditNow(row, edit.copy(referencesToFork = kept), rowId)
		}
	}

	/**
	 * Every edit updates its shown row in place, without rebuilding its list.
	 * - a rename join onto a word the list does not show keeps the survivor at the edited row.
	 * - a join onto a shown row keeps that row, since the survivor keeps its own slot there.
	 * - the edited row leaves and the rows behind it renumber, like any other removal.
	 */
	private suspend fun commitWordEditNow(row: ItemRow, edit: WordEdit, rowId: Int) {
		val graphs = checkNotNull(graphs)
		val word = checkNotNull(editor).editWord(edit)
		val flip = ItemRow.readFlipContent(word, graphs)
		// a touched shared translation re-renders the rows that show it
		val touched = edit.kept.map { it.id } + edit.removed
		val contents = (touched.flatMap { graphs.parentIds(it) }.toSet() - rowId)
			.associateWith { id ->
				graphs.get(id)?.let { ItemRow.readFlipContent(it, graphs) }
			}
		host.postToUi {
			val survivor = host.items().firstOrNull { it !== row && it.item.id == word.id }
			if (survivor == null) row.applyFresh(word, flip)
			else {
				survivor.applyFresh(word, flip)
				host.removeItems(listOf(row))
			}
			// the edited word's own row shows [word], so it needs no second read
			val shown = survivor ?: row
			for (shared in host.items()) {
				if (shared === shown) continue
				contents[shared.item.id]?.let { shared.applyFresh(shared.item, it) }
			}
			host.notifyChanged()
			host.clearSelection()
			host.refreshInfo()
		}
	}

	fun commitItemEdit(row: ItemRow, result: CreatorResult): Boolean {
		if (result.name.isEmpty()) return false
		val description = result.description.replace("\\t", "\t")
		val rename = result.name != row.item.name
		// snapshot on main
		val rowId = row.item.id
		scope.launch(io) {
			val editor = checkNotNull(editor)
			val graphs = checkNotNull(graphs)
			editor.setDescription(rowId, description)
			if (rename) editor.rename(rowId, result.name)
			val fresh = graphs.get(rowId)
			// a word or a translation re-renders through its flip content
			val flip = if (fresh != null && (fresh.type == ItemKind.WORD || fresh.type == ItemKind.TRANSLATION))
				ItemRow.readFlipContent(fresh, graphs) else emptyList()
			host.postToUi {
				row.applyFresh(fresh, flip)
				host.notifyChanged()
				host.clearSelection()
				host.refreshInfo()
			}
		}
		return true
	}

	/**
	 * Commits a new word per backslash-split name, and false on incomplete input.
	 * - a closed container also returns false.
	 * - each name appends, so the database links it at the end.
	 */
	private fun applyWord(result: CreatorResult): Boolean {
		if (result.name.isEmpty() || result.translations.isEmpty()) return false
		val origin = host.path.lastOrNull() ?: return false
		val parentId = origin.id
		val additions = splitRows(result, editing = false)?.third ?: return false
		val names = WordNames.splitEscaped(result.name)
		if (names.any { it.isEmpty() }) return false
		val descs = WordNames.splitEscaped(result.description)
		scope.launch(io) {
			val created = ArrayList<ItemRow>()
			val editor = checkNotNull(editor)
			val graphs = checkNotNull(graphs)
			for (i in names.indices) {
				val w = editor.createWord(
					parentId, names[i],
					if (i < descs.size) descs[i].replace("\\t", "\t") else null,
					additions,
				)
				val links = graphs.childLinksOf(parentId).filter { it.childId == w.id }
				created.add(ItemRow.ofBrowse(w, parentId, 0, links, graphs.translations(w.id)))
			}
			// the live list renumbers on insert, so the rows append in order
			host.postToUi {
				if (host.path.lastOrNull() !== origin) return@postToUi
				created.forEach { host.addItem(it) }
			}
		}
		return true
	}

	fun newSubject() {
		host.emitRequest(Request.CreateItem(RowKind.SUBJECT))
	}

	fun newChapter() {
		host.emitRequest(Request.CreateItem(RowKind.CHAPTER))
	}

	fun newNote() {
		host.emitRequest(Request.CreateItem(RowKind.NOTE))
	}

	fun newWord() {
		host.emitRequest(Request.CreateItem(RowKind.WORD))
	}

	fun commitNewItem(kind: RowKind, result: CreatorResult): Boolean {
		if (result.name.isEmpty()) return false
		when (kind) {
			RowKind.SUBJECT -> {
				val origin = host.path.lastOrNull()
				val description = result.description.replace("\\t", "\t")
				scope.launch(io) {
					val mch = checkNotNull(editor).createSubject(result.name, description)
					val row = ItemRow.ofBrowse(mch, ROOT_ID, 0, emptyList(), emptyList())
					host.postToUi {
						if (host.path.lastOrNull() !== origin) return@postToUi
						host.addItem(row)
					}
				}
			}
			RowKind.NOTE -> {
				val par = host.path.lastOrNull() ?: return false
				val description = result.description.replace("\\t", "\t")
				scope.launch(io) {
					val created = checkNotNull(editor).createNote(par.id, result.name, description)
					host.postToUi {
						if (host.path.lastOrNull() !== par) return@postToUi
						host.addItem(ItemRow.ofBrowse(created, par.id, 0, emptyList(), emptyList()))
					}
				}
			}
			RowKind.CHAPTER -> {
				val par = host.path.lastOrNull() ?: return false
				val description = result.description.replace("\\t", "\t")
				scope.launch(io) {
					val created = checkNotNull(editor).createChapter(par.id, result.name, description)
					host.postToUi {
						if (host.path.lastOrNull() !== par) return@postToUi
						host.addItem(ItemRow.ofBrowse(created, par.id, 0, emptyList(), emptyList()))
						host.openContainer(created)
					}
				}
			}
			RowKind.WORD -> {
				if (!applyWord(result)) return false
			}
		}
		return true
	}
}

internal fun wordEditOf(result: CreatorResult, row: ItemRow): WordEdit? {
	if (result.name.isEmpty() || result.translations.isEmpty()) return null
	val parentId = row.parentId ?: return null
	val rows = splitRows(result, editing = true) ?: return null
	return WordEdit(
		row.item.id, parentId, result.name, result.description.replace("\\t", "\t"),
		rows.first, result.removedTranslations.map { it.id } + rows.second, rows.third,
	)
}

internal fun splitRows(
	result: CreatorResult, editing: Boolean,
): Triple<List<KeptTranslation>, List<Int>, List<Pair<String, String?>>>? {
	val kept = ArrayList<KeptTranslation>()
	val replaced = ArrayList<Int>()
	val additions = ArrayList<Pair<String, String?>>()
	for (row in result.translations) {
		val trls = WordNames.splitEscaped(row.name)
		if (trls[0].isEmpty()) return null
		val trlDescs = WordNames.splitEscaped(row.description)
		if (!editing || row.source == null || trls.size != 1) {
			if (editing && row.source != null) replaced.add(row.source.id)
			for (i in trls.indices)
				additions.add(
					trls[i].trim() to (if (i < trlDescs.size) trlDescs[i].replace("\\t", "\t").trim() else null))
		} else kept.add(KeptTranslation(row.source.id, trls[0].trim(), trlDescs[0].replace("\\t", "\t").trim()))
	}
	return Triple(kept, replaced, additions)
}
