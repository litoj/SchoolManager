package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

// The shared settings-style rows — the switch row and the number row the
// settings and test-setup screens both render. One place, so the two screens
// cannot drift apart in padding, font size or switch colours.


/** A label with a trailing switch — the whole row toggles. */
@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
	Row(
		Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggle)
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, color = DefaultTextColor))
		Switch(
			checked = checked,
			onCheckedChange = null,
			colors = SwitchDefaults.colors(
				checkedThumbColor = colorResource(R.color.colorOnHeader),
				checkedTrackColor = colorResource(R.color.colorPrimaryHeader),
			),
		)
	}
}

/**
 * A label with a trailing digits-only number field — [onFocusLost] typically
 * restores an empty field.
 */
@Composable
fun SettingNumberRow(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	onFocusLost: () -> Unit,
) {
	Row(
		Modifier
			.fillMaxWidth()
			.padding(5.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, color = DefaultTextColor))
		// Same font size as the row label. The component enforces its own square
		// minimum width, so no fixed width is needed here.
		BareTextField(
			value = value,
			onValueChange = onValueChange,
			fontSize = 18.sp,
			keyboardType = KeyboardType.Number,
			onFocusLost = onFocusLost,
		)
	}
}
