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
 * @property icon the row's leading icon — `0` renders none
 * @property children non-empty opens a second menu level (parents carry a placeholder id)
 * @property selectionAction acts on selected rows, keeping the resource order
 * @property always stays in both menu forms (the export rows)
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
 * More-options dropdown — flat rows and one-level submenus, one visible
 * level. A parent tap swaps the menu to its children, the back row
 * returns to the top level (a single state, no stacked popups).
 */
@Composable
fun MoreMenu(entries: List<MenuEntry>, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
	// the M2 dropdown reads MaterialTheme colours, but the app carries no
	// theme — supply the two it reads. The resources resolve the day/night variant
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
			// the disabled alpha matches Material's muted text — the menu carries
			// no per-row theme, so the leaf picks the colour itself
			color = if (entry.enabled) Color.Unspecified
			else MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
			modifier = Modifier.padding(start = entry.iconSpacing()),
		)
	}
}

/**
 * A parent row — label plus arrow, a tap swaps the menu to its children.
 */
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

/** The row's leading icon — an icon-less entry renders text only. */
@Composable
private fun MenuIcon(entry: MenuEntry) {
	if (entry.icon != 0) Icon(painterResource(entry.icon), contentDescription = null)
}

/** The label's leading space — the icon column, zero without an icon. */
private fun MenuEntry.iconSpacing() = if (icon != 0) 12.dp else 0.dp

/**
 * The submenu's back row — left arrow plus the parent label, a tap returns
 * to the top level.
 */
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
