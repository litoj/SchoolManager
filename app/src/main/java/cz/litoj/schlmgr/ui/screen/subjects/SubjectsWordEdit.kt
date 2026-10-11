package cz.litoj.schlmgr.ui.screen.subjects

import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.KeptTranslation
import cz.litoj.schlmgr.data.db.ROOT_ID
import cz.litoj.schlmgr.data.db.WordEdit
import cz.litoj.schlmgr.data.ItemRepository
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
 * Word edit/creation — popup commits, New actions.
 */
internal class SubjectsWordEdit(
	private val scope: CoroutineScope,
	private val io: CoroutineDispatcher,
	private val host: Host,
) {

	interface Host {
		fun items(): List<ItemRow>

		fun selectionCount(): Int

		/** Open chain — the new item lands in last. */
		val path: List<DbItem>

		/** Enqueues view request. */
		fun emitRequest(request: Request)

		/** Runs [work] on main. */
		fun postToUi(work: Runnable)

		fun clearSelection()

		/** Re-reads open container. */
		fun reloadContent()

		fun refreshInfo()

		fun addItem(item: ItemRow)

		fun addItem(index: Int, item: ItemRow)
	}

	/** Opens editor for single selection. */
	fun onEditAction() {
		if (host.selectionCount() != 1) return
		val row = host.items().firstOrNull(ItemRow::selected) ?: return
		val item = row.item
		if (item.type == ItemKind.WORD) {
			scope.launch(io) {
				val rows = ArrayList<TranslationRow>()
				for (trl in ItemRepository.translations(item.id)) {
					rows.add(TranslationRow(trl.name, trl.description, trl))
				}
				host.postToUi { host.emitRequest(Request.EditWord(row, rows)) }
			}
		} else {
			host.emitRequest(Request.EditItem(row))
		}
	}

	/**
	 * `false` keeps popup open. Fork emits [Request.ForkReferences].
	 */
	fun commitWordEdit(row: ItemRow, result: CreatorResult): Boolean {
		val edit = wordEditOf(result, row) ?: return false
		val rowId = row.item.id
		val rowParent = row.parentId
		scope.launch(io) {
			val references =
				if (ItemRepository.editForks(edit)) ItemRepository.referencesOf(rowId)
				else emptyList()
			if (references.isEmpty()) commitWordEditNow(row, edit, rowId, rowParent)
			else host.postToUi { host.emitRequest(Request.ForkReferences(row, edit, references)) }
		}
		return true
	}

	/** Commits fork — only kept references follow. */
	fun commitForkReferences(row: ItemRow, edit: WordEdit, kept: List<Int>) {
		val rowId = row.item.id
		val rowParent = row.parentId
		scope.launch(io) {
			commitWordEditNow(row, edit.copy(referencesToFork = kept), rowId, rowParent)
		}
	}

	/** Lands edit and re-reads list. Join re-reads placement here. */
	private suspend fun commitWordEditNow(row: ItemRow, edit: WordEdit, rowId: Int, rowParent: Int?) {
		val word = ItemRepository.editWord(edit)
		val placement = if (word.id != rowId && rowParent != null) row.readPlacement(rowParent, word) else null
		host.postToUi {
			placement?.let { row.applyPlacement(it) }
			host.reloadContent()
			host.clearSelection()
			host.refreshInfo()
		}
	}

	/** Commits simple editor. `false` on empty name. */
	fun commitItemEdit(row: ItemRow, result: CreatorResult): Boolean {
		if (result.name.isEmpty()) return false
		val description = result.description.replace("\\t", "\t")
		val rename = result.name != row.item.name
		// snapshot on main
		val rowId = row.item.id
		scope.launch(io) {
			ItemRepository.setDescription(rowId, description)
			if (rename) ItemRepository.rename(rowId, result.name)
			host.postToUi {
				host.reloadContent()
				host.clearSelection()
				host.refreshInfo()
			}
		}
		return true
	}

	/**
	 * Commits new word per `\`-split name. `false` on incomplete input or closed container.
	 * Each name appends — the database links at the end.
	 */
	private fun applyWord(result: CreatorResult): Boolean {
		if (result.name.isEmpty() || result.translations.isEmpty()) return false
		val parentId = host.path.lastOrNull()?.id ?: return false
		val additions = splitRows(result, editing = false)?.third ?: return false
		val names = WordNames.splitEscaped(result.name)
		if (names.any { it.isEmpty() }) return false
		val descs = WordNames.splitEscaped(result.description)
		scope.launch(io) {
			val created = ArrayList<ItemRow>()
			for (i in names.indices) {
				val w = ItemRepository.createWord(
					parentId, names[i],
					if (i < descs.size) descs[i].replace("\\t", "\t") else null,
					additions,
				)
				created.add(ItemRow(w, parentId, 0))
			}
			// the live list renumbers on insert — the rows append in order
			host.postToUi { created.forEach { host.addItem(it) } }
		}
		return true
	}

	// ==========================================================================
	//  creation (menu)
	// ==========================================================================

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

	/** Commits new item. `false` on incomplete input. */
	fun commitNewItem(kind: RowKind, result: CreatorResult): Boolean {
		if (result.name.isEmpty()) return false
		when (kind) {
			RowKind.SUBJECT -> {
				val description = result.description.replace("\\t", "\t")
				scope.launch(io) {
					val mch = ItemRepository.createSubject(result.name, description)
					// Row builds here — reads the database.
					val row = ItemRow(mch, ROOT_ID, 0)
					host.postToUi { host.addItem(row) }
				}
			}
			RowKind.CHAPTER, RowKind.NOTE -> {
				val par = host.path.lastOrNull() ?: return false
				val description = result.description.replace("\\t", "\t")
				scope.launch(io) {
					val created = if (kind == RowKind.CHAPTER)
						ItemRepository.createChapter(par.id, result.name, description)
					else
						ItemRepository.createNote(par.id, result.name, description)
					host.postToUi { host.addItem(ItemRow(created, par.id, 0)) }
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
					trls[i] to (if (i < trlDescs.size) trlDescs[i].replace("\\t", "\t") else null))
		} else kept.add(KeptTranslation(row.source.id, trls[0], trlDescs[0].replace("\\t", "\t")))
	}
	return Triple(kept, replaced, additions)
}
