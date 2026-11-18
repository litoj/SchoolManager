package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.data.db.ItemKind
import cz.litoj.schlmgr.ui.search.summary
import cz.litoj.schlmgr.ui.viewmodel.InfoBar

private val HeaderGreen @Composable get() = ExplorerColors.header

private val CrumbColor @Composable get() = ExplorerColors.onHeader

private val InfoColor @Composable get() = ExplorerColors.body

/**
 * The top chrome of the explorer: the breadcrumb strip and the info strip,
 * stacked. Stateless, both hosts drive it from their state.
 * @param statusLine a transient line that replaces the info strip, such as an armed move
 */
@Composable
fun ExplorerTopBar(
	breadcrumbs: List<String>,
	onBreadcrumbClick: (Int) -> Unit,
	info: InfoBar?,
	isInfoExpanded: Boolean,
	onInfoClick: () -> Unit,
	onParentClick: () -> Unit,
	modifier: Modifier = Modifier,
	statusLine: String? = null,
) {
	Column(modifier.fillMaxWidth()) {
		Breadcrumbs(breadcrumbs, onBreadcrumbClick, onParentClick)
		InfoStrip(info, isInfoExpanded, onInfoClick, statusLine)
		HairlineDivider()
	}
}

@Composable
private fun Breadcrumbs(breadcrumbs: List<String>, onClick: (Int) -> Unit, onParentClick: () -> Unit) {
	val scroll = rememberScrollState()
	val parentLabel = stringResource(R.string.crumb_parent)
	// keep the newest crumb visible.
	LaunchedEffect(breadcrumbs) { scroll.animateScrollTo(scroll.maxValue) }
	Row(
		Modifier.fillMaxWidth().height(25.dp).background(HeaderGreen),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
			CrumbChain(breadcrumbs, CrumbColor, onCrumbClick = onClick)
		}
		// one level up needs no crumb: the parent of the first one is the root itself
		if (breadcrumbs.isNotEmpty()) Box(
			Modifier
				.fillMaxHeight()
				.clickable(onClick = onParentClick)
				.padding(horizontal = 10.dp)
				.semantics(mergeDescendants = true) { contentDescription = parentLabel },
			contentAlignment = Alignment.Center,
		) {
			Text("↶", fontSize = 16.sp, color = CrumbColor, maxLines = 1)
		}
	}
}

/**
 * One crumb chain, shared by the breadcrumbs of the titlebar and the tap paths
 * of the picker and the help.
 * @param onCrumbClick a tap on a crumb, null renders a plain chain
 */
@Composable
fun CrumbChain(
	names: List<String>,
	crumbColor: Color,
	modifier: Modifier = Modifier,
	onCrumbClick: ((Int) -> Unit)? = null,
	fontSize: TextUnit = 17.sp,
) {
	Row(modifier, verticalAlignment = Alignment.CenterVertically) {
		names.forEachIndexed { index, label ->
			Text(
				label,
				fontSize = fontSize,
				color = crumbColor,
				maxLines = 1,
				modifier = Modifier
					.then(if (onCrumbClick != null) Modifier.clickable { onCrumbClick(index) } else Modifier)
					.padding(horizontal = 4.dp),
			)
			if (index < names.size - 1) {
				// the tint carries the palette, a fixed grey fill would read the same in both themes.
				// the dimmed chevron stays a separator instead of reading as text.
				Image(
					painterResource(R.drawable.ic_bread_crumbs),
					contentDescription = null,
					modifier = Modifier.fillMaxHeight(0.5f),
					colorFilter = ColorFilter.tint(crumbColor.copy(alpha = 0.75f)),
				)
			}
		}
	}
}

/** A chapter never holds translation rows, so these kinds cover a browsed list. */
private val KindPlurals = listOf(
	ItemKind.WORD to R.plurals.stat_words,
	ItemKind.CHAPTER to R.plurals.stat_chapters_count,
	ItemKind.NOTE to R.plurals.stat_notes,
)

@Composable
private fun kindCounts(kinds: Map<ItemKind, Int>): List<String> =
	KindPlurals.mapNotNull { (kind, plural) ->
		kinds[kind]?.let { pluralStringResource(plural, it, it) }
	}

/**
 * The info strip, which reads the laid-out line count so a wrapped description
 * expands too. Counting '\n' would stick it at the 2-line height, hiding the rest.
 * - a [status] replaces the info text, so an armed move owns the line.
 */
@Composable
private fun InfoStrip(
	info: InfoBar?,
	isExpanded: Boolean,
	onClick: () -> Unit,
	status: String?,
) {
	// the strip resolves the display text from the semantics, because the resource
	// formatting belongs here.
	val text = status ?: when (info) {
		null -> ""
		is InfoBar.Root -> pluralStringResource(R.plurals.stat_subjects, info.count, info.count)
		is InfoBar.Children -> {
			val suffix = if (info.description.isEmpty()) "" else "\n" + info.description
			// -1 marks an untested container.
			val rate = if (info.ratio < 0) stringResource(R.string.data_untested)
			else stringResource(R.string.data_rate, info.ratio)
			(kindCounts(info.kinds) + rate).joinToString(", ") + suffix
		}
		is InfoBar.Search -> {
			val summary = info.filter?.summary()
			when {
				!info.done -> if (summary == null) stringResource(R.string.data_search_running)
				else stringResource(R.string.data_search_filtered_running, summary)
				info.count == 0 -> if (summary == null) stringResource(R.string.data_search_none)
				else stringResource(R.string.data_search_filtered_none, summary)
				summary == null -> stringResource(R.string.data_search_results, info.count)
				else -> stringResource(R.string.data_search_filtered, summary, info.count)
			}
		}
		InfoBar.InvalidSearch -> stringResource(R.string.data_search_invalid)
		InfoBar.InvalidPattern -> stringResource(R.string.data_search_bad_pattern)
	}
	val invalid = info == InfoBar.InvalidSearch || info == InfoBar.InvalidPattern
	// the laid-out line count of the current text. The wrapper Box always gives
	// the text its full width, so the count is stable whatever the strip height.
	var lines by remember { mutableIntStateOf(1) }
	// a status is a single line and never expands
	val expandable = status == null && lines > 2
	val scroll = rememberScrollState()
	// keep the top visible
	LaunchedEffect(text) { scroll.scrollTo(0) }
	Box(
		Modifier
			.fillMaxWidth()
			.then(
				when {
					status != null -> Modifier.height(18.dp)
					expandable && isExpanded -> Modifier.heightIn(min = 18.dp)
					else -> Modifier.height(if (lines == 1) 18.dp else 36.dp)
				},
			)
			.verticalScroll(scroll)
			.clickable(enabled = expandable, onClick = onClick)
			.padding(horizontal = 3.dp),
	) {
		Text(
			text,
			fontSize = 15.sp,
			color = if (invalid) ExplorerColors.failure else InfoColor,
			onTextLayout = { lines = it.lineCount },
		)
	}
}
