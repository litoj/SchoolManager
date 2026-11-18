package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.search.Criterion
import cz.litoj.schlmgr.ui.search.SearchTag
import cz.litoj.schlmgr.ui.search.summary

/**
 * The filters a search runs with, one chip each.
 * - a chip names its filter in the app language, so the user reads no written syntax.
 * - the label opens the editor of that filter, the mark drops it.
 */
@Composable
fun FilterChips(
	criteria: List<Criterion>,
	onEdit: (Criterion) -> Unit,
	onDrop: (SearchTag) -> Unit,
	modifier: Modifier = Modifier,
) {
	val onHeader = ExplorerColors.onHeader
	val scroll = rememberScrollState()
	// the newest filter is the one the user just added
	LaunchedEffect(criteria.size) { scroll.animateScrollTo(scroll.maxValue) }
	Row(
		modifier
			.fillMaxWidth()
			.background(ExplorerColors.header)
			.horizontalScroll(scroll)
			.padding(horizontal = 4.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		criteria.forEach { criterion ->
			FilterChip(criterion, onEdit, onDrop, onHeader)
		}
	}
}

/** One chip height, so the mark owns the whole text line. */
private val ChipHeight = 30.dp

/** The glyph of the mark, the rest of its slot is tap area. */
private val MarkSize = 18.dp

@Composable
private fun FilterChip(
	criterion: Criterion,
	onEdit: (Criterion) -> Unit,
	onDrop: (SearchTag) -> Unit,
	onHeader: Color,
) {
	Row(
		Modifier
			.padding(end = 2.dp)
			.height(ChipHeight)
			.clip(RoundedCornerShape(2.dp))
			.background(onHeader.copy(alpha = 0.18f)),
		verticalAlignment = Alignment.CenterVertically,
	) {
		// the two targets stay apart, so one tap never reads as the other
		Text(
			criterion.summary(),
			fontSize = 14.sp,
			color = onHeader,
			maxLines = 1,
			modifier = Modifier
				.padding(horizontal = 2.dp)
				.clickable { onEdit(criterion) },
		)
		Box(
			Modifier
				.size(ChipHeight)
				.clickable { onDrop(criterion.tag) },
			contentAlignment = Alignment.Center,
		) {
			Image(
				painterResource(R.drawable.ic_cancel_round),
				contentDescription = stringResource(R.string.filter_chip_drop),
				colorFilter = ColorFilter.tint(onHeader),
				modifier = Modifier.size(MarkSize),
			)
		}
	}
}
