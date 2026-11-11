package cz.litoj.schlmgr.ui.popup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UserMessages
import cz.litoj.schlmgr.ui.component.HairlineDivider

/** A raw source line and its marked character, apart from the explanatory text. */
data class MessageSource(val location: String, val line: String, val problemIndex: Int)

/**
 * A message dialog with Copy and dismiss.
 * - it stays open, so variants can hook [onDismissed].
 *
 * @param msg the displayed body, which wraps and scrolls when long
 * @param copyText what Copy puts on the clipboard, which may exceed [msg]
 * @param title the optional heading above the body
 * @param source an optional located source excerpt displayed before [msg]
 */
open class MessagePopup(
	private val msg: String,
	private val copyText: String = msg,
	private val title: String? = null,
	private val source: MessageSource? = null,
) : DialogSpec {

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		PopupCard {
			if (title != null) {
				PopupTitle(title)
				HairlineDivider()
			}
			if (source == null) PopupScrollBody(msg) else PopupScrollBody {
				PopupBodyText(source.location)
				SourceBlock(source)
				PopupBodyText(msg)
			}
			HairlineDivider()
			PopupButtonRow {
				val truncationNote = stringResource(R.string.popup_copy_truncated)
				val copyFailed = stringResource(R.string.popup_copy_failed)
				PopupButton(stringResource(R.string.popup_copy), Modifier.weight(1f)) {
					// the copy failure rides the message bus.
					// the popup never emits on it directly
					if (!copyToClipboard(
							GlobalDependencies.appContext,
							// the clip description of the UI: a snippet, not the content.
							// the label rides in the same binder parcel
							msg.take(CLIPBOARD_LABEL_CHARS),
							capForClipboard(copyText, truncationNote),
						)) UserMessages.showMsg(copyFailed, copyFailed)
					Dialogs.dismiss(this@MessagePopup)
				}
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) { Dialogs.dismiss(this@MessagePopup) }
			}
		}
	}

	@Composable
	private fun SourceBlock(source: MessageSource) {
		val style = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Monospace, color = TextColor)
		var sourceLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
		var markerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
		val markerX = sourceLayout?.let { sourceText ->
			markerLayout?.let { markerText ->
				val index = source.problemIndex.coerceIn(0, source.line.length)
				val targetX = if (index == source.line.length) sourceText.getCursorRect(index).left
				else sourceText.getBoundingBox(index).let { (it.left + it.right) / 2 }
				val markerBounds = markerText.getBoundingBox(0)
				(targetX - (markerBounds.left + markerBounds.right) / 2).roundToInt()
			}
		} ?: 0
		Column(
			Modifier
				.padding(vertical = 8.dp)
				.fillMaxWidth()
				.background(colorResource(R.color.colorSubBackground))
				.horizontalScroll(rememberScrollState())
				.padding(8.dp)
		) {
			Box(Modifier.fillMaxWidth()) {
				Text(
					"↓",
					Modifier.offset { IntOffset(markerX, 0) },
					style = style,
					softWrap = false,
					onTextLayout = { markerLayout = it },
				)
			}
			Text(
				source.line,
				style = style,
				softWrap = false,
				onTextLayout = { sourceLayout = it },
			)
		}
	}

	companion object {
		/**
		 * The label rides in the same binder parcel as the text, so it stays a snippet.
		 */
		internal const val CLIPBOARD_LABEL_CHARS = 64

		/**
		 * The cap of the binder parcel of the clipboard, about 1 MB shared per process.
		 * - the text is UTF-16 plus a label, where 200k characters stays below it.
		 */
		internal const val CLIPBOARD_CHAR_LIMIT = 200_000

		/**
		 * Cuts the text to the size of one clipboard call.
		 * - the binder throws above its limit, which would kill the process.
		 * - the overflow becomes the truncation note, and the popup body still shows all of it.
		 */
		internal fun capForClipboard(text: String, truncationNote: String): String {
			if (text.length <= CLIPBOARD_CHAR_LIMIT) return text
			val cut = CLIPBOARD_CHAR_LIMIT - truncationNote.length - 1
			return text.take(cut) + '\n' + truncationNote
		}

		/**
		 * Puts text on the clipboard under a label.
		 * - returns false when the service rejects it.
		 * - the clipboard is a binder service, where over-limit data throws.
		 */
		internal fun copyToClipboard(context: Context, label: String, text: String): Boolean =
			try {
				val clip = ClipData.newPlainText(label, text)
				(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
					.setPrimaryClip(clip)
				true
			} catch (_: Exception) {
				false
			}
	}
}
