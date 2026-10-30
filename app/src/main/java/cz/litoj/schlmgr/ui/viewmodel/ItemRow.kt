package cz.litoj.schlmgr.ui.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cz.litoj.schlmgr.app.AppSettings
import cz.litoj.schlmgr.data.ItemRepository
import cz.litoj.schlmgr.data.WordNames
import cz.litoj.schlmgr.data.db.DbItem
import cz.litoj.schlmgr.data.db.DbLink
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.data.db.ROOT_ID

class Placement(
	val parentId: Int, val isReference: Boolean, val hasSource: Boolean, val chain: List<DbItem>?,
)

/**
 * One shared row object for composables and the controller.
 * - a reference-linked row keeps its type icon, with a jump to its source.
 * - the mutable fields are main-owned, since an io read snapshots them on main first.
 */
class ItemRow(item: DbItem, parentId: Int?, position: Int, applyDefFlip: Boolean = true) {

	var item: DbItem = item
		private set
	var parentId: Int? = parentId
		private set

	var position: Int = position
		private set
	var flipped = false
		private set
	var selected = false
		private set
	var toShow = ""
		private set
	var info = ""
		private set

	/**
	 * The per-translation lines of a flipped word, one entry per translation.
	 * - the list is empty for every other row.
	 * - it renders under each translation when inline descriptions are on.
	 * - so a description stays with its translation.
	 */
	var translationLines: List<TranslationLine> = emptyList()
		private set

	/**
	 * The container chain of the hit for a search row, with the root first.
	 * - the direct parent comes last, and null marks a browse row.
	 */
	var path: MutableList<DbItem>? = null
		private set

	var isReference = false
		private set

	/**
	 * The flip content of the row, preloaded with the list so a flip reads no database.
	 * - null marks an unpreloaded row, the flip path reads on io then.
	 */
	private var flipContent: List<DbItem>? = null

	/**
	 * Whether the referenced row still lives in a container besides this one.
	 * - its source can be gone, since the home was deleted while the link kept the row alive.
	 * - without another container there is nowhere to jump, so the icon stays hidden.
	 */
	var hasSource = false
		private set

	val isSubject: Boolean
		get() = parentId == ROOT_ID

	/** The mark write of the selection machine, see [Selection]. */
	fun select(on: Boolean) {
		selected = on
	}

	/** The renumber write of the list, which every insert, move and sort runs. */
	fun renumber(to: Int) {
		position = to
	}

	init {
		setNew(item, parentId, applyDefFlip)
	}

	@VisibleForTesting
	fun setNew(item: DbItem, parentId: Int?) = setNew(item, parentId, true)

	fun setNew(item: DbItem, parentId: Int?, applyDefFlip: Boolean) {
		this.item = item
		this.parentId = parentId
		flipped = false
		flipContent = null
		renderName()
		refreshReferenceFacts(parentId)
		if (item.type != ItemKind.WORD) return
		flipped = if (applyDefFlip) defFlip else false
		if (flipped) toShow = renderTranslations()
	}

	/**
	 * The blocking form. Production callers read the flip content on io and land [applyFlip] on main.
	 */
	@VisibleForTesting
	fun flip() = applyFlip(flipContent())

	private fun flipContent(): List<DbItem> = flipContentOf(item)

	fun applyFlip(items: List<DbItem>) {
		if (item.type != ItemKind.WORD && item.type != ItemKind.TRANSLATION) return
		flipped = !flipped
		if (flipped) toShow = renderTranslations(items) else renderName()
	}

	/** Preloads the flip content, so the flips of the row read no database. */
	fun preloadFlip(items: List<DbItem>) {
		flipContent = items
	}

	/**
	 * Flips onto [flippedTo] with the preloaded content.
	 * - a false return means no content was preloaded, the caller reads on io.
	 */
	fun flipOnto(flippedTo: Boolean): Boolean {
		val content = flipContent ?: return false
		if (flipped != flippedTo) applyFlip(content)
		return true
	}

	/**
	 * The io half of a placement change.
	 * - it also reads the search chain, while a browse row carries none.
	 * - that takes up to three database reads, two for a browse row, so callers stay off main.
	 * - they hand the fresh row over, and [applyPlacement] lands the result there.
	 * - so the fields of the row stay main-owned.
	 */
	fun readPlacement(parentId: Int, fresh: DbItem): Placement {
		val reference = ItemRepository.linkIsReference(parentId, fresh.id)
		return Placement(parentId, reference,
			reference && ItemRepository.parentIds(fresh.id).any { it != parentId },
			path?.let { ItemRepository.pathTo(parentId) + fresh })
	}

	fun applyPlacement(placement: Placement) {
		parentId = placement.parentId
		isReference = placement.isReference
		hasSource = placement.hasSource
		val chain = path ?: return
		chain.clear()
		chain.addAll(placement.chain ?: return)
	}

	/**
	 * A reference is a fact of the link, not of the item.
	 * - so the same row renders normal in its home.
	 */
	private fun refreshReferenceFacts(parentId: Int?) {
		isReference = parentId != null && ItemRepository.linkIsReference(parentId, item.id)
		hasSource = isReference && ItemRepository.parentIds(item.id).any { it != parentId }
	}

	/**
	 * The browse build form of the default flip.
	 * - it runs before the row is published, so the main-owned fields stay single-threaded.
	 * - see the class doc.
	 */
	fun applyDefaultFlip(translations: List<DbItem>) {
		if (item.type != ItemKind.WORD || !defFlip) return
		flipped = true
		toShow = renderTranslations(translations)
	}

	/**
	 * The blocking form, while production callers read on io.
	 * - they land [applyFresh] on main instead.
	 */
	@VisibleForTesting
	fun update() = applyFresh(ItemRepository.get(item.id), flipContent())

	fun applyFresh(fresh: DbItem?, items: List<DbItem>) {
		if (fresh == null) return
		item = fresh
		if (item.type != ItemKind.WORD && item.type != ItemKind.TRANSLATION) {
			renderName()
			return
		}
		flipContent = items
		if (flipped) toShow = renderTranslations(items) else renderName()
	}

	private fun renderName() {
		toShow = nameParser(item.name)
		info = item.description
		translationLines = emptyList()
	}

	private fun renderTranslations(): String = renderTranslations(flipContent())

	private fun renderTranslations(items: List<DbItem>): String {
		val display = flippedDisplay(items)
		// no joint description, since each line carries its own
		info = ""
		translationLines = display.lines
		return display.names
	}

	companion object {

		/**
		 * A word shows its translations, and a translation its words.
		 * - those are the actual words that the translation translates, each in its chapter.
		 */
		fun flipContentOf(item: DbItem): List<DbItem> =
			if (item.type == ItemKind.TRANSLATION) ItemRepository.parentRows(item.id)
			else ItemRepository.translations(item.id)

		/**
		 * The display settings below persist through AppSettings.
		 * - the toggling code assigns them together with the AppSettings.set call.
		 */
		// descLines is Compose state, so a settings change reaches a live list without a reload
		var descLines by mutableIntStateOf(DEFAULT_DESC_LINES)
			private set
		var parse = true
			private set
		var defFlip = true
			private set

		/** The double-tap window, a settings change rebuilds the list detector. */
		var doubleTapMs by mutableIntStateOf(DEFAULT_DOUBLE_TAP_MS)
			private set

		const val DEFAULT_DESC_LINES = 5
		const val DEFAULT_DOUBLE_TAP_MS = 300

		/**
		 * A blank description never shows, and a longer one collapses to its icon.
		 * - at or under [descLines] non-empty lines it shows directly.
		 */
		@JvmStatic
		fun showsDescription(description: String): Boolean {
			if (description.isBlank()) return false
			return description.lines().count { it.isNotBlank() } <= descLines
		}

		/** The app layer applies the rest, while this one reads the ui settings. */
		@JvmStatic
		fun applyStoredOptions() {
			applyOptions(
				AppSettings.getBool("parseNames", true),
				AppSettings.getBool("flipWord", true),
				AppSettings.getInt("descLines", DEFAULT_DESC_LINES),
				AppSettings.getInt("doubleTapMs", DEFAULT_DOUBLE_TAP_MS),
			)
		}

		/** One mutation door, the stored load and the settings screen share it. */
		@JvmStatic
		fun applyOptions(parse: Boolean, defFlip: Boolean, descLines: Int, doubleTapMs: Int) {
			this.parse = parse
			this.defFlip = defFlip
			this.descLines = descLines
			this.doubleTapMs = doubleTapMs
		}

		@JvmStatic
		fun nameParser(name: String): String {
			if (parse && (name.contains("\\/") || name.contains(")") && name.contains("/"))) {
				val names = WordNames.parseVariants(name)
				if (names.size == 1) return names[0]
				val sb = StringBuilder()
				for (s in names) sb.append(if (s.length > SINGLE_LINE_MAX) '\n' else '\\').append(s)
				return sb.substring(1)
			}
			return name
		}

		private const val SINGLE_LINE_MAX = 18

		/**
		 * Each line keeps its own description, so no joint description text is assembled.
		 */
		@JvmStatic
		fun flippedDisplay(items: List<DbItem>): FlippedDisplay {
			val trls = StringBuilder()
			val lines = ArrayList<TranslationLine>(items.size)
			for (trl in items) {
				val name = nameParser(trl.name)
				trls.append('\n').append(name)
				lines.add(TranslationLine(name, trl.description))
			}
			return FlippedDisplay(
			// a translation-less word from a mis-anchored import has no names to cut apart
			if (trls.isNotEmpty()) trls.substring(1) else "", lines)
		}

		/**
		 * Rows for the children of a container from preloaded sets.
		 * - the reference facts come from [links].
		 * - the translations of a default-flipped word come from [translations], keyed by word id.
		 * - so the build touches no database.
		 */
		@JvmStatic
		fun convert(
			items: List<DbItem>,
			parentId: Int?,
			links: List<DbLink>,
			translations: Map<Int, List<DbItem>>,
		): ArrayList<ItemRow> {
			val byChild = links.groupBy { it.childId }
			val ret = ArrayList<ItemRow>(items.size)
			var pos = 1
			for (item in items)
				ret.add(ofBrowse(item, parentId, pos++, byChild[item.id] ?: emptyList(),
					translations[item.id] ?: emptyList()))
			return ret
		}

		/**
		 * A browse row with preloaded parent links and translations, as the constructor row.
		 * - its reference facts come from [links] instead of per-row queries.
		 * - so does its default flip, from [translations].
		 */
		@JvmStatic
		fun ofBrowse(
			item: DbItem,
			parentId: Int?,
			position: Int,
			links: List<DbLink>,
			translations: List<DbItem>,
		): ItemRow {
			val row = ItemRow(item, null, position, applyDefFlip = false)
			row.parentId = parentId
			row.isReference = parentId != null && links.any { it.parentId == parentId && it.isReference }
			row.hasSource = row.isReference && links.any { it.parentId != parentId }
			row.preloadFlip(translations)
			row.applyDefaultFlip(translations)
			return row
		}

		/**
		 * A search-hit row carrying the container chain of the hit, see [path].
		 * - the chain serves the jump to directory action.
		 * - its reference facts come from the preloaded [links].
		 * - so a run with thousands of hits stays database-free after preload.
		 * @param flipContent the translations of a word hit, null leaves the row to read on demand
		 */
		@JvmStatic
		fun ofSearchHit(
			item: DbItem,
			path: List<DbItem>,
			position: Int,
			links: List<DbLink>,
			flipContent: List<DbItem>? = null,
		): ItemRow {
			val parent = path.lastOrNull()?.id
			val row = ItemRow(item, null, position, applyDefFlip = false)
			row.path = ArrayList(path)
			row.parentId = parent
			row.isReference = links.any { it.parentId == parent && it.isReference }
			row.hasSource = row.isReference && links.any { it.parentId != parent }
			if (flipContent != null) row.preloadFlip(flipContent)
			return row
		}
	}
}

class TranslationLine(val name: String, val description: String)

class FlippedDisplay(val names: String, val lines: List<TranslationLine>)
