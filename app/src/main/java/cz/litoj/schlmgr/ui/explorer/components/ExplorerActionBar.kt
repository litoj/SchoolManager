package cz.litoj.schlmgr.ui.explorer.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

/** The action strip backdrop — the legacy `#8000` overlay (a grey wash, not green). */
private val BarColor @Composable get() = ExplorerColors.actionBar

/** Enabled label / disabled label (`#6FFF`). The enabled one is the strip's own
 *  light colour, not [ExplorerColors.onHeader]: this strip's backdrop is the grey
 *  wash above, which stays dark in the night palette. */
private val TextEnabled @Composable get() = ExplorerColors.actionText
private val TextDisabled @Composable get() = ExplorerColors.actionTextDisabled

/**
 * One bottom-strip action: an icon over a label, mirroring the legacy `drawableTop`
 * `TextView` buttons. [enabled] swaps both the label color and the icon's disabled
 * variant and gates the click — exactly the legacy `setEnabled`.
 */
private class Action(
	@field:DrawableRes @param:DrawableRes val iconEnabled: Int,
	@field:DrawableRes @param:DrawableRes val iconDisabled: Int,
	@field:StringRes @param:StringRes val label: Int,
	val onClick: () -> Unit,
)

/**
 * The legacy `objects_select` strip (browse + picker share its 60dp `#8000` look):
 * test / cut / edit / delete.
 */
@Composable
fun SelectionActionBar(
	cutEnabled: Boolean,
	editEnabled: Boolean,
	deleteEnabled: Boolean,
	onTest: () -> Unit,
	onCut: () -> Unit,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_test, R.drawable.ic_test, R.string.select_test, onTest),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_cut, R.drawable.ic_cut_disabled, R.string.cut, onCut),
			cutEnabled,
		)
		ActionButton(
			Action(R.drawable.ic_edit, R.drawable.ic_edit_disabled, R.string.edit, onEdit),
			editEnabled,
		)
		ActionButton(
			Action(R.drawable.ic_delete, R.drawable.ic_delete_disabled, R.string.delete, onDelete),
			deleteEnabled,
		)
	}
}

/** The legacy `objects_paster` strip: cancel / paste. */
@Composable
fun PasteActionBar(
	pasteEnabled: Boolean,
	onCancel: () -> Unit,
	onPaste: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_cancel, R.drawable.ic_cancel, R.string.objects_cancel, onCancel),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_paste, R.drawable.ic_paste_disabled, R.string.paste, onPaste),
			pasteEnabled,
		)
	}
}

/** The legacy picker strip (`activity_select_item`): cancel / select-all / select. */
@Composable
fun PickerActionBar(
	selectEnabled: Boolean,
	onCancel: () -> Unit,
	onSelectAll: () -> Unit,
	onSelect: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_cancel, R.drawable.ic_cancel, R.string.objects_cancel, onCancel),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_check_all, R.drawable.ic_check_all, R.string.select_all, onSelectAll),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_check, R.drawable.ic_check_disabled, R.string.select, onSelect),
			selectEnabled,
		)
	}
}

/** The shared 60dp `#8000` row container. */
@Composable
private fun ActionStrip(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier
			.fillMaxWidth()
			.height(60.dp)
			.background(BarColor),
		content = content,
	)
}

/**
 * One icon-over-label button (a weighted legacy `TextView` with `drawableTop`). When
 * [enabled] is `false` the label dims to `#6FFF` and the click is disabled.
 */
@Composable
private fun RowScope.ActionButton(action: Action, enabled: Boolean) {
	Column(
		Modifier
			.weight(1f)
			.fillMaxHeight()
			.clickable(enabled = enabled, onClick = action.onClick),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Image(
			painterResource(if (enabled) action.iconEnabled else action.iconDisabled),
			contentDescription = null,
		)
		Spacer(Modifier.height(2.dp))
		Text(
			stringResource(action.label),
			fontSize = 12.sp,
			color = if (enabled) TextEnabled else TextDisabled,
			maxLines = 1,
		)
	}
}
