package cz.litoj.schlmgr.ui.screen.about

import androidx.activity.compose.BackHandler
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.CrumbChain
import cz.litoj.schlmgr.ui.component.BigHeader
import cz.litoj.schlmgr.ui.component.sectionStyle
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.component.TopBarSearchField
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarIconButton
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

/**
 * The About screen: the top bar with the app name and version above the help tree.
 *
 * @param openDrawer the hamburger press, since the shell owns the drawer
 */
@Composable
fun AboutScreen(viewModel: AboutViewModel, openDrawer: () -> Unit) {
	val context = LocalContext.current
	LaunchedEffect(viewModel) { viewModel.loadVersion(context) }
	// the search owns the back press, so the screen survives a cancel
	BackHandler(viewModel.searchOpen) { viewModel.closeSearchField() }
	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(
				up = viewModel.searchOpen,
				onNavigate = if (viewModel.searchOpen) viewModel::closeSearchField else openDrawer,
				contentDescription = stringResource(
					if (viewModel.searchOpen) R.string.back else R.string.menu_label,
				),
			)
			if (viewModel.searchOpen) {
				TopBarSearchField(
					viewModel.query,
					viewModel::onQueryChange,
					// the tree filters live, so the submit only drops the keyboard
					{},
					viewModel::dismissSearch,
					Modifier.weight(1f),
					hint = stringResource(R.string.help_search_hint),
				)
			} else {
				TopBarTitle(stringResource(R.string.app_name) + (viewModel.version?.let { " v$it" } ?: ""))
			}
			TopBarIconButton(
				icon = R.drawable.ic_search,
				contentDescription = stringResource(R.string.help_search_hint),
				visible = !viewModel.searchOpen,
				onClick = viewModel::openSearchField,
			)
		}
		HelpTree(viewModel, Modifier.weight(1f).headerShadow())
	}
}

@Composable
private fun HelpTree(viewModel: AboutViewModel, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val searching = viewModel.query.isNotBlank()
	// stringArrayResource stays configuration aware, unlike a raw Resources read
	val pathText = HelpFilter.pathIds(sections).associateWith { stringArrayResource(it).toList() }
	val visible = if (searching) {
		HelpFilter.visible(sections, viewModel.query, context::getString, pathText::getValue)
	} else emptySet()
	LazyColumn(modifier.fillMaxSize()) {
		item { BigHeader(stringResource(R.string.help), R.drawable.ic_help) }
		sections.filter { !searching || it.title in visible }.forEach { section ->
			item(key = section.title) { Section(section, viewModel, searching, visible) }
		}
		if (searching && visible.isEmpty()) item { BodyText(stringResource(R.string.help_no_match)) }
		if (!searching) {
			item { BigHeader(stringResource(R.string.menu_about), R.drawable.ic_app_mini) }
			item {
				Text(
					stringResource(R.string.about_text),
					Modifier.padding(8.dp),
					style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
				)
			}
		}
	}
}

/**
 * A single expandable section: a clickable header row, followed by its content.
 * - the visuals depend on its [level].
 * - every level toggles through [AboutViewModel], so the state survives recomposition and rotation.
 * - while [searching] runs, every visible section stands open to reveal its matches.
 */
@Composable
private fun Section(
	section: HelpSection,
	viewModel: AboutViewModel,
	searching: Boolean,
	visible: Set<Int>,
	level: Int = 0,
) {
	val style = sectionStyle(level)
	val expanded = searching || section.title in viewModel.expanded
	Row(
		Modifier
			.fillMaxWidth()
			.background(style.background)
			.clickable { viewModel.toggle(section.title) }
			.padding(start = 15.dp + style.indent, end = 15.dp, top = style.vertical, bottom = style.vertical),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			stringResource(section.title),
			Modifier.weight(1f),
			style = TextStyle(fontSize = style.fontSize, color = DefaultTextColor),
		)
		if (section.icon != 0) {
			// unspecified follows the theme foreground, like the text does.
			// a specified tint is used as given
			Icon(
				painterResource(section.icon),
				null,
				tint = if (section.iconTint == Color.Unspecified) DefaultTextColor else section.iconTint,
			)
		}
	}
	if (expanded) {
		if (section.intro != 0) BodyText(stringResource(section.intro))
		section.paths.forEach { PathRow(it) }
		if (section.body != 0) BodyText(stringResource(section.body))
		section.customIntro?.invoke()
		section.children
			.filter { !searching || it.title in visible }
			.forEach { Section(it, viewModel, searching, visible, level + 1) }
	}
}

@Composable
internal fun BodyText(text: String) {
	Text(
		text,
		Modifier.padding(8.dp),
		style = TextStyle(fontSize = 15.sp, color = DefaultTextColor),
	)
}

/** One interaction path, rendered like the object path of the explorer. */
@Composable
private fun PathRow(@ArrayRes path: Int) {
	CrumbChain(
		stringArrayResource(path).toList(),
		DefaultTextColor,
		Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
		fontSize = 15.sp,
	)
}
