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

// the shared settings-style rows that the settings and test-setup screens both render.
// one place keeps the two from drifting apart in padding, font size or switch colours.


/** A label with a trailing switch. */
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

/** [onFocusLost] typically restores an empty field. */
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
		// the field takes the font size of the row label.
		// the component enforces its own square minimum width, so no fixed width is needed.
		BareTextField(
			value = value,
			onValueChange = onValueChange,
			fontSize = 18.sp,
			keyboardType = KeyboardType.Number,
			onFocusLost = onFocusLost,
		)
	}
}
