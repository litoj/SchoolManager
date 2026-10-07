package cz.litoj.schlmgr.gui.popup

import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R

/**
 * A confirmation dialog with Cancel and OK buttons; OK runs the given action on a
 * background thread (named after the message), Cancel only dismisses.
 */
class ContinuePopup(private val msg: String, onClick: Runnable) : AbstractPopup(0, true) {

	private val onContinue = Thread(onClick, "onContinuePopup: \"$msg\"")

	init {
		create()
	}

	override fun addContent(view: ViewGroup) {
		val compose = ComposeView(view.context)
		(view as FrameLayout).addView(
			compose,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
		compose.setContent { ContinuePopupScreen() }
	}

	@Composable
	private fun ContinuePopupScreen() {
		PopupCard {
			Text(
				msg,
				Modifier.padding(5.dp),
				style = TextStyle(fontSize = 20.sp, color = TextColor),
			)
			PopupDivider()
			Row(Modifier.fillMaxWidth().height(50.dp)) {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) { dismiss() }
				VerticalButtonSeparator()
				PopupButton("OK", Modifier.weight(1f)) {
					dismiss()
					onContinue.start()
				}
			}
		}
	}
}
