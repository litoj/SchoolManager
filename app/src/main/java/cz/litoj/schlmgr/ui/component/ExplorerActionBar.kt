package cz.litoj.schlmgr.ui.component

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
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

private val BarColor @Composable get() = ExplorerColors.actionBar

private val ForegroundEnabled @Composable get() = ExplorerColors.actionText
private val ForegroundDisabled @Composable get() = ExplorerColors.actionTextDisabled

private class Action(
	@field:DrawableRes @param:DrawableRes val icon: Int,
	@field:StringRes @param:StringRes val label: Int,
	val onClick: () -> Unit,
)

/**
 * The bottom-strip selection actions: test, cut, edit, delete.
 * - the search strip swaps cut for go-to.
 * - the strip renders only while rows are selected, so test, cut and delete stay enabled.
 * - edit follows [editEnabled], the single-target rule.
 * - the cut of a hit is ambiguous, since the same word can live in several containers.
 * - so the search strip shows a go-to instead, disabled without the single target.
 */
@Composable
fun SelectionActionBar(
	editEnabled: Boolean,
	onTest: () -> Unit,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	modifier: Modifier = Modifier,
	onCut: (() -> Unit)? = null,
	onGoTo: (() -> Unit)? = null,
	goToEnabled: Boolean = true,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_test, R.string.select_test, onTest),
			true,
		)
		if (onGoTo != null) ActionButton(
			Action(R.drawable.ic_goto, R.string.more_go_to, onGoTo),
			goToEnabled,
		)
		if (onCut != null) ActionButton(
			Action(R.drawable.ic_cut, R.string.cut, onCut),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_edit, R.string.edit, onEdit),
			editEnabled,
		)
		ActionButton(
			Action(R.drawable.ic_delete, R.string.delete, onDelete),
			true,
		)
	}
}

/**
 * The bottom-strip paste actions: cancel and paste. Both stay enabled, because
 * arming the paste clears the selection first, so no state can disable them.
 */
@Composable
fun PasteActionBar(
	onCancel: () -> Unit,
	onPaste: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_cancel, R.string.objects_cancel, onCancel),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_paste, R.string.paste, onPaste),
			true,
		)
	}
}

/** The picker strip: cancel and select, shown only while rows are selected. */
@Composable
fun PickerActionBar(
	onCancel: () -> Unit,
	onSelect: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		ActionButton(
			Action(R.drawable.ic_cancel, R.string.objects_cancel, onCancel),
			true,
		)
		ActionButton(
			Action(R.drawable.ic_check, R.string.select, onSelect),
			true,
		)
	}
}

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

/** One icon-over-label button. A false enabled dims the icon and label, and disables the click. */
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
		// Icons render at the Material standard. No drawable size can change the bar.
		Image(
			painterResource(action.icon),
			contentDescription = null,
			modifier = Modifier.size(IconSize),
			colorFilter = ColorFilter.tint(if (enabled) ForegroundEnabled else ForegroundDisabled),
		)
		Spacer(Modifier.height(2.dp))
		Text(
			stringResource(action.label),
			fontSize = 12.sp,
			color = if (enabled) ForegroundEnabled else ForegroundDisabled,
			maxLines = 1,
		)
	}
}
