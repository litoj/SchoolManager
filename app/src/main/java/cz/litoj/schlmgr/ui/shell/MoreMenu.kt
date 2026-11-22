package cz.litoj.schlmgr.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.lightColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.IconSize
import cz.litoj.schlmgr.ui.component.reportUnavailable

/** One action as data: placement belongs to whoever shows it, the glyph travels with the row. */
data class MenuEntry(
	val id: Int,
	val label: Int,
	val icon: Int = 0,
	val children: List<MenuEntry> = emptyList(),
	val selectionAction: Boolean = false,
	@StringRes val blockedReason: Int = 0,
) {
	val enabled: Boolean get() = blockedReason == 0
}

/**
 * A button and the menu it opens, held in one box.
 * - a dropdown anchors to its parent layout node, so the menu must share the button's node.
 * - [button] takes the opener, since only the host knows when a press opens a menu.
 */
@Composable
fun MenuHost(
	entries: List<MenuEntry>,
	onPick: (Int) -> Unit,
	modifier: Modifier = Modifier,
	button: @Composable (open: () -> Unit) -> Unit,
) {
	var open by remember { mutableStateOf(false) }
	Box(modifier) {
		button { open = true }
		if (open) MoreMenu(entries, { open = false; onPick(it) }) { open = false }
	}
}

/**
 * The more-options dropdown: flat rows and one-level submenus, one visible level.
 * - a parent tap swaps the menu to its children.
 * - the back row returns to the top level, with a single state and no stacked popups.
 */
@Composable
private fun MoreMenu(entries: List<MenuEntry>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
	// the M2 dropdown reads MaterialTheme colours, but the app carries no theme.
	// so supply the two it reads, and the resources resolve the day/night variant
	MaterialTheme(
		colors = lightColors(
			surface = colorResource(R.color.colorMenuBackground),
			onSurface = colorResource(R.color.colorPrimaryFg),
		)
	) {
		var openSub by remember { mutableStateOf<MenuEntry?>(null) }
		DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
			val sub = openSub
			if (sub == null) {
				for (entry in entries) {
					if (entry.children.isEmpty()) {
						MenuLeaf(entry) { onDismiss(); onPick(entry.id) }
					} else {
						MenuParentRow(entry) { openSub = entry }
					}
				}
			} else {
				MenuBackRow(sub) { openSub = null }
				for (child in sub.children) MenuLeaf(child) {
					onDismiss()
					onPick(child.id)
				}
			}
		}
	}
}

@Composable
private fun MenuLeaf(entry: MenuEntry, onClick: () -> Unit) {
	DropdownMenuItem(
		onClick = { if (entry.enabled) onClick() else reportUnavailable(entry.blockedReason) },
		enabled = true,
	) {
		MenuIcon(entry)
		Text(
			stringResource(entry.label),
			color = entryColor(entry),
			modifier = Modifier.padding(start = entry.iconSpacing()),
		)
	}
}

@Composable
private fun MenuParentRow(entry: MenuEntry, onClick: () -> Unit) {
	DropdownMenuItem(
		onClick = {
			if (entry.enabled) onClick() else reportUnavailable(entry.blockedReason)
		},
	) {
		MenuIcon(entry)
		Text(
			stringResource(entry.label),
			color = entryColor(entry),
			modifier = Modifier.padding(start = entry.iconSpacing()),
		)
		Icon(
			Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = entryColor(entry),
			modifier = Modifier.padding(start = 12.dp),
		)
	}
}

/** The disabled alpha matches the muted text of Material. */
@Composable
private fun entryColor(entry: MenuEntry): Color =
	if (entry.enabled) LocalContentColor.current
	else LocalContentColor.current.copy(alpha = 0.38f)

/** The drawables declare their own sizes, so the row fixes one for every icon. */
@Composable
private fun MenuIcon(entry: MenuEntry) {
	if (entry.icon != 0) Icon(
		painterResource(entry.icon),
		contentDescription = null,
		tint = entryColor(entry),
		modifier = Modifier.size(IconSize),
	)
}

private fun MenuEntry.iconSpacing() = if (icon != 0) 12.dp else 0.dp

@Composable
private fun MenuBackRow(entry: MenuEntry, onClick: () -> Unit) {
	DropdownMenuItem(onClick = onClick) {
		Icon(
			Icons.AutoMirrored.Filled.KeyboardArrowLeft,
			contentDescription = null,
		)
		Text(
			stringResource(entry.label),
			modifier = Modifier.padding(start = 12.dp),
		)
	}
}
