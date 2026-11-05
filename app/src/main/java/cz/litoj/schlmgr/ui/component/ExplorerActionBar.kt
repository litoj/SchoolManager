package cz.litoj.schlmgr.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.shell.MenuEntry
import cz.litoj.schlmgr.ui.shell.MoreMenu
import cz.litoj.schlmgr.ui.viewmodel.SelectionAction
import cz.litoj.schlmgr.ui.viewmodel.icon
import cz.litoj.schlmgr.ui.viewmodel.label

// the strip wears the primary header treatment, so its items read as actions at once
private val BarColor @Composable get() = ExplorerColors.header

private val ForegroundEnabled @Composable get() = ExplorerColors.onHeader
private val ForegroundDisabled @Composable get() = ExplorerColors.onHeader.copy(alpha = 0.5f)

private class Action(
	@field:DrawableRes @param:DrawableRes val icon: Int,
	@field:StringRes @param:StringRes val label: Int,
	val onClick: () -> Unit,
)

/**
 * The bottom-strip selection actions of one list kind.
 * - the slots are the first four actions that apply to the list kind.
 * - edit and go-to act on one row, so a wider selection mutes them with a reason.
 * - export opens its word and JSON chooser right on the strip.
 *
 * @param exportBlockedReason the reason of an empty writable selection, 0 when it can export
 */
@Composable
fun SelectionActionBar(
	actions: List<SelectionAction>,
	singleSelected: Boolean,
	onAction: (SelectionAction) -> Unit,
	exportEntries: List<MenuEntry>,
	onExport: (Int) -> Unit,
	modifier: Modifier = Modifier,
	exportBlockedReason: Int = 0,
) {
	ActionStrip(modifier) {
		for (action in actions) {
			val blockedReason = when (action) {
				SelectionAction.EDIT, SelectionAction.GOTO ->
					if (singleSelected) 0 else R.string.unavailable_single_row
				SelectionAction.EXPORT -> exportBlockedReason
				else -> 0
			}
			if (action == SelectionAction.EXPORT)
				ExportStripButton(action, blockedReason, exportEntries, onExport)
			else ActionButton(
				Action(action.icon, action.label) { onAction(action) },
				blockedReason,
				Modifier.weight(1f),
			)
		}
	}
}

/** The export slot, which hosts its format chooser as a dropdown. */
@Composable
private fun RowScope.ExportStripButton(
	action: SelectionAction,
	blockedReason: Int,
	entries: List<MenuEntry>,
	onExport: (Int) -> Unit,
) {
	var open by remember { mutableStateOf(false) }
	// the box holds the strip share of the slot, since the button loses the row scope
	Box(Modifier.weight(1f).fillMaxHeight()) {
		ActionButton(Action(action.icon, action.label) { open = true }, blockedReason)
		if (open) MoreMenu(entries, { open = false; onExport(it) }) { open = false }
	}
}

/**
 * The bottom-strip paste actions of an armed move.
 * - the armed status reads on the top strip, so the bar carries only the two actions.
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
			0,
			Modifier.weight(1f),
		)
		ActionButton(
			Action(R.drawable.ic_paste, R.string.paste, onPaste),
			0,
			Modifier.weight(1f),
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
			0,
			Modifier.weight(1f),
		)
		ActionButton(
			Action(R.drawable.ic_check, R.string.select, onSelect),
			0,
			Modifier.weight(1f),
		)
	}
}

/** The import mover strip: always visible, with the import and done states. */
@Composable
fun ImportActionBar(
	busy: Boolean,
	imported: Boolean,
	onCancel: () -> Unit,
	onImport: () -> Unit,
	onDone: () -> Unit,
	modifier: Modifier = Modifier,
) {
	ActionStrip(modifier) {
		if (imported) {
			ActionButton(
				Action(R.drawable.ic_check, R.string.done, onDone),
				0,
				Modifier.weight(1f),
			)
		} else {
			// a busy mover keeps both buttons in place, so cancel and import explain their wait
			val blockedReason = if (busy) R.string.unavailable_import_busy else 0
			ActionButton(
				Action(R.drawable.ic_cancel, R.string.objects_cancel, onCancel),
				blockedReason,
				Modifier.weight(1f),
			)
			ActionButton(
				Action(R.drawable.ic_import_black, R.string.import_here, onImport),
				blockedReason,
				Modifier.weight(1f),
			)
		}
	}
}

/** The height of the action strips, which the screens pad around. */
internal val ActionStripHeight = 60.dp

@Composable
private fun ActionStrip(
	modifier: Modifier = Modifier,
	content: @Composable RowScope.() -> Unit,
) {
	Row(
		modifier
			.fillMaxWidth()
			.height(ActionStripHeight)
			.background(BarColor),
		content = content,
	)
}

/** One icon-over-label button. A non-zero blocked reason mutes it and explains the tap. */
@Composable
private fun ActionButton(
	action: Action,
	blockedReason: Int,
	modifier: Modifier = Modifier,
) {
	val enabled = blockedReason == 0
	Column(
		modifier
			.fillMaxHeight()
			.clickable { if (enabled) action.onClick() else reportUnavailable(blockedReason) },
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
