package cz.litoj.schlmgr.gui.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.gui.AppSettings
import cz.litoj.schlmgr.gui.Controller

/**
 * Shows the report of an unexpected failure from a previous session. The whole report is a
 * single copyable string; dismissing it (in any way) consumes the report so it is not shown
 * again.
 */
class CrashReportPopup(private val report: String) : AbstractPopup(0, false) {

	init {
		create()
	}

	override fun onDismiss(forever: Boolean) {
		if (forever) AppSettings.remove("uncaughtException")
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
		compose.setContent { CrashReportScreen() }
	}

	@Composable
	private fun CrashReportScreen() {
		PopupCard {
			// Horizontally scrollable (like the old HorizontalScrollView) so long stack
			// traces stay readable, vertically scrollable for long multi-line reports.
			Box(Modifier.verticalScroll(rememberScrollState())) {
				Text(
					report,
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
						.setPrimaryClip(ClipData.newPlainText(report, report))
					dismiss()
				}
				VerticalButtonSeparator()
				PopupButton("OK", Modifier.weight(1f)) { dismiss() }
			}
		}
	}
}
