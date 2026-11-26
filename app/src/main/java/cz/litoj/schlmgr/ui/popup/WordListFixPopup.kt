package cz.litoj.schlmgr.ui.popup

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.ImportFailureExplanation
import cz.litoj.schlmgr.ui.component.HairlineDivider

/**
 * A word-list failure with the file under it, opened on the failing character.
 * - the report stays short: the editor shows the line and the spot, so the source is
 *   never repeated above it.
 * - Save writes the same document and runs the import again, no second app, no re-pick.
 * - a refused write keeps the popup, a typed fix is never lost.
 * - the reported line is marked and brought into view.
 * - the keyboard waits for a tap, it would cover the file the user has to read first.
 */
internal class WordListFixPopup(
	private val title: String,
	private val report: ImportFailureExplanation.Content,
	private val file: Uri,
	source: String,
	private val caret: Int,
	private val onReimport: (Uri) -> Unit,
) : DialogSpec {

	private val original = source

	/** the editor text, held off the composition so a recomposition keeps a typed fix */
	private var text by mutableStateOf(TextFieldValue(source, TextRange(caret)))

	/** the note of a document that refused the write, the reason the popup stayed open */
	private var refused by mutableStateOf(false)

	init {
		Dialogs.show(this)
	}

	override fun canDismissByOutsideTouch(): Boolean = text.text == original

	@Composable
	override fun Content() {
		val ctx = LocalContext.current
		PopupCard(atTop = true) {
			PopupTitle(title)
			HairlineDivider()
			IssueBlock()
			HairlineDivider()
			FileField()
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.cancel), Modifier.weight(1f)) {
					Dialogs.dismiss(this@WordListFixPopup)
				}
				VerticalButtonSeparator()
				PopupButton(stringResource(R.string.popup_save_retry), Modifier.weight(1f)) { save(ctx) }
			}
		}
	}

	/**
	 * The diagnosis of the spot the editor opened at, capped so the file keeps the screen.
	 * - the red notice comes first, testers read the title as a final refusal of the file.
	 */
	@Composable
	private fun IssueBlock() {
		Column(
			Modifier
				.fillMaxWidth()
				.heightIn(max = 180.dp)
				.verticalScroll(rememberScrollState())
				.padding(12.dp)
		) {
			Text(
				stringResource(R.string.popup_file_has_error),
				Modifier.padding(bottom = 6.dp),
				style = TextStyle(fontSize = 20.sp, color = colorResource(R.color.colorDanger)),
			)
			PopupBodyText(report.location)
			PopupBodyText(report.guidance, Modifier.padding(top = 6.dp))
			if (refused) Text(
				stringResource(R.string.popup_save_failed),
				Modifier.padding(top = 6.dp),
				style = TextStyle(fontSize = 20.sp, color = colorResource(R.color.colorDanger)),
			)
		}
	}

	/**
	 * The file as a text of its own, one line per source line, the reported line marked.
	 * - the scrolls are the field's own, so the marked line can be brought into view.
	 */
	@Composable
	private fun ColumnScope.FileField() {
		val vertical = rememberScrollState()
		val horizontal = rememberScrollState()
		var laidOut by remember { mutableStateOf<TextLayoutResult?>(null) }
		var markedLine by remember { mutableIntStateOf(-1) }
		var viewport by remember { mutableStateOf(IntSize.Zero) }
		var revealed by remember { mutableStateOf(false) }
		val markColor = colorResource(R.color.colorMarkedLine)

		LaunchedEffect(laidOut, viewport) {
			val layout = laidOut ?: return@LaunchedEffect
			// once only: a later keystroke must not pull the view back from where the user is
			if (revealed || viewport.height == 0) return@LaunchedEffect
			markedLine = layout.getLineForOffset(caret.coerceIn(0, text.text.length))
			revealTo(layout, markedLine, vertical, horizontal, viewport)
			revealed = true
		}

		Box(
			Modifier
				.weight(1f)
				.fillMaxWidth()
				.padding(horizontal = 12.dp, vertical = 6.dp)
				.border(0.8.dp, colorResource(if (refused) R.color.colorDanger else R.color.colorControlLine))
				.onSizeChanged { viewport = it }
				.verticalScroll(vertical)
				.horizontalScroll(horizontal)
		) {
			BasicTextField(
				value = text,
				onValueChange = { text = it },
				modifier = Modifier
					.drawBehind {
						drawMarkedLine(laidOut, markedLine, markColor)
					},
				textStyle = TextStyle(
					fontSize = 15.sp,
					lineHeight = 20.sp,
					fontFamily = FontFamily.Monospace,
					color = TextColor,
				),
				// the field would paint its caret black on a theme that has no black text
				cursorBrush = SolidColor(TextColor),
				onTextLayout = { laidOut = it },
			)
		}
	}

	/** The band of the reported line, drawn where the line sits in the laid-out text. */
	private fun DrawScope.drawMarkedLine(layout: TextLayoutResult?, line: Int, color: Color) {
		if (layout == null || line < 0 || line >= layout.lineCount) return
		val top = layout.getLineTop(line)
		drawRect(
			color = color,
			topLeft = Offset(0f, top),
			size = Size(layout.getLineRight(line), layout.getLineBottom(line) - top),
		)
	}

	/** Brings the marked line near the top of the field and its failing column into view. */
	private suspend fun revealTo(
		layout: TextLayoutResult,
		line: Int,
		vertical: ScrollState,
		horizontal: ScrollState,
		viewport: IntSize,
	) {
		if (viewport.height == 0 || line >= layout.lineCount) return
		val top = layout.getLineTop(line).toInt()
		vertical.scrollTo((top - viewport.height / 3).coerceIn(0, vertical.maxValue))
		val column = layout.getBoundingBox(caret.coerceIn(0, text.text.length)).left.toInt()
		horizontal.scrollTo((column - viewport.width / 3).coerceIn(0, horizontal.maxValue))
	}

	private fun save(ctx: Context) {
		// the document is written on the click, the way the clipboard button reaches its
		// binder service: the fix has to land before the re-import reads the same file
		refused = !FileText.writeText(ctx, file, text.text)
		if (refused) return
		Dialogs.dismiss(this)
		onReimport(file)
	}
}
