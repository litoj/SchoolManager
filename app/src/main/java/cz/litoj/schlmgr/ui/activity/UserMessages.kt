package cz.litoj.schlmgr.ui.activity

import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.GlobalDependencies
import cz.litoj.schlmgr.ui.popup.MessagePopup
import kotlinx.coroutines.launch

/**
 * Renders [GlobalDependencies.messages] as snackbars: the message's short
 * text with a 'full text' action that opens the [MessagePopup] dialog.
 * Installed by both activities — a report may arrive from any worker thread
 * while either screen is in the foreground, and the flow delivers it only
 * while a host is started (the collector runs on the main thread).
 */
fun ComponentActivity.showUserMessages() {
	lifecycleScope.launch {
		repeatOnLifecycle(Lifecycle.State.STARTED) {
			GlobalDependencies.messages.collect { msg ->
				Snackbar.make(
					window.decorView.rootView,
					msg.text, Snackbar.LENGTH_LONG
				)
					.setAction(GlobalDependencies.appContext.getString(R.string.action_full_text)) {
						MessagePopup(msg.text, msg.fullText)
					}
					// The snackbar draws its own dark grey backdrop in both palettes,
					// so its label uses the strip label colour and stays light.
					.setTextColor(
						ContextCompat.getColor(this@showUserMessages, R.color.colorActionText))
					.show()
			}
		}
	}
}
