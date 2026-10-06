package com.schlmgr.gui.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.schlmgr.R
import com.schlmgr.gui.Controller

/**
 * A message dialog with the full (copyable) text in the body and two actions:
 * copying the message to the clipboard and dismissing.
 */
class TextPopup(private val msg: String, private val fullMsg: String) : AbstractPopup(0, false) {

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
		compose.setContent { TextPopupScreen() }
	}

	@Composable
	private fun TextPopupScreen() {
		PopupCard {
			// The text area caps at 400dp, so the card stays compact and scrolled
			// long texts scroll inside, while the buttons below always stay fixed at
			// the card's bottom.
			Box(
				Modifier
					.heightIn(max = 400.dp)
					.verticalScroll(rememberScrollState())
			) {
				Text(
					msg,
					Modifier
						.horizontalScroll(rememberScrollState())
						.padding(5.dp),
					style = TextStyle(fontSize = 20.sp, color = TextColor),
				)
			}
			PopupDivider()
			Row(Modifier.fillMaxWidth().height(50.dp)) {
				PopupButton(stringResource(R.string.popup_copy), Modifier.weight(1f)) {
					(Controller.currentActivity ?: Controller.activity)
						.getSystemService(Context.CLIPBOARD_SERVICE)
						.let { it as ClipboardManager }
						.setPrimaryClip(ClipData.newPlainText(msg, fullMsg))
					dismiss()
				}
				VerticalButtonSeparator()
				PopupButton("OK", Modifier.weight(1f)) { dismiss() }
			}
		}
	}
}

/** The shared look of the small dialogs: white card with 30dp margins, vertically
 *  centered, only as tall as its content (capped by the scrollable body inside).
 *  A centered [Box] keeps the card hugging its content so there is no dead space
 *  around or below the bottom bar. */
@Composable
internal fun PopupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
	Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
		Column(
			Modifier
				.padding(30.dp)
				.fillMaxWidth()
				.background(Color.White)
		) {
			content()
		}
	}
}

@Composable
internal fun PopupDivider() {
	Spacer(
		Modifier
			.fillMaxWidth()
			.height(0.7.dp)
			.background(Color(0xFF888888))
	)
}

@Composable
internal fun VerticalButtonSeparator() {
	Spacer(
		Modifier
			.width(0.5.dp)
			.fillMaxHeight()
			.padding(vertical = 10.dp)
			.background(Color(0xFF888888))
	)
}

@Composable
internal fun PopupButton(label: String, modifier: Modifier, onClick: () -> Unit) {
	Box(
		modifier
			.fillMaxHeight()
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Text(
			label,
			Modifier.padding(horizontal = 5.dp),
			style = TextStyle(fontSize = 18.sp, color = TextColor),
		)
	}
}

internal val TextColor = Color(0xE0100000)
