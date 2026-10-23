package cz.litoj.schlmgr.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
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

/**
 * @property icon the leading icon of the row, where 0 renders none
 * @property children a non-empty list opens a second menu level
 * @property selectionAction acts on selected rows, keeping the resource order
 * @property always stays in both menu forms, as the export rows do
 * @property enabled false keeps the place but mutes the row and ignores taps
 */
data class MenuEntry(
	val id: Int,
	val label: Int,
	val icon: Int = 0,
	val children: List<MenuEntry> = emptyList(),
	val selectionAction: Boolean = false,
	val always: Boolean = false,
	val enabled: Boolean = true,
)

/**
 * The more-options dropdown: flat rows and one-level submenus, one visible level.
 * - a parent tap swaps the menu to its children.
 * - the back row returns to the top level, with a single state and no stacked popups.
 */
@Composable
fun MoreMenu(entries: List<MenuEntry>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
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
	DropdownMenuItem(onClick = onClick, enabled = entry.enabled) {
		MenuIcon(entry)
		Text(
			stringResource(entry.label),
			// the disabled alpha matches the muted text of Material.
			// the menu carries no per-row theme, so the leaf picks the colour itself
			color = if (entry.enabled) Color.Unspecified
			else MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
			modifier = Modifier.padding(start = entry.iconSpacing()),
		)
	}
}

@Composable
private fun MenuParentRow(entry: MenuEntry, onClick: () -> Unit) {
	DropdownMenuItem(onClick = onClick) {
		MenuIcon(entry)
		Text(
			stringResource(entry.label),
			modifier = Modifier.padding(start = entry.iconSpacing()),
		)
		Icon(
			Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			modifier = Modifier.padding(start = 12.dp),
		)
	}
}

@Composable
private fun MenuIcon(entry: MenuEntry) {
	if (entry.icon != 0) Icon(painterResource(entry.icon), contentDescription = null)
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
