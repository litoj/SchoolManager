package cz.litoj.schlmgr.ui.explorer.components

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

/** The breadcrumb strip's theme green header (`colorPrimaryHeader`) via [ExplorerColors]. */
private val HeaderGreen @Composable get() = ExplorerColors.header

/** The breadcrumb label color (legacy `btn.setTextColor(0xFFFFFFFF)`). */
private val CrumbColor @Composable get() = ExplorerColors.onHeader

/** The info-strip body color — matches the app's default body text. */
private val InfoColor @Composable get() = ExplorerColors.body

/**
 * The explorer's top chrome = the legacy `explorer.xml` header block: the breadcrumb
 * strip and the info strip, stacked vertically. Fully stateless — every value comes
 * from the parameters and every action is a callback, so both hosts (browse + picker)
 * drive it from their `ExplorerScreenState`.
 *
 * @param breadcrumbs one label per `BackLog.path` entry (empty at the subjects root).
 * @param onBreadcrumbClick invoked with a tapped crumb's index (legacy `addPathButton`).
 * @param infoText the info-strip text (`data_children_sf` / `data_child_count` / `data_search_time`).
 * @param isInfoExpanded whether the (expandable) strip currently shows its full height.
 * @param onInfoClick toggles expansion (legacy info `OnClickListener`). Ignored unless expandable.
 */
@Composable
fun ExplorerTopBar(
	breadcrumbs: List<String>,
	onBreadcrumbClick: (Int) -> Unit,
	infoText: String,
	isInfoExpanded: Boolean,
	onInfoClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier.fillMaxWidth()) {
		Breadcrumbs(breadcrumbs, onBreadcrumbClick)
		InfoStrip(infoText, isInfoExpanded, onInfoClick)
		// the 0.6dp #888 divider that separated the info strip from the list
		Box(Modifier.fillMaxWidth().height(0.6.dp).background(ExplorerColors.stripDivider))
	}
}

/**
 * The legacy `explorer_path_handler` HorizontalScrollView + `explorer_path` row:
 * white 17sp buttons separated by `ic_bread_crumbs` chevrons, auto-scrolled to the end.
 */
@Composable
private fun Breadcrumbs(breadcrumbs: List<String>, onClick: (Int) -> Unit) {
	val scroll = rememberScrollState()
	// legacy `hsv.post { hsv.fullScroll(FOCUS_RIGHT) }` — keep the newest crumb visible
	LaunchedEffect(breadcrumbs.size) { scroll.animateScrollTo(scroll.maxValue) }
	Row(
		Modifier
			.fillMaxWidth()
			.height(25.dp)
			.background(HeaderGreen)
			.horizontalScroll(scroll),
		verticalAlignment = Alignment.CenterVertically,
	) {
		breadcrumbs.forEachIndexed { index, label ->
			Text(
				label,
				fontSize = 17.sp,
				color = CrumbColor,
				maxLines = 1,
				modifier = Modifier
					.clickable { onClick(index) }
					.padding(horizontal = 4.dp),
			)
			if (index < breadcrumbs.size - 1) {
				// the legacy chevron view was scaled x2.4; scaled content stays centered here
				// the tint carries the palette: the drawable's own fill is a fixed grey,
				// which would read the same in both palettes
				Image(
					painterResource(R.drawable.ic_bread_crumbs),
					contentDescription = null,
					colorFilter = ColorFilter.tint(CrumbColor),
				)
			}
		}
	}
}

/**
 * The legacy `explorer_info_handler` ScrollView + `explorer_info` TextView: the
 * children/success-rate/description readout. Collapsed it is 18dp (1 line) or 36dp
 * (2 lines); when the text takes 3+ lines it becomes clickable and expands to
 * wrap-content.
 *
 * The line count is the laid-out one: the legacy `height(String)` counted '\n'
 * characters, so a long description — one newline but many wrapped lines — stayed
 * stuck at the collapsed 2-line height with its remainder unreadable off-screen.
 * The strip therefore reads the laid-out line count from its own text.
 */
@Composable
private fun InfoStrip(
	text: String,
	isExpanded: Boolean,
	onClick: () -> Unit,
) {
	// The laid-out line count of the current text; the wrapper Box always gives
	// the text its full width, so the count is stable whatever the strip height.
	var lines by remember { mutableStateOf(1) }
	val expandable = lines > 2
	val scroll = rememberScrollState()
	// legacy `infoScroll.post { infoScroll.fullScroll(FOCUS_UP) }` — keep the top visible
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

/**
 * The 40dp search strip of the legacy `explorer.xml` was removed: the one search
 * field of the app lives in the shell's app bar (MainActivity's toolbar), shared by
 * every explorer host, so the explorer chrome holds no search UI of its own.
 */
