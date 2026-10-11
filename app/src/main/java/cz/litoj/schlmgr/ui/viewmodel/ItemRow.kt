package cz.litoj.schlmgr.ui.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * One explorer list row — a [DbItem] plus parent id, 1-based position, flip
 * and selection — one shared object for composables and controller. A
 * reference-linked row keeps its type icon, with a jump to its source.
 * - the mutable fields are main-owned (an io read snapshots them on
 *   main first) — a main-thread write must not race the read.
 *
 * @param applyDefFlip whether the word takes the default flip state ([defFlip])
 */
class ItemRow(item: DbItem, parentId: Int?, position: Int, applyDefFlip: Boolean = true) {

	var item: DbItem = item
		private set
	var parentId: Int? = parentId
		private set

	var position: Int = position
	var flipped = false
	var selected = false
	var toShow = ""
		private set
	var info = ""
		private set

	/**
	 * The per-translation lines of a flipped word — one entry per translation,
	 * empty for every other row. Rendered under each translation when inline
	 * descriptions are on, so a description stays with its translation.
	 */
	var translationLines: List<TranslationLine> = emptyList()
		private set

	/**
	 * The hit's container chain for a search row (root first, direct parent
	 * last) — `null` for browse rows.
	 */
	var path: MutableList<DbItem>? = null

	var isReference = false
		private set

	/**
	 * Whether the referenced row still lives in a container besides this one —
	 * its source can be gone (the home was deleted while this reference link
	 * kept the row alive), and without another container there is nowhere to
	 * jump to, so the reference icon stays hidden then.
	 */
	var hasSource = false
		private set

	val isSubject: Boolean
		get() = parentId == ROOT_ID

	init {
		setNew(item, parentId, applyDefFlip)
	}

	@VisibleForTesting
	fun setNew(item: DbItem, parentId: Int?) = setNew(item, parentId, true)

	/**
	 * Re-reads the row from [item] under [parentId].
	 * - [applyDefFlip] true takes the default flip state ([defFlip]) — false
	 *   always leaves the word unflipped.
	 */
	fun setNew(item: DbItem, parentId: Int?, applyDefFlip: Boolean) {
		this.item = item
		this.parentId = parentId
		flipped = false
		renderName()
		refreshReferenceFacts(parentId)
		if (item.type != ItemKind.WORD) return
		flipped = if (applyDefFlip) defFlip else false
		if (flipped) toShow = renderTranslations()
	}

	/**
	 * Flips this word — the blocking composed form. Production callers read
	 * the translations on io and land [applyFlip] on main instead.
	 */
	@VisibleForTesting
	fun flip() = applyFlip(ItemRepository.translations(item.id))

	/**
	 * Applies a flip with pre-read translations — runs on the main thread.
	 * A non-word row stays put.
	 */
	fun applyFlip(translations: List<DbItem>) {
		if (item.type != ItemKind.WORD) return
		flipped = !flipped
		if (flipped) toShow = renderTranslations(translations) else renderName()
	}

	/**
	 * Reads the moved row's placement for [parentId]: parent, reference
	 * facts and the search chain (a browse row carries none). Up to three
	 * database reads (two for a browse row), so callers stay off the main
	 * thread with the fresh row in hand — [applyPlacement] lands the
	 * result there, keeping the row's fields main-owned.
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
	 * The reference facts of the link into [parentId]: a reference is a fact
	 * of the link, not of the item — the same row renders normal in its home.
	 */
	private fun refreshReferenceFacts(parentId: Int?) {
		isReference = parentId != null && ItemRepository.linkIsReference(parentId, item.id)
		hasSource = isReference && ItemRepository.parentIds(item.id).any { it != parentId }
	}

	/**
	 * The default flip from preloaded translations — the browse build's
	 * form. Called before the row is published, so the main-owned fields
	 * stay single-threaded (see the class doc).
	 */
	fun applyDefaultFlip(translations: List<DbItem>) {
		if (item.type != ItemKind.WORD || !defFlip) return
		flipped = true
		toShow = renderTranslations(translations)
	}

	/**
	 * Re-reads the row from the database, keeping the flip state (after
	 * edits) — the blocking composed form. Production callers read on io
	 * and land [applyFresh] on main instead.
	 */
	@VisibleForTesting
	fun update() = applyFresh(ItemRepository.get(item.id), ItemRepository.translations(item.id))

	/**
	 * Applies a fresh read of the row — runs on the main thread, the reads
	 * happened on the caller's io side (a `null` row, deleted meanwhile,
	 * changes nothing).
	 */
	fun applyFresh(fresh: DbItem?, translations: List<DbItem>) {
		if (fresh == null) return
		item = fresh
		if (item.type != ItemKind.WORD) {
			renderName()
			return
		}
		if (flipped) toShow = renderTranslations(translations) else renderName()
	}

	private fun renderName() {
		toShow = nameParser(item.name)
		info = item.description
		translationLines = emptyList()
	}

	private fun renderTranslations(): String =
		renderTranslations(ItemRepository.translations(item.id))

	private fun renderTranslations(translations: List<DbItem>): String {
		val display = flippedDisplay(translations)
		info = display.info
		translationLines = display.lines
		return display.names
	}

	companion object {

		/**
		 * The display settings below persist through `AppSettings` — toggling
		 * code assigns them together with the `AppSettings.set` call.
		 */
		// showDesc is Compose state so a settings toggle reaches a live
		// explorer list without a reload
		var showDesc by mutableStateOf(false)
		var parse = true
		var defFlip = true
		var flipAllOnClick = false

		/**
		 * The row display's own half of the stored options — the app layer
		 * applies the rest. This one reads the ui's settings from the ui.
		 */
		@JvmStatic
		fun applyStoredOptions() {
			defFlip = AppSettings.getBool("flipWord", true)
			flipAllOnClick = AppSettings.getBool("flipAllOnClick", false)
			parse = AppSettings.getBool("parseNames", true)
			showDesc = AppSettings.getBool("doShowDesc", false)
		}

		/**
		 * Expands a name's variant syntax for display — one alternative per line
		 * (`\`-joined when short). Unparsable names stay as-is.
		 */
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

		/** A variant longer than this goes on its own display line instead of joining with `\`. */
		private const val SINGLE_LINE_MAX = 18

		/**
		 * The flipped display of a word, assembled from its translations — the name
		 * block (one translation per line), the flat info text (the descriptions of
		 * the translations that have one, each prefixed with its name when several
		 * do, so the text stays attributable) and the per-translation lines for the
		 * interleaved display.
		 */
		@JvmStatic
		fun flippedDisplay(translations: List<DbItem>): FlippedDisplay {
			val infoText = StringBuilder()
			val trls = StringBuilder()
			val lines = ArrayList<TranslationLine>(translations.size)
			val withDescription = ArrayList<DbItem>()
			for (trl in translations) {
				val name = nameParser(trl.name)
				trls.append('\n').append(name)
				lines.add(TranslationLine(name, trl.description))
				if (trl.description.isNotEmpty()) withDescription.add(trl)
			}
			// Show the owning translate's name only when its description
			// would otherwise be ambiguous among multiple descriptions.
			val showNames = withDescription.size > 1
			for (trl in withDescription) {
				infoText.append('\n')
				if (showNames) infoText.append(trl.name).append(": ")
				infoText.append(trl.description)
			}
			return FlippedDisplay(
			// a translation-less word (a mis-anchored import) has no names to cut apart
			if (trls.isNotEmpty()) trls.substring(1) else "",
			if (infoText.isNotEmpty()) infoText.substring(1) else "", lines)
		}

		/**
		 * Rows for a container's children from preloaded sets — the
		 * reference facts come from [links], a default-flipped word's
		 * translations from [translations] (keyed by word id), so the build
		 * touches no database.
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
				ret.add(ofBrowse(item, parentId, pos++, byChild[item.id] ?: emptyList(), translations[item.id] ?: emptyList()))
			return ret
		}

		/**
		 * A browse row with preloaded parent links and translations — as the
		 * constructor row, but its reference facts come from [links] and its
		 * default flip from [translations] instead of per-row queries.
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
			row.applyDefaultFlip(translations)
			return row
		}

		/**
		 * A search-hit row carrying the hit's container chain ([path], root first) for
		 * the "jump to directory" action — its reference facts come from the preloaded
		 * [links], so a run with thousands of hits stays database-free after preload.
		 */
		@JvmStatic
		fun ofSearchHit(item: DbItem, path: List<DbItem>, position: Int, links: List<DbLink>): ItemRow {
			val parent = path.lastOrNull()?.id
			val row = ItemRow(item, null, position, applyDefFlip = false)
			row.path = ArrayList(path)
			row.parentId = parent
			row.isReference = links.any { it.parentId == parent && it.isReference }
			row.hasSource = row.isReference && links.any { it.parentId != parent }
			return row
		}
	}
}

/** One translation of a flipped word — its display name and its (possibly empty) description. */
class TranslationLine(val name: String, val description: String)

/** The parts of a flipped word's display — see [ItemRow.flippedDisplay]. */
class FlippedDisplay(val names: String, val info: String, val lines: List<TranslationLine>)
