package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.viewmodel.InfoBar

private val HeaderGreen @Composable get() = ExplorerColors.header

private val CrumbColor @Composable get() = ExplorerColors.onHeader

private val InfoColor @Composable get() = ExplorerColors.body

/**
 * The top chrome of the explorer: the breadcrumb strip and the info strip,
 * stacked. Stateless, both hosts drive it from their state.
 */
@Composable
fun ExplorerTopBar(
	breadcrumbs: List<String>,
	onBreadcrumbClick: (Int) -> Unit,
	info: InfoBar?,
	isInfoExpanded: Boolean,
	onInfoClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier.fillMaxWidth()) {
		Breadcrumbs(breadcrumbs, onBreadcrumbClick)
		InfoStrip(info, isInfoExpanded, onInfoClick)
		HairlineDivider()
	}
}

@Composable
private fun Breadcrumbs(breadcrumbs: List<String>, onClick: (Int) -> Unit) {
	val scroll = rememberScrollState()
	// keep the newest crumb visible.
	// - keyed on the list, not its size.
	// - a same-length change such as a crumb rename still scrolls the longer crumb into view.
	LaunchedEffect(breadcrumbs) { scroll.animateScrollTo(scroll.maxValue) }
	Row(
		Modifier
			.fillMaxWidth()
			.height(25.dp)
			.background(HeaderGreen)
			.horizontalScroll(scroll),
		verticalAlignment = Alignment.CenterVertically,
	) {
		CrumbChain(breadcrumbs, CrumbColor, onCrumbClick = onClick)
	}
}

/**
 * One crumb chain, shared by the breadcrumbs of the titlebar and the paths of
 * the source picker.
 * @param onCrumbClick a tap on a crumb, null renders a plain chain
 */
@Composable
fun CrumbChain(
	names: List<String>,
	crumbColor: Color,
	modifier: Modifier = Modifier,
	onCrumbClick: ((Int) -> Unit)? = null,
) {
	Row(modifier, verticalAlignment = Alignment.CenterVertically) {
		names.forEachIndexed { index, label ->
			Text(
				label,
				fontSize = 17.sp,
				color = crumbColor,
				maxLines = 1,
				modifier = Modifier
					.then(if (onCrumbClick != null) Modifier.clickable { onCrumbClick(index) } else Modifier)
					.padding(horizontal = 4.dp),
			)
			if (index < names.size - 1) {
				// scaled content stays centered. The tint carries the palette, because
				// the fixed grey fill of the drawable would read the same in both.
				Image(
					painterResource(R.drawable.ic_bread_crumbs),
					contentDescription = null,
					colorFilter = ColorFilter.tint(crumbColor),
				)
			}
		}
	}
}

/**
 * The info strip, which reads the laid-out line count so a wrapped description
 * expands too. Counting '\n' would stick it at the 2-line height, hiding the rest.
 */
@Composable
private fun InfoStrip(
	info: InfoBar?,
	isExpanded: Boolean,
	onClick: () -> Unit,
) {
	// the strip resolves the display text from the semantics, because the resource
	// formatting belongs here, with the description line riding the percent sign
	val text = when (info) {
		null -> ""
		is InfoBar.Root -> stringResource(R.string.data_child_count, info.count)
		is InfoBar.Children -> stringResource(
			R.string.data_children_sf, info.count, info.ratio,
			if (info.description.isEmpty()) "%" else "%\n" + info.description,
		)
		is InfoBar.Search -> stringResource(R.string.data_search_chapters, info.count, info.chapters)
	}
	// the laid-out line count of the current text. The wrapper Box always gives
	// the text its full width, so the count is stable whatever the strip height.
	var lines by remember { mutableIntStateOf(1) }
	val expandable = lines > 2
	val scroll = rememberScrollState()
	// keep the top visible
	LaunchedEffect(text) { scroll.scrollTo(0) }
	Box(
		Modifier
			.fillMaxWidth()
			.then(
				if (expandable && isExpanded) Modifier.heightIn(min = 18.dp)
				else Modifier.height(if (lines == 1) 18.dp else 36.dp),
			)
			.verticalScroll(scroll)
			.clickable(enabled = expandable, onClick = onClick)
			.padding(horizontal = 3.dp),
	) {
		Text(
			text,
			fontSize = 15.sp,
			color = InfoColor,
			onTextLayout = { lines = it.lineCount },
		)
	}
}
