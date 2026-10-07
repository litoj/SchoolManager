package cz.litoj.schlmgr.ui.popup

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R

/**
 * A confirmation dialog with Cancel and OK buttons; OK runs the given action on a
 * background thread (named after the message), Cancel only dismisses.
 */
class ContinuePopup(private val msg: String, onClick: Runnable) : DialogSpec {

	private val onContinue = Thread(onClick, "onContinuePopup: \"$msg\"")
	private var started = false

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			Text(
				msg,
				Modifier.padding(5.dp),
				style = TextStyle(fontSize = 20.sp, color = TextColor),
			)
			PopupDivider()
			Row(Modifier.fillMaxWidth().height(50.dp)) {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) { Dialogs.dismiss(this@ContinuePopup) }
				VerticalButtonSeparator()
				PopupButton("OK", Modifier.weight(1f)) {
					Dialogs.dismiss(this@ContinuePopup)
					// one shot: a double-tap would start the thread twice
					if (!started) {
						started = true
						onContinue.start()
					}
				}
			}
		}
	}
}
