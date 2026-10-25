package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * OK reports through [onOk] on the thread of the caller, which decides where the action runs.
 * - the Cancel button only dismisses.
 */
class ContinuePopup(private val onOk: () -> Unit) : DialogSpec {

	private var fired = false

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			Text(
				stringResource(R.string.continue_delete),
				Modifier.padding(5.dp),
				style = TextStyle(fontSize = 20.sp, color = TextColor),
			)
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) { Dialogs.dismiss(this@ContinuePopup) }
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) {
					Dialogs.dismiss(this@ContinuePopup)
					// one shot, so a double-tap must not confirm twice
					if (!fired) {
						fired = true
						onOk()
					}
				}
			}
		}
	}
}
