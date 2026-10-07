package cz.litoj.schlmgr.ui.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.GlobalDependencies

/**
 * A dialog that shows a text message with two actions: copying [copyText] to
 * the clipboard and dismissing. Open so specialised variants can hook
 * [onDismissed] (e.g. consuming the data they were shown from).
 *
 * @param msg the text shown in the body; it wraps, long texts scroll vertically
 * @param copyText what the Copy button puts on the clipboard, cut to
 * [CLIPBOARD_CHAR_LIMIT] if larger — a caller may shorten the display and
 * keep the full data behind the copy (e.g. a message with the report copied
 * whole), so it only defaults to [msg]
 */
open class MessagePopup(private val msg: String, private val copyText: String = msg) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
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
					Modifier.padding(5.dp),
					style = TextStyle(fontSize = 20.sp, color = TextColor),
				)
			}
			PopupDivider()
			Row(Modifier.fillMaxWidth().height(50.dp)) {
				val truncationNote = stringResource(R.string.popup_copy_truncated)
				val copyFailed = stringResource(R.string.popup_copy_failed)
				PopupButton(stringResource(R.string.popup_copy), Modifier.weight(1f)) {
					// The clipboard is a binder service: it rejects data over its
					// transaction limit by throwing, and the crash handler would
					// turn that into a process kill — so bound the clip and never
					// let a clipboard failure escape this button.
					try {
						// the clip label rides in the same binder parcel as its text
						val clip = ClipData.newPlainText(
							msg.take(CLIPBOARD_CHAR_LIMIT),
							capForClipboard(copyText, truncationNote))
						GlobalDependencies.appContext
							.getSystemService(Context.CLIPBOARD_SERVICE)
							.let { it as ClipboardManager }
							.setPrimaryClip(clip)
					} catch (_: Exception) {
						GlobalDependencies.messages.tryEmit(
							GlobalDependencies.UiMessage(copyFailed, copyFailed))
					}
					Dialogs.dismiss(this@MessagePopup)
				}
				VerticalButtonSeparator()
				PopupButton("OK", Modifier.weight(1f)) { Dialogs.dismiss(this@MessagePopup) }
			}
		}
	}

	companion object {
		/** The binder transaction behind the clipboard is capped (~1 MB shared
		 *  per process); a clip is parceled as UTF-16 together with its label,
		 *  so 200k characters keep the call safely below that limit. */
		internal const val CLIPBOARD_CHAR_LIMIT = 200_000

		/** Cuts [text] to a size one clipboard call can carry: the binder behind
		 *  the clipboard throws above its transaction limit and the uncaught
		 *  exception would kill the process. The overflow is replaced by
		 *  [truncationNote], so the reader knows the copy is not the whole
		 *  text — the popup body still shows all of it. */
		internal fun capForClipboard(text: String, truncationNote: String): String {
			if (text.length <= CLIPBOARD_CHAR_LIMIT) return text
			val cut = (CLIPBOARD_CHAR_LIMIT - truncationNote.length - 1).coerceAtLeast(0)
			return text.take(cut) + '\n' + truncationNote
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
				.background(colorResource(R.color.colorPrimaryBg))
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
			.background(colorResource(R.color.colorStripDivider))
	)
}

@Composable
internal fun VerticalButtonSeparator() {
	Spacer(
		Modifier
			.width(0.5.dp)
			.fillMaxHeight()
			.padding(vertical = 10.dp)
			.background(colorResource(R.color.colorStripDivider))
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

internal val TextColor @Composable get() = colorResource(R.color.colorPrimaryFg)
