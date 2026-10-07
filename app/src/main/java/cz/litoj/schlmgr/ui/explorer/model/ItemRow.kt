package cz.litoj.schlmgr.ui.explorer.model

import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.dao.WordDao

/**
 * One explorer list row: a [DbItem] plus its browsing context (the parent
 * container's id and the 1-based position in it). The presentation state
 * (flip, selection) lives here so the composables and the controller share one
 * mutable object — the Compose replacement for the former engine-backed
 * `ItemViewState`/`TrackItemModel` pair.
 *
 * A row whose link into this container is a reference link renders as a
 * reference — words excepted, they support multiple parents natively and a
 * referenced word renders like any other.
 */
class ItemRow(item: DbItem, parentId: Int?, position: Int, flip: Boolean = true) {

	var item: DbItem = item
		private set
	var parentId: Int? = parentId
		private set

	var position: Int = position
	var flipped = false
	var selected = false
	var toShow = ""
		private set
	/** The description/info line currently attached to this row. */
	var info = ""
		private set

	/**
	 * The per-translation lines of a flipped word — one entry per translation, its
	 * description empty when it has none. Empty for every other row. The row renders
	 * these under each translation when inline descriptions are on, so a description
	 * stays with its translation instead of piling up below the whole name block.
	 */
	var translationLines: List<TranslationLine> = emptyList()
		private set

	/**
	 * The hit's container chain for a search row (root first, direct parent
	 * last); `null` for browse rows.
	 */
	var path: MutableList<DbItem>? = null

	var isReference = false
		private set

	/** Whether this row is a subject — a top-level item, i.e. a child of the root. */
	val isSubject: Boolean
		get() = parentId == ItemRepository.ROOT_ID

	init {
		setNew(item, parentId, flip)
	}

	fun setNew(item: DbItem, parentId: Int?) = setNew(item, parentId, true)

	fun setNew(item: DbItem, parentId: Int?, flip: Boolean) {
		this.item = item
		this.parentId = parentId
		toShow = nameParser(item.name)
		flipped = false
		translationLines = emptyList()
		// the reference is a fact of the link into this container, not of the
		// item — the same row renders normal in its home
		isReference = item.type != ItemKind.WORD && parentId != null &&
			ItemRepository.linkIsReference(parentId, item.id)
		if (item.type == ItemKind.WORD) {
			flipped = !defFlip
			if (flip) flip()
			else info = item.description
		} else info = item.description
	}

	fun flip() {
		if (item.type != ItemKind.WORD) return
		flipped = !flipped
		if (flipped) toShow = translates()
		else {
			toShow = nameParser(item.name)
			info = item.description
			translationLines = emptyList()
		}
	}

	/** Re-reads the row from the database, keeping the flip state (after edits). */
	fun update() {
		val fresh = ItemRepository.get(item.id) ?: return
		item = fresh
		if (item.type != ItemKind.WORD) {
			info = item.description
			toShow = nameParser(item.name)
			translationLines = emptyList()
			return
		}
		if (flipped) toShow = translates()
		else {
			toShow = nameParser(item.name)
			info = item.description
			translationLines = emptyList()
		}
	}

	/** The flipped display of this word, assembled from its translations. */
	private fun translates(): String {
		val display = flippedDisplay(ItemRepository.translations(item.id))
		info = display.info
		translationLines = display.lines
		return display.names
	}

	companion object {

		/**
		 * The display settings below persist through `AppSettings` — toggling
		 * code assigns them together with the `AppSettings.set` call (the
		 * legacy `setX` methods did both in one place).
		 */
		var showDesc = false
		var parse = true
		var defFlip = true
		var flipAllOnClick = false

		/**
		 * Expands the variant syntax of a name for display: each alternative on
		 * its own line (or `\`-joined when short). Unparsable names stay as-is.
		 */
		@JvmStatic
		fun nameParser(name: String): String {
			if (parse && (name.contains("\\/") || name.contains(")") && name.contains("/"))) {
				val names = WordDao.parseVariants(name)
				if (names.size == 1) return names[0]
				val sb = StringBuilder()
				for (s in names) sb.append(if (s.length > 18) '\n' else '\\').append(s)
				return sb.substring(1)
			}
			return name
		}

		/**
		 * The flipped display of a word, assembled from its translations: the name
		 * block (one translation per line), the flat info text (the descriptions of
		 * the translations that have one, each prefixed with its name when several
		 * do, so the text stays attributable) and the per-translation lines for the
		 * interleaved display.
		 */
		@JvmStatic
		fun flippedDisplay(translations: List<DbItem>): FlippedDisplay {
			val desc = StringBuilder()
			val trls = StringBuilder()
			val lines = ArrayList<TranslationLine>(translations.size)
			val withDesc = ArrayList<DbItem>()
			for (trl in translations) {
				val name = nameParser(trl.name)
				trls.append('\n').append(name)
				lines.add(TranslationLine(name, trl.description))
				if (trl.description.isNotEmpty()) withDesc.add(trl)
			}
			// Show the owning translate's name only when its description
			// would otherwise be ambiguous among multiple descriptions.
			val showNames = withDesc.size > 1
			for (trl in withDesc) {
				desc.append('\n')
				if (showNames) desc.append(trl.name).append(": ")
				desc.append(trl.description)
			}
			return FlippedDisplay(trls.substring(1), if (desc.isNotEmpty()) desc.substring(1) else "", lines)
		}

		/** Rows for the children of a container (or the subjects when [parentId] is null). */
		@JvmStatic
		fun convert(items: List<DbItem>, parentId: Int?): ArrayList<ItemRow> {
			val ret = ArrayList<ItemRow>(items.size)
			var pos = 1
			for (item in items) ret.add(ItemRow(item, parentId, pos++))
			return ret
		}

		/**
		 * A search-hit row: same as a browse row but carrying the hit's container
		 * chain ([path], root first) for the "jump to directory" action.
		 */
		@JvmStatic
		fun of(item: DbItem, path: List<DbItem>, position: Int): ItemRow {
			val row = ItemRow(item, path.lastOrNull()?.id, position, false)
			row.flipped = false
			row.path = ArrayList(path)
			return row
		}
	}
}

/** One translation of a flipped word: its display name and its (possibly empty) description. */
class TranslationLine(val name: String, val description: String)

/** The parts of a flipped word's display — see [ItemRow.flippedDisplay]. */
class FlippedDisplay(val names: String, val info: String, val lines: List<TranslationLine>)
