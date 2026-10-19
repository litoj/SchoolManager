package cz.litoj.schlmgr.ui.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.Startup
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * A message dialog with Copy and dismiss. Open so variants can hook [onDismissed].
 *
 * @param msg the displayed body (wraps, long texts scroll)
 * @param copyText what Copy puts on the clipboard (may exceed [msg])
 */
open class MessagePopup(private val msg: String, private val copyText: String = msg) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			// long texts scroll inside — the buttons stay fixed at the bottom.
			// The weight grows the area to the whole free screen height first.
			Box(
				Modifier
					.weight(1f, fill = false)
					.verticalScroll(rememberScrollState())
			) {
				Text(
					msg,
					Modifier.padding(5.dp),
					style = TextStyle(fontSize = 20.sp, color = TextColor),
				)
			}
			HairlineDivider()
			Row(Modifier.fillMaxWidth().height(50.dp)) {
				val truncationNote = stringResource(R.string.popup_copy_truncated)
				val copyFailed = stringResource(R.string.popup_copy_failed)
				PopupButton(stringResource(R.string.popup_copy), Modifier.weight(1f)) {
					// the copy failure rides the message bus — the popup never emits on it directly
					if (!copyToClipboard(
							GlobalDependencies.appContext,
							// the clipboard UI's clip description — a snippet, not the
							// content. The label rides in the same binder parcel
							msg.take(CLIPBOARD_LABEL_CHARS),
							capForClipboard(copyText, truncationNote),
						)) Startup.showMsg(copyFailed, copyFailed)
					Dialogs.dismiss(this@MessagePopup)
				}
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) { Dialogs.dismiss(this@MessagePopup) }
			}
		}
	}

	companion object {
		/** The clip label's snippet length — the label rides in the same
		 *  binder parcel as the text, so it stays a snippet. */
		internal const val CLIPBOARD_LABEL_CHARS = 64

		/** The clipboard's binder parcel is capped (~1 MB shared per
		 *  process). UTF-16 text plus label — 200k characters stays below it. */
		internal const val CLIPBOARD_CHAR_LIMIT = 200_000

		/**
		 * Cuts the text to one clipboard call's size — the binder throws above
		 * its limit, which would kill the process.
		 * - the overflow becomes the truncation note — the popup body still shows all of it.
		 */
		internal fun capForClipboard(text: String, truncationNote: String): String {
			if (text.length <= CLIPBOARD_CHAR_LIMIT) return text
			val cut = CLIPBOARD_CHAR_LIMIT - truncationNote.length - 1
			return text.take(cut) + '\n' + truncationNote
		}

		/**
		 * Puts text on the clipboard under a label — `false` when the service
		 * rejects it.
		 * - the clipboard is a binder service — over-limit data throws.
		 */
		internal fun copyToClipboard(context: Context, label: String, text: String): Boolean =
			try {
				// the clip label rides in the same binder parcel as its text
				val clip = ClipData.newPlainText(label, text)
				(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
					.setPrimaryClip(clip)
				true
			} catch (_: Exception) {
				false
			}
	}
}
