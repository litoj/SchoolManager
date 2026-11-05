package cz.litoj.schlmgr.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R

// the shared settings-style rows that the settings and test-setup screens both render.
// one place keeps the two from drifting apart in padding, font size or switch colours.


/** A label with a trailing switch and an optional caption below. */
@Composable
fun SettingSwitchRow(
	label: String,
	checked: Boolean,
	onToggle: () -> Unit,
	caption: String? = null,
) {
	Column(
		Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggle)
			.padding(5.dp),
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
		caption?.let { SettingCaption(it) }
	}
}

/** [onFocusLost] typically restores an empty field. */
@Composable
fun SettingNumberRow(
	label: String,
	value: String,
	onValueChange: (String) -> Unit,
	onFocusLost: () -> Unit,
	caption: String? = null,
) {
	SettingFieldRow(label, caption) { focus ->
		// the field takes the font size of the row label.
		// the component enforces its own square minimum width, so no fixed width is needed.
		BareTextField(
			value = value,
			onValueChange = onValueChange,
			fontSize = 18.sp,
			keyboardType = KeyboardType.Number,
			onFocusLost = onFocusLost,
			focusRequester = focus,
		)
	}
}

/**
 * A label with its field on the right and an optional caption below.
 * - a tap on the label focuses the field, the same as a tap on the field.
 */
@Composable
fun SettingFieldRow(
	label: String,
	caption: String? = null,
	field: @Composable (FocusRequester) -> Unit,
) {
	val focus = remember { FocusRequester() }
	val keyboard = LocalSoftwareKeyboardController.current
	Column(
		Modifier
			.fillMaxWidth()
			.padding(5.dp),
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Text(
				label,
				Modifier
					.weight(1f)
					.clickable {
						focus.requestFocus()
						keyboard?.show()
					},
				style = TextStyle(fontSize = 18.sp, color = DefaultTextColor),
			)
			field(focus)
		}
		caption?.let { SettingCaption(it) }
	}
}

/** A label with its current choice on the right and an optional caption below. */
@Composable
fun SettingChoiceRow(
	label: String,
	value: String,
	onToggle: () -> Unit,
	caption: String? = null,
) {
	Column(
		Modifier
			.fillMaxWidth()
			.clickable(onClick = onToggle)
			.padding(5.dp),
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Text(label, Modifier.weight(1f), style = TextStyle(fontSize = 18.sp, color = DefaultTextColor))
			Text(value, style = TextStyle(fontSize = 18.sp, color = DefaultTextColor))
		}
		caption?.let { SettingCaption(it) }
	}
}

/** The small explanation under a setting row. */
@Composable
fun SettingCaption(text: String) {
	Text(
		text,
		Modifier.padding(top = 2.dp),
		style = TextStyle(fontSize = 13.sp, color = DefaultTextColor),
	)
}
