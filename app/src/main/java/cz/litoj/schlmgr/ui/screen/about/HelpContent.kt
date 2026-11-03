package cz.litoj.schlmgr.ui.screen.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.DefaultTextColor

/**
 * One collapsible help section.
 * - when expanded it shows the [intro] paragraph, the [paths] rows, the [body] text and the custom intro.
 * - that runs in this order, where a 0 resource or an empty list hides its field.
 * - [children] follow.
 */
class HelpSection(
	@param:StringRes val title: Int,
	@param:StringRes val body: Int = 0,
	@param:StringRes val intro: Int = 0,
	val paths: List<Int> = emptyList(),
	@param:DrawableRes val icon: Int = 0,
	val iconTint: Color = Color.Unspecified,
	val customIntro: (@Composable () -> Unit)? = null,
	val children: List<HelpSection> = emptyList(),
)

internal val sections = listOf(
	HelpSection(
		R.string.help_create, intro = R.string.help_create_how, icon = R.drawable.ic_add_black,
		children = listOf(
			HelpSection(R.string.help_create_mch, paths = listOf(R.array.help_path_create_mch), icon = R.drawable.ic_subject),
			HelpSection(R.string.help_create_ch, paths = listOf(R.array.help_path_create_chapter), icon = R.drawable.ic_chapter),
			HelpSection(R.string.help_create_word, R.string.help_create_word_how, paths = listOf(R.array.help_path_create_word), icon = R.drawable.ic_word),
			HelpSection(R.string.help_create_note, R.string.help_create_note_how, paths = listOf(R.array.help_path_create_note), icon = R.drawable.ic_note),
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
			HelpSection(R.string.help_search_types, R.string.help_search_types_how, customIntro = {
				CodeBlock(stringResource(R.string.help_search_types_examples))
			}),
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
			HelpSection(R.string.help_extra_naming, R.string.help_extra_naming_how, customIntro = {
				CodeBlock(stringResource(R.string.help_extra_naming_examples))
			}),
			HelpSection(
				R.string.help_extra_words_import, R.string.help_extra_words_import_how,
				paths = listOf(R.array.help_path_import),
				children = listOf(
					HelpSection(
						R.string.help_import_format,
						customIntro = {
							CodeBlock(stringResource(R.string.help_import_format_how))
							CodeBlock(stringResource(R.string.help_import_example_text))
						},
					),
				),
			),
			HelpSection(
				R.string.help_extra_words_export, R.string.help_extra_words_export_how,
				paths = listOf(R.array.help_path_export),
			),
		),
	),
)

/** A block of literal text, set apart from the explanation around it. */
@Composable
internal fun CodeBlock(text: String) {
	Text(
		text,
		Modifier
			.padding(8.dp)
			.fillMaxWidth()
			.background(colorResource(R.color.colorSubBackground))
			.padding(8.dp),
		style = TextStyle(
			fontSize = 13.sp,
			fontFamily = FontFamily.Monospace,
			color = DefaultTextColor,
		),
	)
}

@Composable
internal fun RegexIntro() {
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
			// the syntax tokens align only in a monospace column
			style = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = DefaultTextColor),
		)
		Text(
			stringResource(description),
			Modifier.weight(1f),
			style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
		)
	}
}
