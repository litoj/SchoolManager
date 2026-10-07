package cz.litoj.schlmgr.ui.explorer.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemKind
import cz.litoj.schlmgr.ui.explorer.model.ExplorerRowVariant
import cz.litoj.schlmgr.ui.explorer.model.ItemUiModel
import cz.litoj.schlmgr.ui.explorer.model.ItemRow

/**
 * The single explorer list row, replacing both legacy layouts (`item_hierarchy.xml`
 * and `item_search.xml`) — one composable serves browsing and search results alike,
 * the two legacy metric variants selected by [variant]. It is reused by the test
 * result rows as well.
 *
 * The row is stateless: every pixel it draws comes from [item], every user action is
 * reported through lambdas. Which trailing actions a row has is decided entirely by
 * which slots the caller provides — an absent slot renders nothing, a provided slot
 * renders the 30dp action icon at the row's end (after the name, in parameter order):
 *
 * - [infoAction]   — `ic_desc_popup`, opens the item's description (shown when the item has
 *   one and inline descriptions are off),
 * - [checkAction]  — the simulated checkbox used by selection mode
 *   ([cz.litoj.schlmgr.ui.explorer.model.ItemRow.selected]).
 *
 * Two passive slots serve the test result rows: [answer] renders a line under the
 * name/description block (the typed answer), [trailing] renders content at the
 * row's end, vertically centered (the success-rate number).
 *
 * Layout mirrors the legacy row: the success-ratio-colored position cell with the
 * type icon at its end, the 20sp name, an optional green 15sp description line
 * underneath (when [descVisible] and the item has one), and the 1px row separator
 * underneath everything right of the position cell (texts and trailing actions),
 * so neighbouring position cells blend.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item(
	item: ItemUiModel,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	descVisible: Boolean,
	modifier: Modifier = Modifier,
	variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
	positionDigits: Int = 1,
	infoAction: (@Composable () -> Unit)? = null,
	checkAction: (@Composable () -> Unit)? = null,
	answer: (@Composable () -> Unit)? = null,
	trailing: (@Composable () -> Unit)? = null,
) {
	Row(
		modifier
			.fillMaxWidth()
			// Like the old `android:layout_height="match_parent"` on the position
			// cell: stretches to the row's intrinsic (name/actions) height.
			.height(IntrinsicHeightRow)
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		PositionCell(item, variant, positionDigits, Modifier.fillMaxHeight())
		// The wrapper column holds the texts and the trailing actions, so the
		// separator below them spans the whole part of the row the success-rate
		// colour does not cover.
		Column(Modifier.weight(1f)) {
			Row(Modifier.fillMaxWidth()) {
				Column(
					Modifier
						.weight(1f)
						.padding(start = 3.dp, top = 3.dp),
				) {
					// A flipped word with translation descriptions keeps each description directly
					// under its own translation; every other row shows the flat name and info line.
					val inlineTranslations = descVisible && item.translations.any { it.description.isNotEmpty() }
					if (inlineTranslations) {
						item.translations.forEach { line ->
							Text(
								line.name,
								fontSize = 20.sp,
								color = NameColor,
								modifier = Modifier.padding(vertical = 2.dp),
							)
							if (line.description.isNotEmpty()) {
								Text(line.description, fontSize = 15.sp, color = DescColor)
							}
						}
					} else {
						Text(
							item.name,
							fontSize = 20.sp,
							color = NameColor,
							modifier = Modifier.padding(vertical = 2.dp),
						)
						if (descVisible && item.desc.isNotEmpty()) {
							Text(item.desc, fontSize = 15.sp, color = DescColor)
						}
					}
					if (answer != null) answer()
				}
				if (infoAction != null) ActionSlot { infoAction() }
				if (checkAction != null) ActionSlot { checkAction() }
				if (trailing != null) Box(Modifier.align(Alignment.CenterVertically)) { trailing() }
			}
			// The separator spans everything right of the position cell — texts
			// and trailing actions alike; the coloured cells of neighbouring rows
			// still touch, so their success-ratio backgrounds blend.
			Box(
				Modifier
					.fillMaxWidth()
					.height(IntrinsicDivider)
					.background(DividerColor),
			)
		}
	}
}

/**
 * The legacy `item_pos` cell: the `n.` number, the type icon at its end and the
 * background derived from the item's success ratio. [digits] is the digit count of
 * the list size: the number is given a fixed width for all rows, so every position
 * column lines up however the numbers grow.
 */
@Composable
private fun PositionCell(
	item: ItemUiModel,
	variant: ExplorerRowVariant,
	digits: Int,
	modifier: Modifier = Modifier,
) {
	val cell = if (variant == ExplorerRowVariant.Browse) CellMetrics.Browse else CellMetrics.Search
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val numberWidth = remember(digits, cell.textSize) {
		with(density) {
			measurer.measure("9".repeat(digits) + ".", TextStyle(fontSize = cell.textSize))
				.size.width.toDp()
		}
	}
	Row(
		modifier
			.background(successRatioBackground(item)),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			stringResource(R.string.position_number, item.position),
			fontSize = cell.textSize,
			color = ExplorerColors.positionText,
			modifier = Modifier
				.padding(horizontal = 2.dp)
				.width(numberWidth),
		)
		Image(
			painterResource(item.iconRes),
			contentDescription = null,
			modifier = Modifier.size(TypeIconSize),
		)
	}
}

/** Centers a 30dp trailing action icon vertically at the row's end, as in the XML. */
@Composable
private fun ActionSlot(content: @Composable () -> Unit) {
	Box(Modifier.size(ActionIconSize), contentAlignment = Alignment.Center) { content() }
}

private val IntrinsicHeightRow = androidx.compose.foundation.layout.IntrinsicSize.Min
private val IntrinsicDivider = 1.dp // legacy used 1px; visually identical at all densities here

/**
 * The success-ratio tint: red-tinted below a 50 % ratio and green-tinted at or above 50,
 * blue for unrated items (`-1`), no colour for empty containers (`-2`). Every colour
 * uses the one low alpha (`0x40`), so the tints have the same, partial transparency and
 * none stands out; the low alpha also keeps them readable over both themes.
 *
 * The legacy `SearchAdapter.background(int)` gave the ratios 0, 50 and 100 their own
 * colours with a higher saturation (no blue) and another alpha, and used the stronger
 * `0x60` above 50 %; 50 % was a bright, near-opaque yellow. Every colour now shares the
 * one alpha.
 *
 * @param sf success rate, `-1` for no rating, `-2` for no children
 */
internal fun successRatioArgb(sf: Int): Int = when (sf) {
	-2 -> 0
	-1 -> 0x401A6AB8
	// legacy: "FF" + hex(sf*256/50) + "22" below 50, hex((100-sf)*256/50) + "FF22" at or above
	else -> if (sf < 50)
		0x40FF0000 or ((sf * 256 / 50).coerceIn(0, 255) shl 8) or 0x22
	else
		0x40000000 or (((100 - sf) * 256 / 50).coerceIn(0, 255) shl 16) or 0xFF00 or 0x22
}

/** The position cell's background for [item]; a reference cell always renders none. */
fun successRatioBackground(item: ItemUiModel): Color =
	if (item.isReference) {
		// The legacy code passes 0x1a6ab8 WITHOUT an alpha byte to setBackgroundColor,
		// which Android parses as fully transparent — reference cells render with no
		// background. Kept as-is to preserve the app's current look.
		Color(0x001A6AB8)
	} else Color(successRatioArgb(item.ratio))

private class CellMetrics private constructor(val textSize: androidx.compose.ui.unit.TextUnit) {
	companion object {
		/** `item_hierarchy.xml` — 18sp number. */
		val Browse = CellMetrics(18.sp)

		/** `item_search.xml` — 15sp number. */
		val Search = CellMetrics(15.sp)
	}
}

/** `#D000` from the XML layouts — now sourced from the app's theme foreground. */
val NameColor @Composable get() = ExplorerColors.body

/** `#080` — the item description line, sourced from the theme. */
val DescColor @Composable get() = ExplorerColors.desc

/** `#4888` row separator — sourced from the theme. */
val DividerColor @Composable get() = ExplorerColors.rowDivider

private val TypeIconSize = 30.dp
private val ActionIconSize = 30.dp

@Preview(showBackground = true, backgroundColor = 0xFFFFFF, name = "Row variants")
@Composable
private fun ItemPreviews() {
	Column {
		Item(PreviewModels.subject(3, 64, "Mathematics"), {}, {}, descVisible = false)
		Item(PreviewModels.chapter(1, -1, "Integrals"), {}, {}, descVisible = false)
		Item(PreviewModels.chapter(2, 23, "Derivatives"), {}, {}, descVisible = false)
		Item(
			PreviewModels.word(4, 100, "das Haus"),
			{}, {}, descVisible = false,
			infoAction = { Image(painterResource(R.drawable.ic_desc_popup), null) },
		)
		Item(
			PreviewModels.word(5, 0, "die Schule\n школа").copy(flipped = true),
			{}, {},
			descVisible = true,
			checkAction = { Image(painterResource(R.drawable.ic_check_box_filled), null) },
		)
		Item(
			PreviewModels.chapter(6, -1, "Old results"),
			{}, {}, descVisible = false,
			variant = ExplorerRowVariant.Search,
		)
		Item(PreviewModels.reference(8, "see: Algebra"), {}, {}, descVisible = false)
	}
}

/** Fabricates preview rows without touching the database. */
private object PreviewModels {
	private var id = 0

	// Renders read [ItemUiModel] only; the payload just has to exist. A word row
	// with flipping off is the one [ItemRow] that builds without the database.
	private val payload = ItemRow(DbItem(name = "preview", description = "", type = ItemKind.WORD), null, 0, false)

	fun subject(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, R.drawable.ic_subject)

	fun chapter(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, R.drawable.ic_chapter)

	fun word(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, R.drawable.ic_word).copy(isWord = true, desc = "podstatné jméno")

	fun reference(pos: Int, name: String) =
		row(pos, -1, name, R.drawable.ic_ref).copy(isReference = true)

	private fun row(pos: Int, ratio: Int, name: String, icon: Int) = ItemUiModel(
		key = "preview-${id++}", position = pos, name = name, desc = "",
		iconRes = icon, ratio = ratio, isReference = false, isWord = false,
		flipped = false, selected = false, payload = payload,
	)
}
