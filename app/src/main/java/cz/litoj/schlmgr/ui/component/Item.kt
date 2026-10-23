package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.ItemRow
import cz.litoj.schlmgr.ui.viewmodel.ItemUiModel
import cz.litoj.schlmgr.ui.viewmodel.RowKind
import cz.litoj.schlmgr.ui.viewmodel.TranslationLine

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Item(
	item: ItemUiModel,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	descVisible: Boolean,
	modifier: Modifier = Modifier,
	variant: ExplorerRowVariant = ExplorerRowVariant.Browse,
	positionWidth: Dp,
	infoAction: (@Composable () -> Unit)? = null,
	answer: (@Composable () -> Unit)? = null,
	trailing: (@Composable () -> Unit)? = null,
	referenceAction: (@Composable () -> Unit)? = null,
	onInnerInfo: ((TranslationLine) -> Unit)? = null,
) {
	Row(
		modifier
			.fillMaxWidth()
			// the selection rides the background of the row, which paints under the
			// success-ratio tint of the position cell, so the whole row highlights
			.background(if (item.selected) ExplorerColors.selectedRow else Color.Transparent)
			.successRatioBand(item, tintBandWidth(positionWidth))
			.combinedClickable(onClick = onClick, onLongClick = onLongClick),
	) {
		PositionCell(item, variant, positionWidth, Modifier.align(Alignment.CenterVertically))
		// the wrapper column holds the texts and the trailing actions.
		// so the separator below them spans the part of the row without the success-rate colour.
		Column(Modifier.weight(1f)) {
			Row(Modifier.fillMaxWidth()) {
				Column(
					Modifier
						.weight(1f)
						.padding(start = 3.dp, top = 3.dp),
				) {
					if (item.translations.isNotEmpty()) {
						// a flipped row lists the inner items under the unified score of the row.
						// each line carries its own description, inline or as the icon.
						item.translations.forEach { line ->
							Row(Modifier.fillMaxWidth()) {
								Column(Modifier.weight(1f)) {
									Text(
										line.name,
										fontSize = 20.sp,
										color = ExplorerColors.body,
										modifier = Modifier.padding(vertical = 2.dp),
									)
									if (descVisible && ItemRow.showsDescription(line.description)) {
										Text(line.description, fontSize = 15.sp, color = ExplorerColors.description)
									}
								}
								if (line.description.isNotEmpty() && !ItemRow.showsDescription(line.description) && onInnerInfo != null) {
									ActionSlot { InfoActionIcon { onInnerInfo(line) } }
								}
							}
						}
					} else {
						Text(
							item.name,
							fontSize = 20.sp,
							color = ExplorerColors.body,
							modifier = Modifier.padding(vertical = 2.dp),
						)
						if (descVisible && ItemRow.showsDescription(item.description)) {
							Text(item.description, fontSize = 15.sp, color = ExplorerColors.description)
						}
					}
					if (answer != null) answer()
				}
				if (infoAction != null) ActionSlot { infoAction() }
				if (trailing != null) Box(Modifier.align(Alignment.CenterVertically)) { trailing() }
				if (referenceAction != null) ActionSlot { referenceAction() }
			}
			HairlineDivider()
		}
	}
}

@Composable
private fun PositionCell(
	item: ItemUiModel,
	variant: ExplorerRowVariant,
	width: Dp,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier,
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			positionText(item.position),
			fontSize = CellMetrics.of(variant).textSize,
			color = ExplorerColors.positionText,
			maxLines = 1,
			softWrap = false,
			modifier = Modifier
				.padding(horizontal = CellSidePadding)
				.width(width),
		)
		Image(
			painterResource(item.kind.iconRes),
			contentDescription = null,
			modifier = Modifier.size(TypeIconSize),
		)
	}
}

internal fun positionText(position: Int): String = "$position."

/**
 * The one width every position cell of a list takes: the widest displayed
 * position at the number size of the variant, measured once.
 * - rows only consume the result, so all numbers line up.
 */
@Composable
internal fun positionColumnWidth(maxPosition: Int, variant: ExplorerRowVariant): Dp {
	val measurer = rememberTextMeasurer()
	val density = LocalDensity.current
	val size = CellMetrics.of(variant).textSize
	// the density and the font scale change the pixels, so they reopen the measure.
	return remember(maxPosition, size, density, density.fontScale) {
		with(density) {
			val style = TextStyle(fontSize = size)
			val widths = IntArray(10) { measurer.measure(it.toString(), style).size.width }
			measurer.measure(positionText(widestPosition(maxPosition) { widths[it] }), style)
				.size.width.toDp()
		}
	}
}

@Composable
private fun ActionSlot(content: @Composable () -> Unit) {
	Box(Modifier.size(ActionIconSize), contentAlignment = Alignment.Center) { content() }
}

/**
 * Success-ratio tint, all at one low alpha so it stays readable over both themes.
 * - a red-to-yellow ramp below 50 % and a yellow-to-green ramp above it.
 * - blue for an unrated row, nothing for an empty container.
 */
internal fun successRatioArgb(ratio: Int): Int = when (ratio) {
	-2 -> 0
	-1 -> 0x401A6AB8
	// below 50 the green channel ramps up with the ratio, so the tint turns yellow
	// above 50 the red channel fades with the ratio, so the tint runs to green
	else -> if (ratio < 50)
		0x40FF0000 or ((ratio * 256 / 50).coerceIn(0, 255) shl 8) or 0x22
	else
		0x40000000 or (((100 - ratio) * 256 / 50).coerceIn(0, 255) shl 16) or 0xFF00 or 0x22
}

internal fun successRatioBackground(item: ItemUiModel): Color = Color(successRatioArgb(item.ratio))

private val CellSidePadding = 2.dp

private fun tintBandWidth(positionWidth: Dp) = positionWidth + CellSidePadding * 2 + TypeIconSize

/**
 * The success-ratio band behind the position cell, painted in the draw phase.
 * - it spans the full row height, which the text column sets.
 * - measuring that height in layout would cost the intrinsic pass, about 13 % of the scroll floor.
 * - so the band is only drawn, with the cell content centered.
 * - [tintBandWidth] gives the width of the band.
 */
private fun Modifier.successRatioBand(item: ItemUiModel, bandWidth: Dp): Modifier = drawBehind {
	val band = bandWidth.toPx()
	val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - band
	drawRect(
		successRatioBackground(item),
		topLeft = Offset(x, 0f),
		size = Size(band, size.height),
	)
}

private class CellMetrics private constructor(val textSize: androidx.compose.ui.unit.TextUnit) {
	companion object {
		val Browse = CellMetrics(18.sp)

		val Search = CellMetrics(15.sp)

		fun of(variant: ExplorerRowVariant) =
			if (variant == ExplorerRowVariant.Browse) Browse else Search
	}
}

private val TypeIconSize = 30.dp
private val ActionIconSize = 30.dp

val RowKind.iconRes: Int
	get() = when (this) {
		RowKind.SUBJECT -> R.drawable.ic_subject
		RowKind.NOTE -> R.drawable.ic_note
		RowKind.CHAPTER -> R.drawable.ic_chapter
		RowKind.WORD -> R.drawable.ic_word
	}

@Preview(showBackground = true, backgroundColor = 0xFFFFFF, name = "Row variants")
@Composable
private fun ItemPreviews() {
	// the widths the real list would compute for these positions, per variant.
	val browseWidth = positionColumnWidth(8, ExplorerRowVariant.Browse)
	val searchWidth = positionColumnWidth(8, ExplorerRowVariant.Search)
	Column {
		Item(PreviewModels.subject(3, 64, "Mathematics"), {}, {}, descVisible = false, positionWidth = browseWidth)
		Item(PreviewModels.chapter(1, -1, "Integrals"), {}, {}, descVisible = false, positionWidth = browseWidth)
		Item(PreviewModels.chapter(2, 23, "Derivatives"), {}, {}, descVisible = false, positionWidth = browseWidth)
		Item(
			PreviewModels.word(4, 100, "das Haus"),
			{}, {}, descVisible = false,
			positionWidth = browseWidth,
			infoAction = { Image(painterResource(R.drawable.ic_desc_popup), null) },
		)
		Item(
			PreviewModels.word(5, 0, "die Schule\n школа").copy(selected = true),
			{}, {},
			descVisible = true,
			positionWidth = browseWidth,
		)
		Item(
			PreviewModels.chapter(6, -1, "Old results"),
			{}, {}, descVisible = false,
			variant = ExplorerRowVariant.Search,
			positionWidth = searchWidth,
		)
		Item(PreviewModels.reference(8, "see: Algebra"), {}, {}, descVisible = false, positionWidth = browseWidth)
	}
}

private object PreviewModels {
	private var id = 0

	// previews render [ItemUiModel] only and never reach the backing row,
	// so the payload stays at its default null

	fun subject(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, RowKind.SUBJECT)

	fun chapter(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, RowKind.CHAPTER)

	fun word(pos: Int, ratio: Int, name: String) =
		row(pos, ratio, name, RowKind.WORD).copy(description = "podstatné jméno")

	fun reference(pos: Int, name: String) =
		row(pos, -1, name, RowKind.WORD).copy(isReference = true)

	private fun row(pos: Int, ratio: Int, name: String, kind: RowKind) = ItemUiModel(
		key = "preview-${id++}", position = pos, name = name, description = "",
		kind = kind, ratio = ratio, isReference = false, selected = false,
	)
}
