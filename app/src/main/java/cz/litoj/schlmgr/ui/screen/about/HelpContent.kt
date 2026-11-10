package cz.litoj.schlmgr.ui.screen.about

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
			HelpSection(R.string.action_reference, R.string.setts_act_reference, icon = R.drawable.ic_reference_black),
			HelpSection(R.string.edit, R.string.help_select_edit, icon = R.drawable.ic_edit),
			HelpSection(R.string.action_flip, R.string.setts_act_flip, icon = R.drawable.ic_flip_black),
		),
	),
	HelpSection(
		R.string.help_search, intro = R.string.help_search_how, icon = R.drawable.ic_search,
		children = listOf(
			HelpSection(R.string.more_go_to, R.string.setts_act_goto, icon = R.drawable.ic_goto),
			HelpSection(R.string.help_search_filter, R.string.help_search_filter_how),
			HelpSection(R.string.help_search_advanced, R.string.help_search_advanced_how, customIntro = {
				CodeBlock(stringResource(R.string.help_search_advanced_examples))
			}),
		),
	),
	HelpSection(
		R.string.help_test, intro = R.string.help_test_how, icon = R.drawable.ic_test,
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
				icon = R.drawable.ic_import_black,
			),
			HelpSection(
				R.string.help_extra_words_export, R.string.help_extra_words_export_how,
				paths = listOf(R.array.help_path_export),
				icon = R.drawable.ic_export_black,
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
