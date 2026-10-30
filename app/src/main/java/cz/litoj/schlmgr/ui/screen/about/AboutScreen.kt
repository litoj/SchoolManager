package cz.litoj.schlmgr.ui.screen.about

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.shell.TopBar
import cz.litoj.schlmgr.ui.shell.TopBarNav
import cz.litoj.schlmgr.ui.shell.TopBarTitle
import cz.litoj.schlmgr.ui.shell.headerShadow

private val GroupBackground @Composable get() = colorResource(R.color.colorGroupBackground)
private val SubBackground @Composable get() = colorResource(R.color.colorSubBackground)

private val sections = listOf(
	HelpSection(
		R.string.help_create, intro = R.string.help_create_how, icon = R.drawable.ic_add_black,
		children = listOf(
			HelpSection(R.string.help_create_mch, R.string.help_create_mch_how, icon = R.drawable.ic_subject),
			HelpSection(R.string.help_create_ch, R.string.help_create_ch_how, icon = R.drawable.ic_chapter),
			HelpSection(R.string.help_create_word, R.string.help_create_word_how, icon = R.drawable.ic_word),
			HelpSection(R.string.help_create_note, R.string.help_create_note_how, icon = R.drawable.ic_note),
		),
	),
	HelpSection(
		R.string.help_select, intro = R.string.help_select_how, icon = R.drawable.ic_select_black,
		children = listOf(
			HelpSection(R.string.delete, R.string.help_select_delete, icon = R.drawable.ic_delete),
			HelpSection(R.string.cut, R.string.help_select_move, icon = R.drawable.ic_cut),
			HelpSection(R.string.edit, R.string.help_select_edit, icon = R.drawable.ic_edit),
		),
	),
	HelpSection(
		R.string.help_search, intro = R.string.help_search_how, icon = R.drawable.ic_search,
		children = listOf(
			HelpSection(R.string.help_search_types, R.string.help_search_types_how),
			HelpSection(R.string.help_search_regex, customIntro = { RegexIntro() }),
		),
	),
	HelpSection(
		R.string.help_test, intro = R.string.help_test_how, icon = R.drawable.ic_test_black,
		children = listOf(
			HelpSection(R.string.help_test_select, R.string.help_test_select_how),
			HelpSection(R.string.help_test_run, R.string.help_test_run_how),
			HelpSection(R.string.help_test_results, R.string.help_test_results_how),
		),
	),
	HelpSection(
		R.string.help_extra, intro = R.string.help_extra_how, icon = R.drawable.ic_extra,
		children = listOf(
			HelpSection(R.string.help_extra_naming, R.string.help_extra_naming_how),
			HelpSection(
				R.string.help_extra_words_import, R.string.help_extra_words_import_how,
				children = listOf(
					HelpSection(R.string.help_import_format, R.string.help_import_format_how),
					HelpSection(R.string.help_import_example, customIntro = { WordListExample() }),
					HelpSection(R.string.help_import_errors, R.string.help_import_errors_how),
				),
			),
			HelpSection(
				R.string.help_extra_words_export, R.string.help_extra_words_export_how,
				children = listOf(
					HelpSection(R.string.help_export_word, R.string.help_export_word_how),
					HelpSection(R.string.help_export_json, R.string.help_export_json_how),
				),
			),
			HelpSection(R.string.help_extra_import_mch, R.string.help_extra_import_mch_how, icon = R.drawable.ic_import_help),
		),
	),
)

/**
 * The About screen: the top bar with the app name and version above the help tree.
 *
 * @param openDrawer the hamburger press, since the shell owns the drawer
 */
@Composable
fun AboutScreen(viewModel: AboutViewModel, openDrawer: () -> Unit) {
	val context = LocalContext.current
	LaunchedEffect(viewModel) { viewModel.loadVersion(context) }
	Column(Modifier.fillMaxSize()) {
		TopBar {
			TopBarNav(onNavigate = openDrawer)
			TopBarTitle(stringResource(R.string.app_name) + (viewModel.version?.let { " v$it" } ?: ""))
		}
		HelpTree(viewModel, Modifier.weight(1f).headerShadow())
	}
}

/**
 * One collapsible help section.
 * - when expanded it shows the [intro] paragraph, the [body] text and the custom intro.
 * - that runs in this order, where a 0 resource or null hides its field.
 * - [children] follow.
 */
class HelpSection(
	@param:StringRes val title: Int,
	@param:StringRes val body: Int = 0,
	@param:StringRes val intro: Int = 0,
	@param:DrawableRes val icon: Int = 0,
	val iconTint: Color = Color.Unspecified,
	val customIntro: (@Composable () -> Unit)? = null,
	val children: List<HelpSection> = emptyList(),
)

@Composable
private fun HelpTree(viewModel: AboutViewModel, modifier: Modifier = Modifier) {
	LazyColumn(modifier.fillMaxSize()) {
		item { BigHeader(stringResource(R.string.help), R.drawable.ic_help) }
		sections.forEach { section -> item(key = section.title) { Section(section, viewModel) } }
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

@Composable
private fun BigHeader(text: String, @DrawableRes icon: Int) {
	Row(
		Modifier
			.fillMaxWidth()
			.background(colorResource(R.color.colorPrimaryHeader))
			.padding(horizontal = 15.dp, vertical = 3.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			text,
			Modifier.weight(1f),
			// the on-header colour keeps the green header chrome readable.
			// it also works when the header turns bright and its text dark.
			style = TextStyle(fontSize = 25.sp, color = colorResource(R.color.colorOnHeader)),
		)
		Icon(painterResource(icon), null, tint = Color.Unspecified)
	}
}

/**
 * A single expandable section: a clickable header row, followed by its content.
 * - the visuals depend on its [level].
 * - both levels toggle through [AboutViewModel], so the state survives recomposition and rotation.
 */
@Composable
private fun Section(section: HelpSection, viewModel: AboutViewModel, level: Int = 0) {
	val expanded = section.title in viewModel.expanded
	Row(
		Modifier
			.fillMaxWidth()
			.background(if (level == 0) GroupBackground else SubBackground)
			.clickable { viewModel.toggle(section.title) }
			.padding(horizontal = 15.dp, vertical = if (level == 0) 7.dp else 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(
			stringResource(section.title),
			Modifier.weight(1f),
			style = TextStyle(fontSize = if (level == 0) 19.sp else 17.sp, color = DefaultTextColor),
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
		if (section.body != 0) BodyText(stringResource(section.body))
		section.customIntro?.invoke()
		section.children.forEach { Section(it, viewModel, level + 1) }
	}
}

@Composable
private fun BodyText(text: String) {
	Text(
		text,
		Modifier.padding(8.dp),
		style = TextStyle(fontSize = 15.sp, color = DefaultTextColor),
	)
}

@Composable
private fun WordListExample() {
	Text(
		stringResource(R.string.help_import_example_text),
		Modifier.padding(8.dp),
		style = TextStyle(
			fontSize = 13.sp,
			fontFamily = FontFamily.Monospace,
			color = DefaultTextColor,
		),
	)
}

@Composable
private fun RegexIntro() {
	BodyText(stringResource(R.string.help_search_regex_how_1))
	RegexTable(R.string.help_search_regex_char_class, R.string.help_search_regex_char_class_desc, 100.dp)
	BodyText(stringResource(R.string.help_search_regex_how_2))
	RegexTable(R.string.help_search_regex_quant, R.string.help_search_regex_quant_desc, 70.dp)
	BodyText(stringResource(R.string.help_search_regex_how_3))
	RegexTable(R.string.help_search_regex_meta, R.string.help_search_regex_meta_desc, 70.dp, 8.dp)
}

@Composable
private fun RegexTable(@StringRes column: Int, @StringRes description: Int, width: Dp, bottom: Dp = 0.dp) {
	Row(
		Modifier
			.padding(start = 8.dp, end = 8.dp, bottom = bottom),
	) {
		Text(
			stringResource(column),
			Modifier.width(width),
			style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
		)
		Text(
			stringResource(description),
			Modifier.weight(1f),
			style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
		)
	}
}
