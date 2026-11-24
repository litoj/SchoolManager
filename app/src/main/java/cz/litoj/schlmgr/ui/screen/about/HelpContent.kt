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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.DefaultTextColor
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction

/**
 * One collapsible help section.
 * - when expanded it shows the [intro] paragraph, the [paths] rows, the [body] text and the [examples] block.
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
	@param:StringRes val examples: Int = 0,
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
			SelectionAction.DELETE.action.let { HelpSection(it.label, R.string.help_select_delete, icon = it.icon) },
			SelectionAction.CUT.action.let { HelpSection(it.label, R.string.help_select_move, icon = it.icon) },
			SelectionAction.REFERENCE.action.let { HelpSection(it.label, it.description, icon = it.icon) },
			SelectionAction.EDIT.action.let { HelpSection(it.label, R.string.help_select_edit, icon = it.icon) },
			SelectionAction.FLIP.action.let { HelpSection(it.label, it.description, icon = it.icon) },
		),
	),
	HelpSection(
		R.string.help_search, intro = R.string.help_search_how, icon = R.drawable.ic_search,
		children = listOf(
			SelectionAction.GOTO.action.let { HelpSection(it.label, it.description, icon = it.icon) },
		),
	),
	HelpSection(
		R.string.help_test, intro = R.string.help_test_how, icon = R.drawable.ic_test,
		children = listOf(
			HelpSection(R.string.help_test_select, R.string.help_test_select_how),
			HelpSection(R.string.help_test_run, R.string.help_test_run_how),
		),
	),
	HelpSection(
		R.string.help_extra, intro = R.string.help_extra_how, icon = R.drawable.ic_extra,
		children = listOf(
			HelpSection(R.string.help_extra_rate, R.string.help_extra_rate_how),
			HelpSection(R.string.help_extra_naming, R.string.help_extra_naming_how,
				examples = R.string.help_extra_naming_examples),
			HelpSection(
				R.string.help_extra_words_import, R.string.help_extra_words_import_how,
				paths = listOf(R.array.help_path_import_new, R.array.help_path_import),
				icon = R.drawable.ic_download,
			),
			HelpSection(
				R.string.help_extra_words_export, R.string.help_extra_words_export_how,
				paths = listOf(R.array.help_path_export),
				icon = SelectionAction.EXPORT.action.icon,
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
